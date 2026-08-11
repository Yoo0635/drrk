package com.drrk.global.error;

public enum CommonErrorCode implements ErrorCode {

    INVALID_REQUEST(400, "COMMON-400-001", "요청 값이 올바르지 않습니다."),
    INVALID_JSON(400, "COMMON-400-002", "요청 본문을 읽을 수 없습니다."),
    TYPE_MISMATCH(400, "COMMON-400-003", "요청 값의 형식이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(404, "COMMON-404-001", "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(405, "COMMON-405-001", "지원하지 않는 요청 방식입니다."),
    INTERNAL_SERVER_ERROR(500, "COMMON-500-001", "서버 내부 오류가 발생했습니다.");

    private final int status;
    private final String code;
    private final String message;

    CommonErrorCode(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
