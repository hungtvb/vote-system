package com.hungtvb.votesystem.comment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hungtvb.votesystem.comment.Comment;
import com.hungtvb.votesystem.vote.VoteType;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommentVoteResponse(
        UUID commentId,
        long voteScore,
        long upVotes,
        long downVotes,
        VoteType myVote
) {
    public static CommentVoteResponse from(Comment comment, VoteType myVote) {
        return new CommentVoteResponse(
                comment.getId(),
                comment.getVoteScore(),
                comment.getUpVotes(),
                comment.getDownVotes(),
                myVote
        );
    }
}
