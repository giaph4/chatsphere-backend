package com.chatsphere.chat.service;

import com.chatsphere.chat.domain.Conversation;
import com.chatsphere.chat.domain.ConversationParticipant;
import com.chatsphere.chat.domain.ConversationType;
import com.chatsphere.chat.domain.Message;
import com.chatsphere.chat.domain.MessageStatus;
import com.chatsphere.chat.dto.MessageResponse;
import com.chatsphere.chat.dto.SendMessageRequest;
import com.chatsphere.chat.mapper.ConversationMapper;
import com.chatsphere.chat.repository.ConversationParticipantRepository;
import com.chatsphere.chat.repository.MessageRepository;
import com.chatsphere.common.BusinessException;
import com.chatsphere.common.CursorPageResponse;
import com.chatsphere.common.ErrorCode;
import com.chatsphere.user.service.BlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    /** UC-20: chỉ được thu hồi tin nhắn trong vòng 5 phút kể từ lúc gửi. */
    private static final Duration RECALL_WINDOW = Duration.ofMinutes(5);

    private static final int DEFAULT_PAGE_LIMIT = 30;
    private static final int MAX_PAGE_LIMIT = 100;

    private final MessageRepository messageRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ConversationService conversationService;
    private final BlockService blockService;
    private final ConversationMapper conversationMapper;

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

        return conversationMapper.toMessageResponse(saved);
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
        UUID nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        return new CursorPageResponse<>(conversationMapper.toMessageResponses(page), nextCursor, hasNext);
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
        return conversationMapper.toMessageResponse(message);
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
