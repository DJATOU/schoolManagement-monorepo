package com.school.management.util;

import lombok.*;
import org.springframework.http.HttpStatus;

@NoArgsConstructor
@Getter
@Setter
@Builder
public class ApiErrorResponse {
    private HttpStatus status;
    private String message;
    private String errorCode;

    public ApiErrorResponse(HttpStatus status, String message, String errorCode) {
        this.status = status;
        this.message = message;
        this.errorCode = errorCode;
    }

}

