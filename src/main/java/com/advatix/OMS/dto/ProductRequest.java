package com.advatix.OMS.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank String productName,
        @NotBlank String clientName,
        @NotBlank String sku,
        @NotBlank String productDescription,
        @NotBlank String upc,
        @NotNull @DecimalMin("0.001") BigDecimal weight,
        @NotNull @DecimalMin("0.01") BigDecimal unitPrice,
        @NotNull @Min(0) Integer openingStock,
        @NotBlank String warehouseLocation
) {
}
