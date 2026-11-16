package com.ttareureung.api.domain.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record FileUploadResponse(
        @Schema(description = "약 등록 image_url에 저장할 값", example = "uploads/2026/11/3f2a...e1.jpg") String fileKey,
        @Schema(description = "바로 표시용 링크. 만료 후에는 약 조회 응답의 image_url로 다시 받음") String url,
        @Schema(example = "image/jpeg") String contentType,
        @Schema(example = "248211") long size,
        @Schema(description = "url 만료 시각(KST)", example = "2026-11-08T16:00:00") LocalDateTime expiresAt
) {
}
