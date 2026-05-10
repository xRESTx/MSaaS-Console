package com.msaas.common;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String path,
        String requestId,
        List<String> details
) {
    public static ErrorResponse of(int status, String error, String code, String path, String requestId, List<String> details) {
        return new ErrorResponse(Instant.now(), status, error, code, path, requestId, details);
    }
}
