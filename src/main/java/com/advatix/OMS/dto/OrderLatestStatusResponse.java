package com.advatix.OMS.dto;

import java.time.LocalDateTime;

public record OrderLatestStatusResponse(
        String orderNumber,
        String status,
        Integer statusCode,
        String description,
        LocalDateTime performedAt
) {
}
