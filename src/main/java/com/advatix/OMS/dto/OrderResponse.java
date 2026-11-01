package com.advatix.OMS.dto;

import com.advatix.OMS.domain.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        String orderNumber,
        OrderStatus status,
        String message,
        LocalDateTime createdAt,
        List<OrderItemResponse> items
) {
}
