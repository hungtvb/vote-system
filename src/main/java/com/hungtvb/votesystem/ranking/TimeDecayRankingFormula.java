package com.hungtvb.votesystem.ranking;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class TimeDecayRankingFormula implements RankingFormula {
    @Override
    public double score(long voteScore, Instant createdAt, Instant now) {
        double ageHours = Math.max(0.0, Duration.between(createdAt, now).toMinutes() / 60.0);
        double magnitude = Math.copySign(Math.log10(Math.abs(voteScore) + 1.0), voteScore);
        return magnitude + (createdAt.getEpochSecond() / 45_000.0) - (ageHours / 72.0);
    }
}
