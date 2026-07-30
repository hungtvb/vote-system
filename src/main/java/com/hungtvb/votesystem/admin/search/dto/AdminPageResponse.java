package com.hungtvb.votesystem.admin.search.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record AdminPageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <T> AdminPageResponse<T> from(Page<T> result) {
        return new AdminPageResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }
}
