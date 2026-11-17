package com.ttareureung.api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SignupResponse(@Schema(example = "1") Long accountId) {
}
