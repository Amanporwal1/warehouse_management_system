package com.advatix.OMS.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PickOrderRequest(
        @NotBlank String pickerId,
        @NotBlank String containerId,
        @NotEmpty List<@NotBlank String> serialNumbers
) {
}
