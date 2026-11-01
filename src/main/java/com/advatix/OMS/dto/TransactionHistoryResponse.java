package com.advatix.OMS.dto;

import java.time.LocalDateTime;

public record TransactionHistoryResponse(
        LocalDateTime performedAt,
        String status
) {
}
