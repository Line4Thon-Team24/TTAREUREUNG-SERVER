
package com.ttareureung.api.global.common;

public record BaseResponse<T>(
        boolean success,
        String code,
        String message,
        T data
) {

    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(
                true,
                "SUCCESS",
                "요청이 성공했습니다.",
                data
        );
    }

    public static BaseResponse<Void> successWithoutData() {
        return new BaseResponse<>(
                true,
                "SUCCESS",
                "요청이 성공했습니다.",
                null
        );
    }

    public static <T> BaseResponse<T> error(
            String code,
            String message
    ) {
        return new BaseResponse<>(
                false,
                code,
                message,
                null
        );
    }
}
