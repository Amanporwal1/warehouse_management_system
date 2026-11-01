package com.advatix.OMS.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CarrierDataResponse(

        @JsonProperty("shipment_id")
        String shipmentId,

        @JsonProperty("carrier_status")
        String carrierStatus,

        String message
){


}
