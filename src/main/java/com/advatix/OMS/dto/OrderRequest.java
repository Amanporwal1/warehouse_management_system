package com.advatix.OMS.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record OrderRequest(
        @NotBlank String customerName,
        @Email @NotBlank String customerEmail,
        @Valid @NotNull ShippingAddressRequest shippingAddress,
        @Valid @NotEmpty List<OrderItemRequest> items
) {
}
