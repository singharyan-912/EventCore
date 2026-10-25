package com.eventprocessor.reliable_event_processor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "event-processor.admin")
public class AdminSecurityProperties {

    private String apiKey = "admin-secret-key-12345";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
