package com.advatix.OMS.dto;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String productName,
        String clientName,
        String sku,
        String productDescription,
        String upc,
        BigDecimal weight,
        BigDecimal unitPrice,
        Integer availableStock
) {
}
