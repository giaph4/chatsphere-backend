package com.chatsphere.common;

import java.util.UUID;

/**
 * Tập trung MỘT nơi mọi địa chỉ (destination) STOMP của hệ thống.
 *
 * <p>Lý do không rải chuỗi {@code "/topic/conversation/" + id} khắp code: cùng một chuỗi này
 * xuất hiện ở 3 chỗ với 3 vai trò khác nhau — bên gửi ({@code ChatRealtimeBroadcaster}), bên
 * kiểm tra quyền SUBSCRIBE ({@code WebSocketAuthInterceptor}) và test. Lệch nhau 1 ký tự thì
 * tin nhắn vẫn gửi đi "thành công" nhưng không ai nhận được — lỗi im lặng, rất khó truy.
 *
 * <p>Quy ước phân biệt {@code /topic} và {@code /queue}:
 * <ul>
 *   <li>{@code /topic/...} — nhiều người cùng nhận (mọi thành viên 1 hội thoại).</li>
 *   <li>{@code /user/{userId}/queue/...} — riêng 1 người; client chỉ cần subscribe
 *       {@code /queue/...} với tiền tố {@code /user}, Spring tự chèn định danh phiên vào giữa.</li>
 * </ul>
 */
public final class WsDestinations {

    /** Tiền tố client gửi lên server ({@code @MessageMapping}). */
    public static final String APP_PREFIX = "/app";

    /** Tiền tố destination riêng từng người — Spring tự dịch sang session cụ thể. */
    public static final String USER_PREFIX = "/user";

    public static final String TOPIC_PREFIX = "/topic";
    public static final String QUEUE_PREFIX = "/queue";

    /** Mọi sự kiện của 1 hội thoại: tin nhắn mới, thu hồi, đang soạn, đã đọc. */
    public static final String TOPIC_CONVERSATION_PREFIX = TOPIC_PREFIX + "/conversation/";

    /** Bạn bè online/offline — gửi riêng từng người vì mỗi người có danh sách bạn khác nhau. */
    public static final String QUEUE_PRESENCE = QUEUE_PREFIX + "/presence";

    /** Lỗi nghiệp vụ trả về cho chính người vừa gửi frame lỗi. */
    public static final String QUEUE_ERRORS = QUEUE_PREFIX + "/errors";

    private WsDestinations() {
    }

    public static String conversationTopic(UUID conversationId) {
        return TOPIC_CONVERSATION_PREFIX + conversationId;
    }

    /**
     * Tách {@code conversationId} từ destination client xin subscribe.
     *
     * @return null nếu destination không phải topic hội thoại hoặc phần id không phải UUID hợp lệ
     */
    public static UUID parseConversationTopic(String destination) {
        if (destination == null || !destination.startsWith(TOPIC_CONVERSATION_PREFIX)) {
            return null;
        }
        try {
            return UUID.fromString(destination.substring(TOPIC_CONVERSATION_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
