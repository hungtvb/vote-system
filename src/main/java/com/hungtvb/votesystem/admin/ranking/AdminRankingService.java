package com.hungtvb.votesystem.admin.ranking;

import com.hungtvb.votesystem.admin.audit.AdminAuditAction;
import com.hungtvb.votesystem.admin.audit.AdminAuditEvent;
import com.hungtvb.votesystem.admin.audit.AdminAuditLogService;
import com.hungtvb.votesystem.admin.audit.AdminAuditTargetType;
import com.hungtvb.votesystem.admin.ranking.dto.AdminRankingStatusResponse;
import com.hungtvb.votesystem.ranking.RankingRebuildPreview;
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
        rankingService.rebuild(preview -> appendAudit(actorId, reason, preview));
        return status();
    }

    private void appendAudit(UUID actorId, String reason, RankingRebuildPreview preview) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("previous_generation", previousGeneration(preview));
        metadata.put("generation", preview.generation().id());
        metadata.put("previous_hot_count", Long.toString(preview.previousCounts().hot()));
        metadata.put("new_hot_count", Long.toString(preview.stagedCounts().hot()));
        metadata.put("new_day_count", Long.toString(preview.stagedCounts().day()));
        metadata.put("new_week_count", Long.toString(preview.stagedCounts().week()));
        metadata.put("visible_posts", Long.toString(preview.visiblePostCount()));
        metadata.put("redis_batches", Integer.toString(preview.redisBatchCount()));
        metadata.put("source_revision", Long.toString(preview.sourceRevision()));
        auditLogService.append(new AdminAuditEvent(actorId, AdminAuditAction.ADMIN_REBUILD_RANKING,
                AdminAuditTargetType.RANKING, "ALL", reason, metadata));
    }

    private String previousGeneration(RankingRebuildPreview preview) {
        String generation = preview.previousMetadata().generation();
        return generation == null ? "legacy" : generation;
    }
}
