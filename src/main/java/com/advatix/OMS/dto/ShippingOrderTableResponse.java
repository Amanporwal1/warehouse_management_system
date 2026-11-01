package com.advatix.OMS.dto;

import com.advatix.OMS.domain.OrderStatus;
import java.time.LocalDateTime;

public record ShippingOrderTableResponse(
        String orderNumber,
        String customerName,
        String customerEmail,
        OrderStatus status,
        String assignedBox,
        String shippingLabelNumber,
        String trackingNumber,
        String carrier,
        String serviceLevel,
        LocalDateTime shippingLabelGeneratedAt,
        String lpnNumber,
        String truckloadNumber,
        String destinationAddress
) {
}
