package com.advatix.OMS.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateLpnRequest(
        @NotBlank String createdBy,
        String remarks
) {
}
