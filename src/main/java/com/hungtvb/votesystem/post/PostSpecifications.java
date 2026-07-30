package com.hungtvb.votesystem.post;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

public final class PostSpecifications {
    private PostSpecifications() {
    }

    public static Specification<Post> matches(PostFilter filter) {
        Specification<Post> specification = publiclyVisible();

        if (filter.query() != null) {
            String pattern = "%" + escapeLike(filter.query().toLowerCase(Locale.ROOT)) + "%";
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), pattern, '\\'),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("content")), pattern, '\\'),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("ballotNumber")), pattern, '\\')
            ));
        }
        if (filter.category() != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("category"), filter.category()));
        }
        if (filter.status() != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), filter.status()));
        }
        if (filter.authorId() != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("authorId"), filter.authorId()));
        }
        return specification;
    }

    public static Specification<Post> publiclyVisible() {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("moderationStatus"), ModerationStatus.VISIBLE);
    }

    public static Specification<Post> createdAtOnOrAfter(Instant start) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), start);
    }

    public static Specification<Post> idIn(Collection<UUID> ids) {
        return (root, query, criteriaBuilder) -> root.get("id").in(ids);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
