package com.hungtvb.votesystem.ranking;

import java.time.Instant;

public interface RankingFormula {
    double score(long voteScore, Instant createdAt, Instant now);
}
