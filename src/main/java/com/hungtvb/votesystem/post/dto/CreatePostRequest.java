package com.hungtvb.votesystem.post.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreatePostRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 20_000) String content,
        @Size(max = 50) String category,
        Instant closesAt,
        @Min(50) @Max(100) Integer verdictThreshold
) {
    public CreatePostRequest(String title, String content) {
        this(title, content, null, null, null);
    }
}
