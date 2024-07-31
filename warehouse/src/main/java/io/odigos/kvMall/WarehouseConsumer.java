package io.odigos.kvMall;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;

public class WarehouseConsumer {

    private final KafkaConsumer<String, String> consumer;

    private static final Logger logger = LoggerFactory.getLogger(WarehouseConsumer.class);

    public WarehouseConsumer(KafkaConsumer<String, String> consumer) {
        this.consumer = consumer;
    }

    public void consume() {
        logger.info("Consuming messages");

        try {
            this.consumer.subscribe(Arrays.asList("orders"));
            while (true) {
                ConsumerRecords<String, String> records = this.consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : records) {
                    logger.info("Received message: " + record.value());
                    record.headers().forEach(header -> logger.info("Header: " + header.key() + " - " + new String(header.value())));
                }
            }
        } catch (WakeupException e) {
            logger.info("Received shutdown signal");
        } catch (Exception e) {
            logger.error("Error while consuming messages", e);
        } finally {
            this.consumer.close();
            logger.info("Consumer has been closed");
        }
    }
}
