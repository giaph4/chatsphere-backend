package com.chatsphere.chat.service;

import com.chatsphere.chat.domain.Conversation;
import com.chatsphere.chat.domain.ConversationParticipant;
import com.chatsphere.chat.domain.ConversationType;
import com.chatsphere.chat.domain.ParticipantRole;
import com.chatsphere.chat.dto.ConversationParticipantResponse;
import com.chatsphere.chat.dto.ConversationResponse;
import com.chatsphere.chat.dto.CreateGroupRequest;
import com.chatsphere.chat.dto.UpdateGroupRequest;
import com.chatsphere.chat.mapper.ConversationMapper;
import com.chatsphere.chat.repository.ConversationParticipantRepository;
import com.chatsphere.chat.repository.ConversationRepository;
import com.chatsphere.chat.repository.ConversationUnreadCount;
import com.chatsphere.chat.repository.MessageRepository;
import com.chatsphere.common.BusinessException;
import com.chatsphere.common.ErrorCode;
import com.chatsphere.common.PageResponse;
import com.chatsphere.user.domain.User;
import com.chatsphere.user.repository.UserRepository;
import com.chatsphere.user.service.BlockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final BlockService blockService;
    private final ConversationMapper conversationMapper;

    // ---------- Tạo / lấy hội thoại 1-1 ----------

    /**
     * Trả conversation DIRECT đã có giữa 2 người nếu tồn tại, chỉ tạo mới khi chưa có
     * (UC-14). KHÔNG chặn nếu 2 người đã từng chat rồi mới bị block sau đó — họ vẫn xem
     * được lịch sử cũ, chỉ gửi tin mới bị chặn ở MessageService.sendMessage().
     */
    @Transactional
    public ConversationResponse getOrCreateDirectConversation(UUID currentUserId, UUID otherUserId) {
        if (currentUserId.equals(otherUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_FRIEND_SELF, "Không thể tự tạo hội thoại với chính mình");
        }

        return conversationRepository.findDirectBetween(currentUserId, otherUserId)
                .map(this::toResponse)
                .orElseGet(() -> {
                    blockService.assertNotBlocked(currentUserId, otherUserId);

                    User me = getActiveUser(currentUserId);
                    User other = getActiveUser(otherUserId);

                    Conversation conversation = conversationRepository.save(Conversation.direct(me));
                    participantRepository.save(ConversationParticipant.of(conversation, me, ParticipantRole.MEMBER));
                    participantRepository.save(ConversationParticipant.of(conversation, other, ParticipantRole.MEMBER));

                    return toResponse(conversation);
                });
    }

    // ---------- Tạo nhóm ----------

    /** Người gọi API tự động là ADMIN — không nằm trong request.memberIds() do client gửi lên. */
    @Transactional
    public ConversationResponse createGroup(UUID creatorId, CreateGroupRequest request) {
        User creator = getActiveUser(creatorId);

        List<UUID> memberIds = request.memberIds().stream()
                .distinct()
                .filter(id -> !id.equals(creatorId))
                .toList();

        Conversation conversation = conversationRepository.save(Conversation.group(request.name(), creator));
        participantRepository.save(ConversationParticipant.of(conversation, creator, ParticipantRole.ADMIN));

        for (UUID memberId : memberIds) {
            User member = getActiveUser(memberId);
            participantRepository.save(ConversationParticipant.of(conversation, member, ParticipantRole.MEMBER));
        }

        return toResponse(conversation);
    }

    // ---------- Danh sách hội thoại ----------

    public PageResponse<ConversationResponse> getMyConversations(UUID currentUserId, Pageable pageable) {
        Page<Conversation> page = conversationRepository.findMyConversations(currentUserId, pageable);

        List<UUID> conversationIds = page.getContent().stream().map(Conversation::getId).toList();
        if (conversationIds.isEmpty()) {
            return PageResponse.empty(page);
        }

        Map<UUID, List<ConversationParticipantResponse>> participantsByConversation =
                participantRepository.findActiveByConversationIds(conversationIds).stream()
                        .collect(Collectors.groupingBy(
                                p -> p.getConversation().getId(),
                                Collectors.mapping(conversationMapper::toParticipantResponse, Collectors.toList())));

        Map<UUID, Long> unreadByConversation = messageRepository
                .countUnreadByConversationIds(currentUserId, conversationIds).stream()
                .collect(Collectors.toMap(ConversationUnreadCount::conversationId, ConversationUnreadCount::unreadCount));

        return PageResponse.from(page, conversation -> conversationMapper.toConversationResponse(
                conversation,
                conversation.getLastMessage() == null ? null : conversationMapper.toMessageResponse(conversation.getLastMessage()),
                unreadByConversation.getOrDefault(conversation.getId(), 0L),
                participantsByConversation.getOrDefault(conversation.getId(), List.of())));
    }

    // ---------- Quản lý thành viên nhóm ----------

    @Transactional
    public void addMember(UUID actorId, UUID conversationId, UUID newMemberId) {
        Conversation conversation = getGroupOrThrow(conversationId);
        requireAdmin(conversation, actorId);

        if (participantRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, newMemberId)) {
            throw new BusinessException(ErrorCode.ALREADY_CONVERSATION_MEMBER);
        }

        User newMember = getActiveUser(newMemberId);
        participantRepository.save(ConversationParticipant.of(conversation, newMember, ParticipantRole.MEMBER));
    }

    @Transactional
    public void removeMember(UUID actorId, UUID conversationId, UUID targetUserId) {
        Conversation conversation = getGroupOrThrow(conversationId);
        requireAdmin(conversation, actorId);

        ConversationParticipant target = participantRepository
                .findByConversationIdAndUserIdAndLeftAtIsNull(conversationId, targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER));
        target.setLeftAt(Instant.now());
    }

    @Transactional
    public ConversationResponse updateGroupInfo(UUID actorId, UUID conversationId, UpdateGroupRequest request) {
        Conversation conversation = getGroupOrThrow(conversationId);
        requireAdmin(conversation, actorId);

        conversation.setName(request.name());
        conversation.setAvatarUrl(request.avatarUrl());
        return toResponse(conversation);
    }

    /**
     * UC-17: rời nhóm. Nếu người rời là ADMIN DUY NHẤT còn lại và nhóm vẫn còn thành viên
     * khác, tự động chuyển quyền ADMIN cho người tham gia sớm nhất (không phải người vừa rời).
     * <p>Không dùng khóa bi quan (pessimistic lock) — chấp nhận khe hở lý thuyết khi 2 admin
     * cuối cùng cùng rời trong tích tắc (rất hiếm với quy mô group chat học tập), khác mức độ
     * cứng hóa đã áp dụng cho FriendService.acceptRequest() ở Phase 2 vốn xử lý va chạm ghi
     * dữ liệu tài chính/quan hệ hai chiều.
     */
    @Transactional
    public void leaveGroup(UUID userId, UUID conversationId) {
        Conversation conversation = getGroupOrThrow(conversationId);
        ConversationParticipant leaving = participantRepository
                .findByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER));

        boolean wasAdmin = leaving.getRole() == ParticipantRole.ADMIN;
        leaving.setLeftAt(Instant.now());

        if (!wasAdmin) {
            return;
        }

        long remainingAdmins = participantRepository.countByConversationIdAndLeftAtIsNullAndRole(
                conversationId, ParticipantRole.ADMIN);
        if (remainingAdmins > 0) {
            return;
        }

        participantRepository.findByConversationIdAndLeftAtIsNullOrderByJoinedAtAsc(conversationId).stream()
                .findFirst()
                .ifPresent(nextAdmin -> {
                    nextAdmin.setRole(ParticipantRole.ADMIN);
                    log.info("Chuyển quyền ADMIN nhóm {} cho user {} sau khi admin cuối cùng rời nhóm",
                            conversationId, nextAdmin.getUser().getId());
                });
    }

    // ---------- Tắt thông báo hội thoại (Phase 5 — UC-27) ----------

    /**
     * Tắt thông báo của 1 hội thoại tới thời điểm {@code until}; {@code null} nghĩa là BẬT LẠI.
     *
     * <p>Lưu mốc thời gian hết hạn thay vì cờ boolean: người dùng chọn "tắt 8 tiếng" thì hệ
     * thống phải tự bật lại, không bắt họ nhớ quay lại mở. Với cờ boolean ta sẽ cần thêm một job
     * quét định kỳ để bật lại — còn ở đây, chỉ cần so sánh {@code mutedUntil <= now} ngay trong
     * câu truy vấn chọn người nhận thông báo ({@code findNotifiableUserIds}).
     *
     * <p>Mute là cài đặt của TỪNG người trên hội thoại chung (nằm ở {@code conversation_participants}),
     * nên tắt thông báo không hề ảnh hưởng tới người khác trong nhóm.
     */
    @Transactional
    public void muteConversation(UUID userId, UUID conversationId, Instant until) {
        ConversationParticipant participant = getActiveParticipantOrThrow(conversationId, userId);
        if (until != null && until.isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thời điểm tắt thông báo phải ở tương lai");
        }
        participant.setMutedUntil(until);
    }

    // ---------- helper dùng chung với MessageService ----------

    Conversation getConversationOrThrow(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    ConversationParticipant getActiveParticipantOrThrow(UUID conversationId, UUID userId) {
        return participantRepository.findByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER));
    }

    // ---------- helper riêng ----------

    private Conversation getGroupOrThrow(UUID conversationId) {
        Conversation conversation = getConversationOrThrow(conversationId);
        if (conversation.getType() != ConversationType.GROUP) {
            throw new BusinessException(ErrorCode.NOT_A_GROUP_CONVERSATION);
        }
        return conversation;
    }

    private void requireAdmin(Conversation conversation, UUID userId) {
        ConversationParticipant participant = getActiveParticipantOrThrow(conversation.getId(), userId);
        if (participant.getRole() != ParticipantRole.ADMIN) {
            throw new BusinessException(ErrorCode.GROUP_ADMIN_REQUIRED);
        }
    }

    private ConversationResponse toResponse(Conversation conversation) {
        List<ConversationParticipantResponse> participants = participantRepository
                .findActiveByConversationIds(List.of(conversation.getId())).stream()
                .map(conversationMapper::toParticipantResponse)
                .toList();
        var lastMessage = conversation.getLastMessage() == null
                ? null
                : conversationMapper.toMessageResponse(conversation.getLastMessage());
        return conversationMapper.toConversationResponse(conversation, lastMessage, 0L, participants);
    }

    private User getActiveUser(UUID id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
