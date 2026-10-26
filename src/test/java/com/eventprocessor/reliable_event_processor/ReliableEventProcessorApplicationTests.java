package com.eventprocessor.reliable_event_processor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"inventory-events", "inventory-events-dlt"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = {
        "event-processor.outbox.enabled=false",
        "event-processor.consumer.enabled=false"
})
class ReliableEventProcessorApplicationTests {

    @Test
    void contextLoads() {
    }

}
