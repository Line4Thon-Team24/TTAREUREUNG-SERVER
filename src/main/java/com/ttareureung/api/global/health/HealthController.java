package com.ttareureung.api.global.health;

import com.ttareureung.api.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Health", description = "서버 상태 확인")
public class HealthController {

    @Operation(summary = "서버 상태 확인", description = "인증 없이 서버의 응답 여부를 확인합니다.")
    @GetMapping("/api/v1/health")
    public BaseResponse<String> health() {
        return BaseResponse.success("서버 정상 작동");
    }
}
