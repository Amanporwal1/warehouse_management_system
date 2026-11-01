package com.advatix.OMS.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CarrierSyncResponse(
        @JsonProperty("carrier_data_id")
        Long carrierDataId,

        @JsonProperty("shipment_id")
        String shipmentId,

        @JsonProperty("carrier_status")
        String carrierStatus,

        Integer webhook,

        String message
) {
}
