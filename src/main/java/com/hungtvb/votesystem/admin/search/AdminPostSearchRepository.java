package com.hungtvb.votesystem.admin.search;

import com.hungtvb.votesystem.post.Post;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
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
public class AdminPostSearchRepository {
    private final EntityManager entityManager;

    public AdminPostSearchRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Page<Post> search(AdminPostFilter filter, Pageable pageable) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Post> query = builder.createQuery(Post.class);
        Root<Post> root = query.from(Post.class);
        query.select(root)
                .where(predicates(filter, root, builder))
                .orderBy(builder.desc(root.get("createdAt")), builder.desc(root.get("id")));

        TypedQuery<Post> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult(pageOffset(pageable));
        typedQuery.setMaxResults(pageable.getPageSize());
        List<Post> content = typedQuery.getResultList();

        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<Post> countRoot = countQuery.from(Post.class);
        countQuery.select(builder.count(countRoot))
                .where(predicates(filter, countRoot, builder));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }

    public Optional<Post> findById(UUID postId) {
        return Optional.ofNullable(entityManager.find(Post.class, postId));
    }

    private Predicate[] predicates(AdminPostFilter filter,
                                   Root<Post> root,
                                   CriteriaBuilder builder) {
        List<Predicate> predicates = new ArrayList<>();
        if (filter.id() != null) {
            predicates.add(builder.equal(root.get("id"), filter.id()));
        }
        if (filter.ballotNumber() != null) {
            predicates.add(builder.equal(root.get("ballotNumber"), filter.ballotNumber()));
        }
        if (filter.query() != null) {
            String pattern = "%" + escapeLike(filter.query().toLowerCase(Locale.ROOT)) + "%";
            predicates.add(builder.or(
                    builder.like(builder.lower(root.get("title")), pattern, '\\'),
                    builder.like(builder.lower(root.get("content")), pattern, '\\')
            ));
        }
        if (filter.authorId() != null) {
            predicates.add(builder.equal(root.get("authorId"), filter.authorId()));
        }
        if (filter.category() != null) {
            predicates.add(builder.equal(root.get("category"), filter.category()));
        }
        if (filter.status() != null) {
            predicates.add(builder.equal(root.get("status"), filter.status()));
        }
        if (filter.moderationStatus() != null) {
            predicates.add(builder.equal(root.get("moderationStatus"), filter.moderationStatus()));
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
