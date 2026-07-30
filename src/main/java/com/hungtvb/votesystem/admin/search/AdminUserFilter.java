package com.hungtvb.votesystem.admin.search;

import com.hungtvb.votesystem.user.AccountStatus;
import com.hungtvb.votesystem.user.Role;

import java.time.Instant;
import java.util.UUID;

public record AdminUserFilter(
        UUID id,
        String query,
        Role role,
        AccountStatus accountStatus,
        Instant createdFrom,
        Instant createdTo
) {
}
