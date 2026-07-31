package com.hungtvb.votesystem.system;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SystemStatusRepository extends JpaRepository<SystemStatus, Short> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select status from SystemStatus status where status.singletonId = 1")
    Optional<SystemStatus> findSingletonForUpdate();
}
