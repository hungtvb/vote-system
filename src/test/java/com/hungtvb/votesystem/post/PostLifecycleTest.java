package com.hungtvb.votesystem.post;

import com.hungtvb.votesystem.vote.VoteVerdict;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PostLifecycleTest {

    @Test
    void newBallotStartsOpenAndAcceptsVotesBeforeDeadline() {
        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        Post post = Post.create(
                UUID.randomUUID(),
                "Question",
                "Details",
                "GENERAL",
                now.plus(1, ChronoUnit.HOURS),
                70
        );

        assertEquals(BallotStatus.OPEN, post.getStatus());
        assertTrue(post.acceptsVotes(now));
        assertFalse(post.acceptsVotes(now.plus(1, ChronoUnit.HOURS)));
    }

    @Test
    void manualCloseFreezesBallotAndFinalizesVerdict() {
        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        Post post = Post.create(UUID.randomUUID(), "Question", "Details", "GENERAL", null, 70);

        post.close(now);

        assertEquals(BallotStatus.CLOSED, post.getStatus());
        assertEquals(now, post.getClosedAt());
        assertEquals(VoteVerdict.UNDECIDED, post.getFinalVerdict());
        assertFalse(post.acceptsVotes(now));
        assertThrows(IllegalStateException.class,
                () -> post.update("Changed", "Changed", "GENERAL", null, 70));
    }

    @Test
    void closeIsIdempotentAndDoesNotReplaceOriginalCloseTime() {
        Instant firstClose = Instant.parse("2026-07-25T00:00:00Z");
        Instant secondClose = firstClose.plus(1, ChronoUnit.HOURS);
        Post post = Post.create(UUID.randomUUID(), "Question", "Details", "GENERAL", null, 70);

        post.close(firstClose);
        post.close(secondClose);

        assertEquals(firstClose, post.getClosedAt());
    }
}
