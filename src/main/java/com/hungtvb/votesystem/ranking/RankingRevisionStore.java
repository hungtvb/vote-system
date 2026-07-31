package com.hungtvb.votesystem.ranking;

import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
public class RankingRevisionStore {
    private static final String CONSISTENCY_LOCK = "vote-system:ranking-consistency";

    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    public RankingRevisionStore(EntityManager entityManager, JdbcTemplate jdbcTemplate) {
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    public long current() {
        Long revision = jdbcTemplate.queryForObject(
                "select revision from ranking_revision where singleton_id = 1",
                Long.class
        );
        if (revision == null) {
            throw new IllegalStateException("Ranking revision row is missing");
        }
        return revision;
    }

    public long bump() {
        requireTransaction();
        lockConsistencyBoundary();
        Long revision = jdbcTemplate.queryForObject("""
                update ranking_revision
                   set revision = revision + 1,
                       updated_at = current_timestamp
                 where singleton_id = 1
             returning revision
                """, Long.class);
        if (revision == null) {
            throw new IllegalStateException("Ranking revision could not be incremented");
        }
        return revision;
    }

    public long lockAndRead() {
        requireTransaction();
        lockConsistencyBoundary();
        return current();
    }

    private void lockConsistencyBoundary() {
        entityManager.createNativeQuery(
                        "select pg_advisory_xact_lock(hashtext(cast(:lockKey as text)))")
                .setParameter("lockKey", CONSISTENCY_LOCK)
                .getSingleResult();
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Ranking revision operations require an active transaction");
        }
    }
}
