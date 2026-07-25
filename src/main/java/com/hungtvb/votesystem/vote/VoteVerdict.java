package com.hungtvb.votesystem.vote;

public enum VoteVerdict {
    UP,
    DOWN,
    UNDECIDED;

    public static VoteVerdict from(long upVotes, long downVotes, int thresholdPercent) {
        long totalVotes = upVotes + downVotes;
        if (totalVotes == 0) {
            return UNDECIDED;
        }

        if (upVotes * 100 >= totalVotes * (long) thresholdPercent) {
            return UP;
        }
        if (downVotes * 100 >= totalVotes * (long) thresholdPercent) {
            return DOWN;
        }
        return UNDECIDED;
    }
}