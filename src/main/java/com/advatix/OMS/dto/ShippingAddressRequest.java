package com.advatix.OMS.dto;

import jakarta.validation.constraints.NotBlank;

public record ShippingAddressRequest(
        @NotBlank String addressLine1,
        String addressLine2,
        @NotBlank String city,
        @NotBlank String state,
        @NotBlank String postalCode,
        @NotBlank String country
) {
}
