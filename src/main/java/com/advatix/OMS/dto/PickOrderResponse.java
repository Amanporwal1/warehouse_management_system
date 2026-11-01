package com.advatix.OMS.dto;

import com.advatix.OMS.domain.OrderStatus;
import java.util.List;

public record PickOrderResponse(
        String orderNumber,
        String pickerId,
        String containerId,
        OrderStatus status,
        String message,
        List<PickedInventoryResponse> pickedInventory
) {
}
