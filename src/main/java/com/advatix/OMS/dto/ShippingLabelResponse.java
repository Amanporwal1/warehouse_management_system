package com.advatix.OMS.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShippingLabelResponse(
        String orderNumber,
        String shippingLabelNumber,
        String trackingNumber,
        String carrier,
        String serviceLevel,
        LocalDateTime generatedAt,
        String shipFromAddress,
        String destinationAddress,
        BigDecimal packageWeight,
        String assignedBox,
        String barcodeValue,
        String labelImageContentType,
        String labelImageBase64,
        String labelImageDataUrl,
        String status,
        String message
) {
}
