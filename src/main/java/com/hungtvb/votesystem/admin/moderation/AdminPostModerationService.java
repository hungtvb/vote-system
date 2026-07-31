package com.hungtvb.votesystem.admin.moderation;

import com.hungtvb.votesystem.admin.audit.AdminAuditAction;
import com.hungtvb.votesystem.admin.audit.AdminAuditEvent;
import com.hungtvb.votesystem.admin.audit.AdminAuditLogService;
import com.hungtvb.votesystem.admin.audit.AdminAuditTargetType;
import com.hungtvb.votesystem.admin.moderation.dto.AdminPostModerationResponse;
import com.hungtvb.votesystem.common.error.ConflictException;
import com.hungtvb.votesystem.common.error.ResourceNotFoundException;
import com.hungtvb.votesystem.post.ModerationStatus;
import com.hungtvb.votesystem.post.Post;
import com.hungtvb.votesystem.post.PostModerationChangedEvent;
import com.hungtvb.votesystem.post.PostRepository;
import com.hungtvb.votesystem.ranking.RankingChangedEvent;
import com.hungtvb.votesystem.ranking.RankingRevisionStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

@Service
public class AdminPostModerationService {
    private final PostRepository postRepository;
    private final AdminAuditLogService auditLogService;
    private final RankingRevisionStore rankingRevisionStore;
    private final ApplicationEventPublisher eventPublisher;

    public AdminPostModerationService(PostRepository postRepository,
                                      AdminAuditLogService auditLogService,
                                      RankingRevisionStore rankingRevisionStore,
                                      ApplicationEventPublisher eventPublisher) {
        this.postRepository = postRepository;
        this.auditLogService = auditLogService;
        this.rankingRevisionStore = rankingRevisionStore;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AdminPostModerationResponse hide(UUID actorId, UUID postId, String reason) {
        return mutate(actorId, postId, reason, AdminAuditAction.ADMIN_HIDE_POST, Post::hide);
    }

    @Transactional
    public AdminPostModerationResponse restore(UUID actorId, UUID postId, String reason) {
        return mutate(actorId, postId, reason, AdminAuditAction.ADMIN_RESTORE_POST, Post::restore);
    }

    @Transactional
    public AdminPostModerationResponse softDelete(UUID actorId, UUID postId, String reason) {
        return mutate(actorId, postId, reason, AdminAuditAction.ADMIN_DELETE_POST, Post::softDelete);
    }

    private AdminPostModerationResponse mutate(UUID actorId,
                                               UUID postId,
                                               String reason,
                                               AdminAuditAction action,
                                               BiConsumer<Post, Instant> transition) {
        Post post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        ModerationStatus previousStatus = post.getModerationStatus();

        try {
            transition.accept(post, Instant.now());
        } catch (IllegalStateException exception) {
            throw new ConflictException("Ballot moderation state does not allow this action");
        }

        auditLogService.append(new AdminAuditEvent(
                actorId,
                action,
                AdminAuditTargetType.POST,
                postId.toString(),
                reason,
                Map.of(
                        "previous_status", previousStatus.name(),
                        "new_status", post.getModerationStatus().name()
                )
        ));
        rankingRevisionStore.bump();

        if (post.isPubliclyVisible()) {
            eventPublisher.publishEvent(RankingChangedEvent.upsert(
                    post.getId(), post.getVoteScore(), post.getCreatedAt()));
        } else {
            eventPublisher.publishEvent(RankingChangedEvent.delete(post.getId(), post.getCreatedAt()));
        }
        eventPublisher.publishEvent(new PostModerationChangedEvent(
                post.getId(), post.isPubliclyVisible()));

        return AdminPostModerationResponse.from(post);
    }
}
