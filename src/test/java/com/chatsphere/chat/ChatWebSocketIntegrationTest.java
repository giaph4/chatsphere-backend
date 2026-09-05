package com.chatsphere.chat;

import com.chatsphere.auth.security.JwtTokenProvider;
import com.chatsphere.chat.domain.MessageType;
import com.chatsphere.chat.dto.MarkReadRequest;
import com.chatsphere.chat.dto.MessageResponse;
import com.chatsphere.chat.dto.ReadReceiptEvent;
import com.chatsphere.chat.dto.SendMessageRequest;
import com.chatsphere.chat.dto.TypingEvent;
import com.chatsphere.chat.dto.TypingRequest;
import com.chatsphere.chat.dto.WsSendMessageRequest;
import com.chatsphere.chat.service.ConversationService;
import com.chatsphere.chat.service.MessageService;
import com.chatsphere.common.WsDestinations;
import com.chatsphere.presence.PresenceEvent;
import com.chatsphere.presence.PresenceService;
import com.chatsphere.presence.PresenceStatus;
import com.chatsphere.support.TestcontainersConfiguration;
import com.chatsphere.user.domain.Friendship;
import com.chatsphere.user.domain.User;
import com.chatsphere.user.domain.UserRole;
import com.chatsphere.user.domain.UserStatus;
import com.chatsphere.user.repository.FriendshipRepository;
import com.chatsphere.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Nghiệm thu Phase 4 (03_CODE_ROADMAP.md 4.5): dựng 2 client STOMP thật, kết nối qua TCP tới
 * server đang chạy, xác nhận tin nhắn/typing/read receipt/presence tới đúng người.
 *
 * <p><b>Vì sao không kế thừa {@code AbstractIntegrationTest}?</b> Lớp cha dùng
 * {@code webEnvironment = MOCK} (MockMvc, không mở cổng thật). WebSocket cần một cổng TCP thật
 * để bắt tay và nâng cấp giao thức — không giả lập được bằng MockMvc. Đổi lại,
 * {@code RANDOM_PORT} tạo một application context riêng (kèm bộ container riêng), nên suite test
 * chạy lâu hơn một chút; đây là cái giá bắt buộc phải trả để test được tầng vận chuyển.
 *
 * <p>Dữ liệu nền được tạo thẳng qua repository/service thay vì gọi REST đăng ký → xác thực OTP:
 * lớp test này chỉ nhắm vào tầng realtime, đi vòng qua luồng auth chỉ làm nó chậm và dễ vỡ vì
 * lý do không liên quan (luồng đó đã có {@code AuthFlowIntegrationTest} phủ).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ChatWebSocketIntegrationTest {

    private static final int TIMEOUT_SECONDS = 5;

    /**
     * SUBSCRIBE và SEND đi trên hai kết nối TCP khác nhau nên không có thứ tự bảo đảm giữa
     * chúng. Chờ một nhịp ngắn để broker đăng ký xong subscription trước khi phát tin — nếu
     * không, tin có thể tới broker lúc chưa ai đăng ký và bị bỏ đi hoàn toàn đúng theo thiết kế
     * (topic không lưu trữ), khiến test đỏ vì lý do sai.
     */
    private static final long SUBSCRIBE_SETTLE_MS = 400;

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FriendshipRepository friendshipRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private ConversationService conversationService;
    @Autowired
    private MessageService messageService;
    @Autowired
    private PresenceService presenceService;

    /**
     * Dùng CHÍNH bộ chuyển đổi mà broker phía server đang dùng. Tự tạo một
     * {@code JacksonJsonMessageConverter} mới ở phía client là cái bẫy kinh điển: mapper của test
     * và của server có thể khác nhau về quy ước đặt tên (snake_case) hay cách ghi {@code Instant},
     * và test sẽ đỏ vì lệch cấu hình JSON chứ không phải vì code sai.
     */
    @Autowired
    @Qualifier("brokerMessageConverter")
    private MessageConverter brokerMessageConverter;

    private final List<StompSession> openSessions = new ArrayList<>();

    @AfterEach
    void disconnectAll() {
        openSessions.forEach(session -> {
            if (session.isConnected()) {
                session.disconnect();
            }
        });
        openSessions.clear();
    }

    // ---------- Kịch bản nghiệm thu chính ----------

    @Test
    void hai_client_cung_nhan_duoc_tin_nhan_realtime_khong_can_goi_lai_api() throws Exception {
        User alice = createUser("wsalice");
        User bob = createUser("wsbob");
        UUID conversationId = directConversationOf(alice, bob);

        StompSession aliceSession = connect(alice);
        StompSession bobSession = connect(bob);

        BlockingQueue<MessageResponse> aliceInbox = subscribeMessages(aliceSession, conversationId);
        BlockingQueue<MessageResponse> bobInbox = subscribeMessages(bobSession, conversationId);
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        aliceSession.send(WsDestinations.APP_PREFIX + "/chat.sendMessage",
                new WsSendMessageRequest(conversationId, MessageType.TEXT, "Hello realtime", null));

        // Người nhận thấy tin ngay — đúng điều Phase 3 chưa làm được.
        MessageResponse received = poll(bobInbox);
        assertThat(received.content()).isEqualTo("Hello realtime");
        assertThat(received.sender().id()).isEqualTo(alice.getId());
        assertThat(received.conversationId()).isEqualTo(conversationId);

        // Người GỬI cũng nhận lại từ topic: client không cần tự chèn tin vào danh sách rồi
        // đối chiếu lại với server — mọi thiết bị của họ hiển thị cùng một nguồn sự thật.
        assertThat(poll(aliceInbox).id()).isEqualTo(received.id());
    }

    @Test
    void tin_nhan_gui_qua_rest_cung_duoc_phat_realtime() throws Exception {
        User alice = createUser("wsrestalice");
        User bob = createUser("wsrestbob");
        UUID conversationId = directConversationOf(alice, bob);

        StompSession bobSession = connect(bob);
        BlockingQueue<MessageResponse> bobInbox = subscribeMessages(bobSession, conversationId);
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        // Gọi thẳng service (đúng đường mà MessageController của Phase 3 đi) — KHÔNG qua STOMP.
        messageService.sendMessage(alice.getId(), conversationId,
                new SendMessageRequest(MessageType.TEXT, "Gui qua REST", null));

        assertThat(poll(bobInbox).content()).isEqualTo("Gui qua REST");
    }

    @Test
    void typing_duoc_broadcast_toi_thanh_vien_khac() throws Exception {
        User alice = createUser("wstypea");
        User bob = createUser("wstypeb");
        UUID conversationId = directConversationOf(alice, bob);

        StompSession aliceSession = connect(alice);
        StompSession bobSession = connect(bob);
        BlockingQueue<TypingEvent> bobInbox = subscribe(bobSession, conversationId, TypingEvent.class);
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        aliceSession.send(WsDestinations.APP_PREFIX + "/chat.typing",
                new TypingRequest(conversationId, true));

        TypingEvent event = poll(bobInbox);
        assertThat(event.userId()).isEqualTo(alice.getId());
        assertThat(event.typing()).isTrue();
    }

    @Test
    void mark_read_cap_nhat_con_tro_va_broadcast_bien_nhan() throws Exception {
        User alice = createUser("wsreada");
        User bob = createUser("wsreadb");
        UUID conversationId = directConversationOf(alice, bob);

        // Tạo tin TRƯỚC khi ai đó subscribe: frame của tin này bay đi lúc chưa ai nghe,
        // nên hộp thư dưới đây chỉ chứa đúng biên nhận cần kiểm tra.
        MessageResponse message = messageService.sendMessage(alice.getId(), conversationId,
                new SendMessageRequest(MessageType.TEXT, "Doc di", null));

        StompSession aliceSession = connect(alice);
        StompSession bobSession = connect(bob);
        BlockingQueue<ReadReceiptEvent> aliceInbox =
                subscribe(aliceSession, conversationId, ReadReceiptEvent.class);
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        bobSession.send(WsDestinations.APP_PREFIX + "/chat.markRead",
                new MarkReadRequest(conversationId, message.id()));

        ReadReceiptEvent receipt = poll(aliceInbox);
        assertThat(receipt.userId()).isEqualTo(bob.getId());
        assertThat(receipt.lastReadMessageId()).isEqualTo(message.id());

        // Con trỏ đã dời thật trong DB -> unreadCount của bob về 0.
        var conversations = conversationService.getMyConversations(
                bob.getId(), org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(conversations.items()).singleElement()
                .satisfies(item -> assertThat(item.unreadCount()).isZero());
    }

    // ---------- Presence ----------

    @Test
    void ban_be_nhan_duoc_su_kien_online_khi_co_nguoi_ket_noi() throws Exception {
        User alice = createUser("wspresa");
        User bob = createUser("wspresb");
        friendshipRepository.save(Friendship.between(alice, bob));

        // Bob online trước và đang nghe queue riêng của mình.
        StompSession bobSession = connect(bob);
        BlockingQueue<PresenceEvent> bobInbox = subscribePresence(bobSession);
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        connect(alice);

        PresenceEvent event = poll(bobInbox);
        assertThat(event.userId()).isEqualTo(alice.getId());
        assertThat(event.status()).isEqualTo(PresenceStatus.ONLINE);
        assertThat(presenceService.isOnline(alice.getId())).isTrue();
    }

    @Test
    void dong_mot_trong_hai_tab_van_giu_trang_thai_online() throws Exception {
        User alice = createUser("wsmultitab");

        StompSession tab1 = connect(alice);
        connect(alice); // tab thứ 2 của cùng người dùng
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        tab1.disconnect();
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        // Còn tab 2 -> vẫn online. Đây là lý do presence đếm số phiên thay vì dùng cờ boolean.
        assertThat(presenceService.isOnline(alice.getId())).isTrue();
    }

    // ---------- Bảo mật ----------

    @Test
    void connect_bi_tu_choi_khi_token_khong_hop_le() {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer khong-phai-jwt");

        assertThatThrownBy(() -> newClient()
                .connectAsync(wsUrl(), new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
                })
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    void khong_the_subscribe_topic_cua_hoi_thoai_minh_khong_tham_gia() throws Exception {
        User alice = createUser("wsspya");
        User bob = createUser("wsspyb");
        User intruder = createUser("wsspyc");
        UUID privateConversation = directConversationOf(alice, bob);

        StompSession intruderSession = connect(intruder);
        intruderSession.subscribe(WsDestinations.conversationTopic(privateConversation),
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return MessageResponse.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        // Không bao giờ được gọi tới.
                    }
                });

        // Server phản hồi ERROR frame rồi đóng phiên — client mất kết nối, không nhận được gì.
        Thread.sleep(SUBSCRIBE_SETTLE_MS);
        assertThat(intruderSession.isConnected()).isFalse();
    }

    // ---------- Helper ----------

    private User createUser(String username) {
        User user = new User();
        user.setEmail(username + "@ws.test");
        user.setUsername(username);
        user.setDisplayName(username);
        user.setPasswordHash(passwordEncoder.encode("Password1"));
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);
        return userRepository.save(user);
    }

    private UUID directConversationOf(User a, User b) {
        return conversationService.getOrCreateDirectConversation(a.getId(), b.getId()).id();
    }

    private WebSocketStompClient newClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(brokerMessageConverter);
        return client;
    }

    private String wsUrl() {
        // Endpoint WebSocket THUẦN (không phải SockJS): client Java không cần lớp fallback,
        // và dùng thẳng WebSocket giúp test đúng đường mà trình duyệt hiện đại vẫn đi.
        return "ws://localhost:" + port + "/ws";
    }

    private StompSession connect(User user) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization",
                "Bearer " + jwtTokenProvider.generateAccessToken(user.getId(), user.getRole()));

        StompSession session = newClient()
                .connectAsync(wsUrl(), new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {
                        })
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        openSessions.add(session);
        return session;
    }

    private BlockingQueue<MessageResponse> subscribeMessages(StompSession session, UUID conversationId) {
        return subscribe(session, conversationId, MessageResponse.class);
    }

    private <T> BlockingQueue<T> subscribe(StompSession session, UUID conversationId, Class<T> payloadType) {
        return subscribeTo(session, WsDestinations.conversationTopic(conversationId), payloadType);
    }

    private BlockingQueue<PresenceEvent> subscribePresence(StompSession session) {
        return subscribeTo(session,
                WsDestinations.USER_PREFIX + WsDestinations.QUEUE_PRESENCE, PresenceEvent.class);
    }

    private <T> BlockingQueue<T> subscribeTo(StompSession session, String destination, Class<T> payloadType) {
        BlockingQueue<T> inbox = new ArrayBlockingQueue<>(10);
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return payloadType;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                inbox.add((T) payload);
            }
        });
        return inbox;
    }

    /** Chờ có giới hạn rồi khẳng định đã nhận được — không bao giờ chờ vô hạn trong test. */
    private <T> T poll(BlockingQueue<T> inbox) throws InterruptedException {
        T payload = inbox.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(payload).as("không nhận được frame nào sau %ds", TIMEOUT_SECONDS).isNotNull();
        return payload;
    }
}
