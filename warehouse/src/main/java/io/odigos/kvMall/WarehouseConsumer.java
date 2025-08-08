package io.odigos.kvMall;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

public class WarehouseConsumer {

    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("warehouse-service", "1.0.0");

    private final KafkaConsumer<String, String> consumer;

    private static final Logger logger = LoggerFactory.getLogger(WarehouseConsumer.class);

    public WarehouseConsumer(KafkaConsumer<String, String> consumer) {
        this.consumer = consumer;
    }

    public void consume() {
        // Create a main span for the consuming process
        Span mainSpan = tracer.spanBuilder("warehouse.consume_orders")
                .setAttribute("service.name", "warehouse")
                .setAttribute("messaging.system", "kafka")
                .setAttribute("messaging.destination", "orders")
                .setAttribute("messaging.operation", "consume")
                .startSpan();
        
        try (Scope mainScope = mainSpan.makeCurrent()) {
            logger.info("Consuming messages");
            mainSpan.addEvent("starting.message.consumption");

            try {
                this.consumer.subscribe(Arrays.asList("orders"));
                mainSpan.addEvent("subscribed.to.topic");
                
                while (true) {
                    // Create a span for each poll operation
                    Span pollSpan = tracer.spanBuilder("warehouse.poll_messages")
                            .setAttribute("messaging.operation", "poll")
                            .setAttribute("poll.timeout.ms", 100)
                            .startSpan();
                    
                    try (Scope pollScope = pollSpan.makeCurrent()) {
                        ConsumerRecords<String, String> records = this.consumer.poll(Duration.ofMillis(100));
                        pollSpan.setAttribute("messages.received", records.count());
                        
                        if (records.count() > 0) {
                            pollSpan.addEvent("processing.received.messages");
                            
                            for (ConsumerRecord<String, String> record : records) {
                                // Create a span for each message processing
                                Span messageSpan = tracer.spanBuilder("warehouse.process_order")
                                        .setAttribute("messaging.operation", "process")
                                        .setAttribute("messaging.kafka.partition", record.partition())
                                        .setAttribute("messaging.kafka.offset", record.offset())
                                        .setAttribute("order.key", record.key())
                                        .startSpan();
                                
                                try (Scope messageScope = messageSpan.makeCurrent()) {
                                    messageSpan.addEvent("processing.order.message");
                                    
                                    logger.info("Received message: " + record.value());
                                    messageSpan.setAttribute("order.message", record.value());
                                    
                                    // Process headers
                                    messageSpan.addEvent("processing.message.headers");
                                    record.headers().forEach(header -> {
                                        String headerValue = new String(header.value());
                                        logger.info("Header: " + header.key() + " - " + headerValue);
                                        
                                        // Add header as span attribute
                                        messageSpan.setAttribute("header." + header.key(), headerValue);
                                    });
                                    
                                    messageSpan.addEvent("order.processing.completed");
                                    messageSpan.setStatus(StatusCode.OK);
                                } catch (Exception e) {
                                    messageSpan.setStatus(StatusCode.ERROR, e.getMessage());
                                    messageSpan.recordException(e);
                                    logger.error("Error processing message", e);
                                } finally {
                                    messageSpan.end();
                                }
                            }
                            
                            pollSpan.addEvent("all.messages.processed");
                        }
                        
                        pollSpan.setStatus(StatusCode.OK);
                    } catch (Exception e) {
                        pollSpan.setStatus(StatusCode.ERROR, e.getMessage());
                        pollSpan.recordException(e);
                        throw e;
                    } finally {
                        pollSpan.end();
                    }
                }
            } catch (WakeupException e) {
                mainSpan.addEvent("received.shutdown.signal");
                mainSpan.setStatus(StatusCode.OK, "Graceful shutdown");
                logger.info("Received shutdown signal");
            } catch (Exception e) {
                mainSpan.setStatus(StatusCode.ERROR, e.getMessage());
                mainSpan.recordException(e);
                logger.error("Error while consuming messages", e);
            } finally {
                this.consumer.close();
                mainSpan.addEvent("consumer.closed");
                logger.info("Consumer has been closed");
            }
        } finally {
            mainSpan.end();
        }
    }
}
