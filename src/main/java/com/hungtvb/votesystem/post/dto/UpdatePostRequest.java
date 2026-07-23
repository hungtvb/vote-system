package com.hungtvb.votesystem.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePostRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String content
) {
}