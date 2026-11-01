package com.advatix.OMS.dto;

import com.advatix.OMS.domain.ShipmentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record LpnResponse(
        String lpnNumber,
        LocalDateTime createdAt,
        String createdBy,
        String carrierName,
        ShipmentStatus shipmentStatus,
        Integer totalOrders,
        Integer totalQuantity,
        BigDecimal totalWeight,
        String remarks,
        String truckloadNumber,
        List<String> orderNumbers,
        String message
) {
}
