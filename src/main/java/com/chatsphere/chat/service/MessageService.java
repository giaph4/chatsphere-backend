package com.chatsphere.chat.service;

import com.chatsphere.chat.domain.Conversation;
import com.chatsphere.chat.domain.ConversationParticipant;
import com.chatsphere.chat.domain.ConversationType;
import com.chatsphere.chat.domain.Message;
import com.chatsphere.chat.domain.MessageAttachment;
import com.chatsphere.chat.domain.MessageDeletion;
import com.chatsphere.chat.domain.MessageReaction;
import com.chatsphere.chat.domain.MessageStatus;
import com.chatsphere.chat.domain.MessageType;
import com.chatsphere.chat.dto.AttachmentRequest;
import com.chatsphere.chat.dto.AttachmentResponse;
import com.chatsphere.chat.dto.MessageResponse;
import com.chatsphere.chat.dto.ReactionResponse;
import com.chatsphere.chat.dto.ReadReceiptEvent;
import com.chatsphere.chat.dto.SendMessageRequest;
import com.chatsphere.chat.event.MessageReadEvent;
import com.chatsphere.chat.event.MessageRecalledEvent;
import com.chatsphere.chat.event.MessageSentEvent;
import com.chatsphere.chat.mapper.ConversationMapper;
import com.chatsphere.chat.repository.ConversationParticipantRepository;
import com.chatsphere.chat.repository.MessageAttachmentRepository;
import com.chatsphere.chat.repository.MessageDeletionRepository;
import com.chatsphere.chat.repository.MessageReactionRepository;
import com.chatsphere.chat.repository.MessageRepository;
import com.chatsphere.common.BusinessException;
import com.chatsphere.common.CursorPageResponse;
import com.chatsphere.common.ErrorCode;
import com.chatsphere.media.MediaService;
import com.chatsphere.user.domain.User;
import com.chatsphere.user.service.BlockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MessageService {

    /** UC-20: chỉ được thu hồi tin nhắn trong vòng 5 phút kể từ lúc gửi. */
    private static final Duration RECALL_WINDOW = Duration.ofMinutes(5);

    private static final int DEFAULT_PAGE_LIMIT = 30;
    private static final int MAX_PAGE_LIMIT = 100;

    private final MessageRepository messageRepository;
    private final MessageAttachmentRepository attachmentRepository;
    private final MessageReactionRepository reactionRepository;
    private final MessageDeletionRepository deletionRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ConversationService conversationService;
    private final BlockService blockService;
    private final MediaService mediaService;
    private final ConversationMapper conversationMapper;
    private final ApplicationEventPublisher events;

    /**
     * Validate người gửi là participant hợp lệ, chưa bị block (chỉ áp dụng cho DIRECT —
     * theo đúng phạm vi ghi ở 03_CODE_ROADMAP.md 3.3), lưu message rồi cập nhật
     * conversation.lastMessage. KHÔNG gọi save(conversation) tường minh: conversation đang
     * managed trong cùng persistence context (load từ getConversationOrThrow), Hibernate tự
     * UPDATE lúc flush/commit nhờ dirty checking, và @LastModifiedDate tự cập nhật updatedAt
     * — đúng field dùng để sắp xếp danh sách hội thoại (ConversationRepository.findMyConversations).
     */
    @Transactional
    public MessageResponse sendMessage(UUID senderId, UUID conversationId, SendMessageRequest request) {
        Conversation conversation = conversationService.getConversationOrThrow(conversationId);
        ConversationParticipant senderParticipant =
                conversationService.getActiveParticipantOrThrow(conversationId, senderId);

        if (conversation.getType() == ConversationType.DIRECT) {
            assertOtherPartyNotBlocking(conversationId, senderId);
        }

        assertPayloadConsistent(request);

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(senderParticipant.getUser());
        message.setType(request.type());
        message.setContent(request.content());

        if (request.replyToMessageId() != null) {
            Message replyTo = messageRepository.findById(request.replyToMessageId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
            if (!replyTo.getConversation().getId().equals(conversationId)) {
                throw new BusinessException(ErrorCode.MESSAGE_NOT_IN_CONVERSATION);
            }
            message.setReplyToMessage(replyTo);
        }

        Message saved = messageRepository.save(message);
        conversation.setLastMessage(saved);

        List<MessageAttachment> attachments = saveAttachments(saved, request.attachments());

        MessageResponse response = conversationMapper.toMessageResponse(
                saved, conversationMapper.toAttachmentResponses(attachments), List.of());
        events.publishEvent(new MessageSentEvent(response));
        return response;
    }

    /**
     * UC-21: chuyển tiếp tin nhắn sang hội thoại khác.
     *
     * <p><b>Sao chép nội dung chứ không trỏ tới tin gốc.</b> Nếu chỉ lưu con trỏ, người gửi gốc
     * thu hồi tin của họ là bản chuyển tiếp ở hội thoại khác cũng trống theo — người nhận bản
     * chuyển tiếp chưa từng đồng ý điều đó, và họ cũng không nhìn thấy hội thoại gốc để hiểu
     * chuyện gì vừa xảy ra. {@code forwardedFromMessageId} vẫn được lưu, nhưng chỉ để hiển thị
     * nhãn "Đã chuyển tiếp".
     *
     * <p>Người chuyển tiếp phải là thành viên của CẢ hai hội thoại: nguồn (để được đọc tin) và
     * đích (để được gửi) — thiếu vế đầu thì chuyển tiếp thành cách đọc trộm hội thoại người khác.
     */
    @Transactional
    public MessageResponse forwardMessage(UUID currentUserId, UUID messageId, UUID targetConversationId) {
        Message origin = messageRepository.findById(messageId)
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        if (origin.getStatus() == MessageStatus.RECALLED) {
            throw new BusinessException(ErrorCode.MESSAGE_ALREADY_RECALLED);
        }
        conversationService.getActiveParticipantOrThrow(origin.getConversation().getId(), currentUserId);

        Conversation target = conversationService.getConversationOrThrow(targetConversationId);
        ConversationParticipant sender =
                conversationService.getActiveParticipantOrThrow(targetConversationId, currentUserId);
        if (target.getType() == ConversationType.DIRECT) {
            assertOtherPartyNotBlocking(targetConversationId, currentUserId);
        }

        Message copy = new Message();
        copy.setConversation(target);
        copy.setSender(sender.getUser());
        copy.setType(origin.getType());
        copy.setContent(origin.getContent());
        copy.setForwardedFromMessage(origin);

        Message saved = messageRepository.save(copy);
        target.setLastMessage(saved);

        // Đính kèm cũng được nhân bản ở tầng metadata, nhưng CÙNG trỏ tới một object trên
        // storage — không tải lại file. Đây là lý do việc xóa file vật lý phải do job dọn dẹp
        // đếm tham chiếu đảm nhiệm, không thể xóa ngay khi một tin nhắn biến mất.
        List<MessageAttachment> copied = attachmentRepository.findByMessageIds(List.of(origin.getId())).stream()
                .map(source -> MessageAttachment.of(saved, source.getFileUrl(), source.getFileName(),
                        source.getFileType(), source.getFileSize()))
                .toList();
        attachmentRepository.saveAll(copied);

        MessageResponse response = conversationMapper.toMessageResponse(
                saved, conversationMapper.toAttachmentResponses(copied), List.of());
        events.publishEvent(new MessageSentEvent(response));
        return response;
    }

    /**
     * Cursor-based pagination (01_SYSTEM_DESIGN.md §8.1): {@code cursor} là ID tin nhắn cuối
     * trang trước, {@code null} nghĩa là lấy trang mới nhất. Lấy dư 1 dòng để biết
     * {@code hasNext} mà không cần COUNT(*) trên bảng lớn nhất hệ thống.
     */
    public CursorPageResponse<MessageResponse> getMessages(UUID currentUserId, UUID conversationId,
                                                            UUID cursor, Integer limit) {
        conversationService.getActiveParticipantOrThrow(conversationId, currentUserId);

        int pageSize = clampLimit(limit);
        PageRequest window = PageRequest.of(0, pageSize + 1);

        List<Message> rows;
        if (cursor == null) {
            rows = messageRepository.findFirstPage(conversationId, window);
        } else {
            Message cursorMessage = messageRepository.findById(cursor)
                    .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
            if (!cursorMessage.getConversation().getId().equals(conversationId)) {
                throw new BusinessException(ErrorCode.MESSAGE_NOT_IN_CONVERSATION);
            }
            rows = messageRepository.findPageBefore(
                    conversationId, cursorMessage.getCreatedAt(), cursor, window);
        }

        boolean hasNext = rows.size() > pageSize;
        List<Message> page = hasNext ? rows.subList(0, pageSize) : rows;
        // Cursor được tính TRƯỚC khi lọc tin đã ẩn: nó phải là mốc của trang vừa quét trong DB,
        // không phải của danh sách sau khi lọc — lấy nhầm sẽ khiến trang sau bỏ sót tin.
        UUID nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        return new CursorPageResponse<>(toResponses(page, currentUserId), nextCursor, hasNext);
    }

    /**
     * UC-22: thả cảm xúc. Gửi lại đúng emoji đang có nghĩa là GỠ (toggle) — đúng thói quen người
     * dùng và cũng là cách duy nhất để bỏ reaction mà không cần thêm một endpoint DELETE riêng.
     */
    @Transactional
    public MessageResponse reactToMessage(UUID currentUserId, UUID messageId, String emoji) {
        Message message = loadVisibleMessage(messageId);
        ConversationParticipant participant = conversationService.getActiveParticipantOrThrow(
                message.getConversation().getId(), currentUserId);

        reactionRepository.findByMessageIdAndUserId(messageId, currentUserId)
                .ifPresentOrElse(
                        existing -> {
                            if (existing.getEmoji().equals(emoji)) {
                                reactionRepository.delete(existing);
                            } else {
                                existing.setEmoji(emoji);
                            }
                        },
                        () -> saveNewReaction(message, participant.getUser(), emoji));

        // flush để reaction vừa ghi chắc chắn có mặt trong câu đọc lại ngay bên dưới —
        // cùng một transaction, Hibernate mặc định còn đang giữ lệnh INSERT trong hàng đợi.
        reactionRepository.flush();
        return toResponses(List.of(message), currentUserId).getFirst();
    }

    /**
     * UC-28: ẩn tin nhắn khỏi tầm mắt CHÍNH MÌNH. Người khác vẫn thấy bình thường — khác hẳn
     * thu hồi. Không phát sự kiện realtime: đây là thay đổi riêng tư của một người, phát cho cả
     * hội thoại sẽ tiết lộ chính xác điều họ vừa muốn giấu.
     *
     * <p>Idempotent: ẩn một tin đã ẩn không phải lỗi, kết quả mong muốn đã đạt được.
     */
    @Transactional
    public void deleteForMe(UUID currentUserId, UUID messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        conversationService.getActiveParticipantOrThrow(message.getConversation().getId(), currentUserId);

        if (deletionRepository.findDeletedMessageIds(currentUserId, List.of(messageId)).isEmpty()) {
            deletionRepository.save(MessageDeletion.of(message, currentUserId));
        }
    }

    /** UC-20: chỉ người gửi, trong vòng 5 phút. Xóa nội dung thật trong DB — không chỉ đổi status. */
    @Transactional
    public MessageResponse recallMessage(UUID currentUserId, UUID messageId) {
        Message message = messageRepository.findById(messageId)
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));

        if (!message.getSender().getId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.MESSAGE_RECALL_FORBIDDEN);
        }
        if (message.getStatus() == MessageStatus.RECALLED) {
            throw new BusinessException(ErrorCode.MESSAGE_ALREADY_RECALLED);
        }
        if (Duration.between(message.getCreatedAt(), Instant.now()).compareTo(RECALL_WINDOW) > 0) {
            throw new BusinessException(ErrorCode.MESSAGE_RECALL_WINDOW_EXPIRED);
        }

        message.setStatus(MessageStatus.RECALLED);
        message.setContent(null);

        MessageResponse response = conversationMapper.toMessageResponse(message);
        events.publishEvent(new MessageRecalledEvent(response));
        return response;
    }

    /**
     * UC-24: dời con trỏ "đã đọc" của {@code currentUserId} tới {@code messageId}.
     *
     * <p><b>Con trỏ chỉ tiến, không lùi.</b> Client gửi biên nhận rất tùy hứng: tab cũ mở từ hôm
     * qua, người dùng cuộn ngược lên đọc lại tin cũ, mạng gửi lặp frame. Nếu cho phép lùi thì
     * {@code unreadCount} sẽ tự dưng tăng lại — người dùng thấy huy hiệu "chưa đọc" trên hội
     * thoại mình vừa đọc xong. So sánh bằng {@code createdAt} (không phải thời điểm gọi API) vì
     * đó chính là trục mà {@code countUnreadByConversationIds} dùng để đếm.
     *
     * <p>Trả về biên nhận hiện tại (không ném lỗi) khi con trỏ không tiến: đây không phải lỗi
     * của client, chỉ là thao tác không có gì để làm.
     */
    @Transactional
    public ReadReceiptEvent markRead(UUID currentUserId, UUID conversationId, UUID messageId) {
        ConversationParticipant participant =
                conversationService.getActiveParticipantOrThrow(conversationId, currentUserId);

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        if (!message.getConversation().getId().equals(conversationId)) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_IN_CONVERSATION);
        }

        Message current = participant.getLastReadMessage();
        boolean movesForward = current == null || current.getCreatedAt().isBefore(message.getCreatedAt());
        if (!movesForward) {
            return new ReadReceiptEvent(conversationId, currentUserId, current.getId(), current.getCreatedAt());
        }

        participant.setLastReadMessage(message);
        ReadReceiptEvent receipt =
                new ReadReceiptEvent(conversationId, currentUserId, messageId, Instant.now());
        events.publishEvent(new MessageReadEvent(receipt));
        return receipt;
    }

    /**
     * Chốt quyền cho các thao tác realtime KHÔNG chạm database (typing) — chúng không đi qua
     * đường sendMessage nên phải tự kiểm tra, nếu không ai cũng phát được "đang soạn tin" vào
     * hội thoại của người lạ.
     */
    public void assertParticipant(UUID userId, UUID conversationId) {
        conversationService.getActiveParticipantOrThrow(conversationId, userId);
    }

    // ---------- Lắp ráp response (Phase 5: kèm attachment + reaction) ----------

    /**
     * Chuyển 1 trang Message thành DTO, nạp attachment/reaction/tin-đã-ẩn theo LÔ.
     *
     * <p>Toàn bộ trang chỉ tốn 3 query cố định, không phụ thuộc số tin nhắn. Nếu để mapper tự đi
     * lấy từng thứ cho từng tin thì một trang 30 tin sẽ bắn 90 query — chính là bẫy N+1 mà
     * Phase 3 đã tránh cho participant và unread count.
     */
    private List<MessageResponse> toResponses(List<Message> messages, UUID currentUserId) {
        if (messages.isEmpty()) {
            return List.of();
        }
        List<UUID> messageIds = messages.stream().map(Message::getId).toList();

        Map<UUID, List<AttachmentResponse>> attachmentsByMessage =
                attachmentRepository.findByMessageIds(messageIds).stream()
                        .collect(Collectors.groupingBy(
                                attachment -> attachment.getMessage().getId(),
                                Collectors.mapping(conversationMapper::toAttachmentResponse, Collectors.toList())));

        Map<UUID, List<ReactionResponse>> reactionsByMessage = groupReactions(messageIds);
        Set<UUID> hiddenForMe = deletionRepository.findDeletedMessageIds(currentUserId, messageIds);

        return messages.stream()
                .filter(message -> !hiddenForMe.contains(message.getId()))
                .map(message -> conversationMapper.toMessageResponse(
                        message,
                        attachmentsByMessage.getOrDefault(message.getId(), List.of()),
                        reactionsByMessage.getOrDefault(message.getId(), List.of())))
                .toList();
    }

    /** Gom reaction thô thành dạng "emoji + số lượng + ai đã thả" mà giao diện cần. */
    private Map<UUID, List<ReactionResponse>> groupReactions(List<UUID> messageIds) {
        return reactionRepository.findByMessageIds(messageIds).stream()
                .collect(Collectors.groupingBy(reaction -> reaction.getMessage().getId()))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().stream()
                        .collect(Collectors.groupingBy(MessageReaction::getEmoji))
                        .entrySet().stream()
                        .map(byEmoji -> new ReactionResponse(
                                byEmoji.getKey(),
                                byEmoji.getValue().size(),
                                byEmoji.getValue().stream().map(r -> r.getUser().getId()).toList()))
                        // Nhiều emoji nhất lên trước, rồi tới thứ tự chữ cái để kết quả ổn định
                        // giữa các lần gọi — tránh giao diện nhảy lung tung khi cùng số lượng.
                        .sorted(Comparator.comparingLong(ReactionResponse::count).reversed()
                                .thenComparing(ReactionResponse::emoji))
                        .toList()));
    }

    // ---------- Kiểm tra & lưu phần đính kèm ----------

    /**
     * Ràng buộc liên-field không diễn đạt được bằng annotation trên từng field của DTO.
     */
    private void assertPayloadConsistent(SendMessageRequest request) {
        boolean hasContent = request.content() != null && !request.content().isBlank();
        boolean hasAttachment = !request.attachments().isEmpty();

        if (!hasContent && !hasAttachment) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_REQUIRED);
        }
        // IMAGE/FILE/VOICE mà không có tệp là dữ liệu tự mâu thuẫn: client sẽ dựng khung ảnh
        // rỗng. Chặn tại đây thay vì để lọt vào DB rồi giao diện tự xoay xở.
        if (request.type() != MessageType.TEXT && request.type() != MessageType.SYSTEM && !hasAttachment) {
            throw new BusinessException(ErrorCode.ATTACHMENT_REQUIRED);
        }
    }

    private List<MessageAttachment> saveAttachments(Message message, List<AttachmentRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        List<MessageAttachment> attachments = requests.stream()
                .peek(request -> mediaService.assertManagedUrl(request.fileUrl()))
                .map(request -> MessageAttachment.of(message, request.fileUrl(), request.fileName(),
                        request.fileType(), request.fileSize()))
                .toList();
        return attachmentRepository.saveAll(attachments);
    }

    private void saveNewReaction(Message message, User user, String emoji) {
        try {
            reactionRepository.saveAndFlush(MessageReaction.of(message, user, emoji));
        } catch (DataIntegrityViolationException e) {
            // Hai request thả reaction gần như đồng thời cùng vượt qua bước kiểm tra "đã có
            // chưa?"; unique index chặn dòng thứ hai. Trạng thái cuối vẫn ĐÚNG (đã có reaction),
            // nên nuốt lỗi thay vì báo về client — cùng cách xử lý với BlockService ở Phase 2.
            log.debug("Bỏ qua reaction trùng của user {} trên tin nhắn {}", user.getId(), message.getId());
        }
    }

    private Message loadVisibleMessage(UUID messageId) {
        Message message = messageRepository.findById(messageId)
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        if (message.getStatus() == MessageStatus.RECALLED) {
            throw new BusinessException(ErrorCode.MESSAGE_ALREADY_RECALLED);
        }
        return message;
    }

    /** DIRECT chỉ có đúng 2 participant — tìm "người kia" rồi kiểm tra chặn 2 chiều. */
    private void assertOtherPartyNotBlocking(UUID conversationId, UUID senderId) {
        participantRepository.findActiveByConversationIds(List.of(conversationId)).stream()
                .map(p -> p.getUser().getId())
                .filter(id -> !id.equals(senderId))
                .findFirst()
                .ifPresent(otherUserId -> blockService.assertNotBlocked(senderId, otherUserId));
    }

    private int clampLimit(Integer requested) {
        if (requested == null) {
            return DEFAULT_PAGE_LIMIT;
        }
        return Math.min(Math.max(requested, 1), MAX_PAGE_LIMIT);
    }
}
