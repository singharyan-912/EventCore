package com.eventprocessor.reliable_event_processor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
public class ReliableEventProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReliableEventProcessorApplication.class, args);
    }

}
