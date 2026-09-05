package com.chatsphere.chat.mapper;

import com.chatsphere.chat.domain.Conversation;
import com.chatsphere.chat.domain.ConversationParticipant;
import com.chatsphere.chat.domain.Message;
import com.chatsphere.chat.dto.ConversationParticipantResponse;
import com.chatsphere.chat.dto.ConversationResponse;
import com.chatsphere.chat.dto.MessageResponse;
import com.chatsphere.user.mapper.UserMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * `uses = UserMapper.class`: mọi field kiểu User (participant.user, message.sender)
 * được MapStruct tự chuyển sang UserSummaryResponse bằng UserMapper.toSummaryResponse(),
 * không cần khai báo lại logic ẩn email ở đây (giữ ĐÚNG MỘT nơi quyết định field nào lộ ra).
 * <p>
 * unmappedTargetPolicy = ERROR như UserMapper (Phase 2): thêm field mới vào DTO mà quên
 * map thì build FAIL ngay.
 */
@Mapper(
        componentModel = "spring",
        uses = UserMapper.class,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface ConversationMapper {

    @Mapping(target = "conversationId", source = "conversation.id")
    @Mapping(target = "replyToMessageId", source = "replyToMessage.id")
    @Mapping(target = "forwardedFromMessageId", source = "forwardedFromMessage.id")
    MessageResponse toMessageResponse(Message message);

    List<MessageResponse> toMessageResponses(List<Message> messages);

    ConversationParticipantResponse toParticipantResponse(ConversationParticipant participant);

    List<ConversationParticipantResponse> toParticipantResponses(List<ConversationParticipant> participants);

    /**
     * `lastMessage`/`unreadCount`/`participants` không nằm sẵn trên entity Conversation
     * (tránh load N+1 khi Mapper tự ý fetch) — Service tính trước rồi truyền vào,
     * cùng nguyên tắc STATELESS đã áp dụng cho UserMapper.toFriendResponse() ở Phase 2.
     */
    default ConversationResponse toConversationResponse(
            Conversation conversation,
            MessageResponse lastMessage,
            long unreadCount,
            List<ConversationParticipantResponse> participants
    ) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getType(),
                conversation.getName(),
                conversation.getAvatarUrl(),
                lastMessage,
                unreadCount,
                participants,
                conversation.getUpdatedAt()
        );
    }
}
