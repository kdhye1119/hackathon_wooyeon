package com.wassup.dinner.common.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(String tokenSecret, long tokenTtlHours) {
}
