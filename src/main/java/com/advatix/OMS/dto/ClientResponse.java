package com.advatix.OMS.dto;

public record ClientResponse(Long id, String name, String clientCode, String contactEmail, String contactPhone, boolean active) {
}
