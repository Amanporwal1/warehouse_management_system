package com.advatix.OMS.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PackOrderRequest(
        @NotNull @DecimalMin("0.001") BigDecimal weight
) {
}
