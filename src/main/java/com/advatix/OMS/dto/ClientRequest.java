package com.advatix.OMS.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ClientRequest(
        @NotBlank String name,
        @NotBlank String clientCode,
        @Email @NotBlank String contactEmail,
        @NotBlank String contactPhone
) {
}
