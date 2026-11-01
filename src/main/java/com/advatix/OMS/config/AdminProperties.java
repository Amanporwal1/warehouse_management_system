package com.advatix.OMS.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.default-admin")
public record AdminProperties(String username, String password, String fullName) {
}
