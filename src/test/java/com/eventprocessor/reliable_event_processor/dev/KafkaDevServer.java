package com.eventprocessor.reliable_event_processor.dev;

import org.springframework.kafka.test.EmbeddedKafkaZKBroker;

public class KafkaDevServer {

    public static void main(String[] args) {
        try {
            System.out.println("Starting in-process Kafka broker on port 9092...");
            EmbeddedKafkaZKBroker broker = new EmbeddedKafkaZKBroker(1, false, 3, "inventory-events");
            broker.kafkaPorts(9092);
            broker.afterPropertiesSet();
            System.out.println("=================================================");
            System.out.println(" Apache Kafka broker is RUNNING on localhost:9092");
            System.out.println(" Topics: inventory-events");
            System.out.println("=================================================");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Stopping Kafka broker...");
                broker.destroy();
            }));

            Thread.currentThread().join();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
