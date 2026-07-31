package com.hungtvb.votesystem.ranking;

import org.springframework.stereotype.Component;

@Component
public class RankingRebuildLifecycle {
    public void afterSnapshotPrepared(RankingRebuildPreview preview) {
        // Production no-op. Integration tests use this seam to coordinate concurrent commits deterministically.
    }
}
