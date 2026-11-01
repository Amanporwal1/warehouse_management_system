package com.advatix.OMS.dto;

import com.advatix.OMS.domain.ShipmentStatus;
import java.time.LocalDateTime;
import java.util.List;

public record TruckloadResponse(
        String truckloadNumber,
        String carrierName,
        LocalDateTime dispatchDate,
        ShipmentStatus shipmentStatus,
        String remarks,
        List<String> lpnNumbers,
        List<String> directOrderNumbers,
        String message
) {
}
