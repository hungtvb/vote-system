package com.hungtvb.votesystem.vote;

public enum VoteType {
    UP(1),
    DOWN(-1);

    private final int delta;

    VoteType(int delta) {
        this.delta = delta;
    }

    public int delta() {
        return delta;
    }
}
