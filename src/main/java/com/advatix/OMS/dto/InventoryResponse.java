package com.advatix.OMS.dto;

public record InventoryResponse(String sku, Integer availableQuantity, Integer reservedQuantity, String warehouseLocation) {
}
