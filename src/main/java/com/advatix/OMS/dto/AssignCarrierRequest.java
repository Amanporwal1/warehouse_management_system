package com.advatix.OMS.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record AssignCarrierRequest(
        @JsonAlias("carrier_name")
        @NotBlank String carrierName
) {
}
