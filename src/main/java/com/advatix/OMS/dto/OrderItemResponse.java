package com.advatix.OMS.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        String sku,
        String productName,
        int quantity,
        int fulfilledQuantity,
        int backorderedQuantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}
