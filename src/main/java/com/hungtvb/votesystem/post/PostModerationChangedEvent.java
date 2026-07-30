package com.hungtvb.votesystem.post;

import java.util.UUID;

public record PostModerationChangedEvent(UUID postId, boolean publiclyVisible) {
}
