package com.hungtvb.votesystem.admin.ranking;

import com.hungtvb.votesystem.admin.audit.AdminAuditAction;
import com.hungtvb.votesystem.admin.audit.AdminAuditEvent;
import com.hungtvb.votesystem.admin.audit.AdminAuditLogService;
import com.hungtvb.votesystem.admin.audit.AdminAuditTargetType;
import com.hungtvb.votesystem.admin.ranking.dto.AdminRankingStatusResponse;
import com.hungtvb.votesystem.ranking.RankingRebuildResult;
import com.hungtvb.votesystem.ranking.RankingService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminRankingService {
    private final RankingService rankingService;
    private final AdminAuditLogService auditLogService;

    public AdminRankingService(RankingService rankingService, AdminAuditLogService auditLogService) {
        this.rankingService = rankingService;
        this.auditLogService = auditLogService;
    }

    public AdminRankingStatusResponse status() {
        return AdminRankingStatusResponse.from(rankingService.status());
    }

    public AdminRankingStatusResponse rebuild(UUID actorId, String reason) {
        RankingRebuildResult result = rankingService.rebuild();
        try {
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("previous_generation", previousGeneration(result));
            metadata.put("generation", result.generation().id());
            metadata.put("previous_hot_count", Long.toString(result.previousCounts().hot()));
            metadata.put("new_hot_count", Long.toString(result.publishedCounts().hot()));
            metadata.put("new_day_count", Long.toString(result.publishedCounts().day()));
            metadata.put("new_week_count", Long.toString(result.publishedCounts().week()));
            metadata.put("visible_posts", Integer.toString(result.visiblePostCount()));
            auditLogService.append(new AdminAuditEvent(actorId, AdminAuditAction.ADMIN_REBUILD_RANKING,
                    AdminAuditTargetType.RANKING, "ALL", reason, metadata));
            rankingService.completeRebuild(result);
            return status();
        } catch (RuntimeException exception) {
            try {
                rankingService.rollbackRebuild(result);
            } catch (RuntimeException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        }
    }

    private String previousGeneration(RankingRebuildResult result) {
        String generation = result.publishState().previousMetadata().generation();
        return generation == null ? "legacy" : generation;
    }
}
