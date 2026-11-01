package com.advatix.OMS.domain;

public enum OrderStatus {
    CREATED,
    ALLOCATED,
    BACKORDERED,
    PICK_IN_PROGRESS,
    PICKED,
    READY_TO_PACK,
    PACKED,
    READY_TO_SHIP,
    SHIPPED,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED,
    REJECTED
}
