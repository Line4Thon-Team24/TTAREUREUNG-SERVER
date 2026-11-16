package com.ttareureung.api.domain.file.controller;

import com.ttareureung.api.domain.file.dto.FileUploadResponse;
import com.ttareureung.api.domain.file.exception.FileErrorCode;
import com.ttareureung.api.domain.file.service.FileService;
import com.ttareureung.api.domain.file.storage.LocalFileStorage;
import com.ttareureung.api.global.common.BaseResponse;
import com.ttareureung.api.global.exception.CustomException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/**
 * 사진 업로드 API. 받은 fileKey를 약 등록의 image_url에 넣으면 조회 응답에서 링크로 바뀜
 */
@RestController
@RequestMapping("/api/v1/files")
@Tag(name = "File", description = "약·처방전 사진 업로드")
public class FileController {

    private final FileService fileService;
    private final LocalFileStorage localStorage;

    public FileController(FileService fileService, LocalFileStorage localStorage) {
        this.fileService = fileService;
        this.localStorage = localStorage;
    }

    @Operation(summary = "사진 업로드", description = "JPG/PNG/WEBP/PDF, 10MB 이하. 응답의 fileKey를 약 등록·수정의 image_url에 저장합니다. url은 1시간 동안 열리는 링크입니다.")
    @ApiResponse(responseCode = "201", description = "업로드 완료")
    @ApiResponse(responseCode = "400", description = "file 누락 또는 10MB 초과", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "403", description = "비활성 계정 (AUTH_005)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "415", description = "지원하지 않는 파일 형식 (MEDICATION_007)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "500", description = "저장 실패 (FILE_003)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<FileUploadResponse>> upload(@AuthenticationPrincipal Jwt jwt,
                                                                   @RequestPart("file") MultipartFile file) {
        FileUploadResponse response = fileService.upload(Long.valueOf(jwt.getSubject()), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(response));
    }

    // 서버 디스크 저장 환경 전용. 서명·만료가 붙은 링크만 열리므로 토큰 없이 허용(S3 서명 URL과 같은 역할)
    @Operation(summary = "사진 조회(디스크 저장 환경)", description = "업로드 응답의 url로만 접근합니다. S3 환경에서는 S3 서명 URL이 대신 쓰입니다.")
    @ApiResponse(responseCode = "200", description = "파일 본문")
    @ApiResponse(responseCode = "403", description = "서명 불일치 또는 만료 (FILE_002)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 파일 (FILE_001)", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    @SecurityRequirements
    @GetMapping("/local/{*key}")
    public ResponseEntity<byte[]> serveLocal(@PathVariable String key,
                                             @RequestParam long exp,
                                             @RequestParam String sig) {
        String normalized = key.startsWith("/") ? key.substring(1) : key;
        byte[] body = localStorage.load(normalized, exp, sig);
        String contentType = LocalFileStorage.contentTypeOf(normalized)
                .orElseThrow(() -> new CustomException(FileErrorCode.FILE_NOT_FOUND));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                .body(body);
    }
}
