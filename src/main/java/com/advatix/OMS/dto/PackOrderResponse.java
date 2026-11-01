package com.advatix.OMS.dto;

import com.advatix.OMS.domain.OrderStatus;
import java.math.BigDecimal;

public record PackOrderResponse(
        String orderNumber,
        BigDecimal weight,
        String assignedBox,
        OrderStatus status,
        String message
) {
}
