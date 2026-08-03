package com.hungtvb.votesystem.auth.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountSecurityEventRepository extends JpaRepository<AccountSecurityEvent, UUID> {
}
