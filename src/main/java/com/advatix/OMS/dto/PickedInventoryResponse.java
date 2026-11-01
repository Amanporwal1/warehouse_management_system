package com.advatix.OMS.dto;

public record PickedInventoryResponse(
        String sku,
        String serialNumber,
        String fromLocation,
        String toContainer
) {
}
