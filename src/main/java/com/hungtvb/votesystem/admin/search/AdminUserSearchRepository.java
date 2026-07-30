package com.hungtvb.votesystem.admin.search;

import com.hungtvb.votesystem.user.AccountStatus;
import com.hungtvb.votesystem.user.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AdminUserSearchRepository {
    private final EntityManager entityManager;

    public AdminUserSearchRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Page<AppUser> search(AdminUserFilter filter, Pageable pageable, Instant now) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<AppUser> query = builder.createQuery(AppUser.class);
        Root<AppUser> root = query.from(AppUser.class);
        query.select(root)
                .where(predicates(filter, now, root, builder))
                .orderBy(builder.desc(root.get("createdAt")), builder.desc(root.get("id")));

        TypedQuery<AppUser> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult(pageOffset(pageable));
        typedQuery.setMaxResults(pageable.getPageSize());
        List<AppUser> content = typedQuery.getResultList();

        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<AppUser> countRoot = countQuery.from(AppUser.class);
        countQuery.select(builder.count(countRoot))
                .where(predicates(filter, now, countRoot, builder));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }

    public Optional<AppUser> findById(UUID userId) {
        return Optional.ofNullable(entityManager.find(AppUser.class, userId));
    }

    private Predicate[] predicates(AdminUserFilter filter,
                                   Instant now,
                                   Root<AppUser> root,
                                   CriteriaBuilder builder) {
        List<Predicate> predicates = new ArrayList<>();
        if (filter.id() != null) {
            predicates.add(builder.equal(root.get("id"), filter.id()));
        }
        if (filter.query() != null) {
            String pattern = "%" + escapeLike(filter.query().toLowerCase(Locale.ROOT)) + "%";
            predicates.add(builder.or(
                    builder.like(builder.lower(root.get("email")), pattern, '\\'),
                    builder.like(builder.lower(root.get("displayName")), pattern, '\\')
            ));
        }
        if (filter.role() != null) {
            predicates.add(builder.equal(root.get("role"), filter.role()));
        }
        if (filter.accountStatus() != null) {
            predicates.add(effectiveStatus(filter.accountStatus(), now, root, builder));
        }
        if (filter.createdFrom() != null) {
            predicates.add(builder.greaterThanOrEqualTo(
                    root.<Instant>get("createdAt"), filter.createdFrom()));
        }
        if (filter.createdTo() != null) {
            predicates.add(builder.lessThanOrEqualTo(
                    root.<Instant>get("createdAt"), filter.createdTo()));
        }
        return predicates.toArray(Predicate[]::new);
    }

    private Predicate effectiveStatus(AccountStatus requested,
                                      Instant now,
                                      Root<AppUser> root,
                                      CriteriaBuilder builder) {
        Path<AccountStatus> status = root.get("accountStatus");
        Path<Instant> until = root.get("statusUntil");
        Predicate expiredRestriction = builder.and(
                builder.notEqual(status, AccountStatus.ACTIVE),
                builder.isNotNull(until),
                builder.lessThanOrEqualTo(until, now)
        );
        if (requested == AccountStatus.ACTIVE) {
            return builder.or(builder.equal(status, AccountStatus.ACTIVE), expiredRestriction);
        }
        return builder.and(
                builder.equal(status, requested),
                builder.or(builder.isNull(until), builder.greaterThan(until, now))
        );
    }

    private int pageOffset(Pageable pageable) {
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Page offset is too large");
        }
        return (int) pageable.getOffset();
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
