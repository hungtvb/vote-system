package com.hungtvb.votesystem.vote;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VotePolicy {
    private final int verdictThreshold;

    public VotePolicy(@Value("${app.vote.verdict-threshold:70}") int verdictThreshold) {
        if (verdictThreshold < 50 || verdictThreshold > 100) {
            throw new IllegalArgumentException("Vote verdict threshold must be between 50 and 100");
        }
        this.verdictThreshold = verdictThreshold;
    }

    public int verdictThreshold() {
        return verdictThreshold;
    }
}