package com.hungtvb.votesystem.post;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostModerationStateTests {

    @Test
    void visibleBallotCanBeSoftDeletedDirectly() {
        Post post = ballot();

        post.softDelete(Instant.parse("2026-07-30T10:00:00Z"));

        assertEquals(ModerationStatus.DELETED, post.getModerationStatus());
        assertThrows(IllegalStateException.class,
                () -> post.restore(Instant.parse("2026-07-30T10:01:00Z")));
    }

    @Test
    void hiddenBallotCanBeSoftDeletedButCannotBeRestoredAfterward() {
        Post post = ballot();
        post.hide(Instant.parse("2026-07-30T10:00:00Z"));

        post.softDelete(Instant.parse("2026-07-30T10:01:00Z"));

        assertEquals(ModerationStatus.DELETED, post.getModerationStatus());
        assertThrows(IllegalStateException.class,
                () -> post.restore(Instant.parse("2026-07-30T10:02:00Z")));
    }

    private Post ballot() {
        return Post.create(
                UUID.randomUUID(),
                "Moderation transition ballot",
                "Transition contract",
                "GENERAL",
                null,
                70
        );
    }
}
