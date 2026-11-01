package com.advatix.OMS.dto;

import java.time.LocalDateTime;

public record ApiErrorResponse(LocalDateTime timestamp, int status, String error, String message) {
}
