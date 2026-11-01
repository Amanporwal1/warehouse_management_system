package com.advatix.OMS.dto;

import com.advatix.OMS.domain.OrderStatus;
import com.advatix.OMS.domain.ShipmentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderTransactionResponse(
        String orderNumber,
        String customerName,
        String customerEmail,
        OrderStatus status,
        String shipmentStatus,
        Integer orderStatusCode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime shippingLabelGeneratedAt,
        LocalDateTime lastCarrierUpdateAt,
        LocalDateTime deliveredAt,
        String assignedPicker,
        String pickContainer,
        String assignedBox,
        BigDecimal packageWeight,
        String shipmentId,
        String trackingNumber,
        String shippingLabelNumber,
        String shippingCarrier,
        String serviceLevel,
        String lpnNumber,
        String lpnCarrier,
        ShipmentStatus lpnShipmentStatus,
        String truckloadNumber,
        String truckloadCarrier,
        ShipmentStatus truckloadShipmentStatus,
        LocalDateTime truckloadDispatchDate,
        Integer totalItemQuantity,
        Integer backorderedQuantity,
        String itemSummary,
        String destinationAddress
) {
}
