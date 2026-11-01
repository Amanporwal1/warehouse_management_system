package com.advatix.OMS.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record CarrierDataRequest(
        @JsonProperty("carrier_status")
        @NotBlank
        String carrierStatus,

        @JsonProperty("shipment_id")
        @NotBlank
        String shipmentId
) {
}
