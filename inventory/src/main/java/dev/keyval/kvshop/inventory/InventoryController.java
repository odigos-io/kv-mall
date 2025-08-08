package dev.keyval.kvshop.inventory;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

@RestController
public class InventoryController {

    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("inventory-service", "1.0.0");

    private static final List<InventoryItem> items = loadItems();
    private final KafkaProducer<String, String> producer;
    private static final Integer watchProductID = 12;

    @Autowired
    public InventoryController(InventoryKafkaProducer producer) {
        this.producer = producer.getProducer();
    }

    @GetMapping("/inventory")
    public List<InventoryItem> getInventory() {
        // Create a custom span for inventory retrieval
        Span span = tracer.spanBuilder("inventory.get_all")
                .setAttribute("service.name", "inventory")
                .setAttribute("http.method", "GET")
                .setAttribute("http.endpoint", "/inventory")
                .setAttribute("operation", "list_products")
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            System.out.println("Returning inventory");
            
            span.addEvent("loading.inventory.items");
            span.setAttribute("inventory.size", items.size());
            span.addEvent("inventory.loaded");
            span.setStatus(StatusCode.OK);
            
            return items;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @PostMapping("/buy")
    public void buyProduct(@RequestParam int id) throws InterruptedException {
        // Create a custom span for product purchase
        Span span = tracer.spanBuilder("inventory.buy_product")
                .setAttribute("service.name", "inventory")
                .setAttribute("product.id", id)
                .setAttribute("http.method", "POST")
                .setAttribute("http.endpoint", "/buy")
                .setAttribute("business.operation", "purchase")
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            System.out.println("Buying product with id " + id);
            
            span.addEvent("processing.purchase.request");
            
            // Check if this is the watch product (special handling)
            if (id == watchProductID) {
                span.setAttribute("product.type", "watch");
                span.setAttribute("requires.lock", true);
                span.addEvent("initiating.product.lock");
                
                System.out.println("Simulating lock for product with id " + id);
                triggerLockRequest(10);  // Lock for 10s
                
                span.addEvent("product.lock.completed");
            } else {
                span.setAttribute("requires.lock", false);
            }
            
            // Create child span for Kafka message publishing
            Span kafkaSpan = tracer.spanBuilder("inventory.publish_order")
                    .setAttribute("messaging.system", "kafka")
                    .setAttribute("messaging.destination", "orders")
                    .setAttribute("messaging.operation", "publish")
                    .setAttribute("product.id", id)
                    .startSpan();
            
            try (Scope kafkaScope = kafkaSpan.makeCurrent()) {
                kafkaSpan.addEvent("creating.kafka.record");
                
                ProducerRecord<String, String> record = new ProducerRecord<>("orders", "" + id, "Product with id " + id + " has been bought");
                record.headers().add("product-id", String.valueOf(id).getBytes());
                
                kafkaSpan.addEvent("sending.kafka.message");
                this.producer.send(record);
                this.producer.flush();
                
                kafkaSpan.addEvent("kafka.message.sent");
                kafkaSpan.setStatus(StatusCode.OK);
            } catch (Exception e) {
                kafkaSpan.setStatus(StatusCode.ERROR, e.getMessage());
                kafkaSpan.recordException(e);
                throw e;
            } finally {
                kafkaSpan.end();
            }
            
            span.addEvent("purchase.completed");
            span.setStatus(StatusCode.OK);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    private static List<InventoryItem> loadItems() {
        return List.of(
            new InventoryItem(1, "T Shirt", "https://emoji.aranja.com/static/emoji-data/img-apple-160/1f455.png"),
            new InventoryItem(2, "Pants", "https://emoji.aranja.com/static/emoji-data/img-apple-160/1f456.png"),
            new InventoryItem(3, "Shoes", "https://emoji.aranja.com/static/emoji-data/img-apple-160/1f462.png"),
            new InventoryItem(4, "Hat", "https://emoji.aranja.com/static/emoji-data/img-apple-160/1f9e2.png"),
            new InventoryItem(5, "Socks", "https://emoji.aranja.com/static/emoji-data/img-apple-160/1f9e6.png"),
            new InventoryItem(6, "Gloves", "https://emoji.aranja.com/static/emoji-data/img-apple-160/1f9e4.png"),
            new InventoryItem(7, "Scarf", "https://emoji.aranja.com/static/emoji-data/img-apple-160/1f9e3.png"),
            new InventoryItem(8, "Jacket", "https://emoji.aranja.com/static/emoji-data/img-apple-160/1f9e5.png"),
            new InventoryItem(9, "Kimono", "https://emoji.aranja.com/static/emoji-data/img-apple-160/1f458.png"),
            new InventoryItem(10, "Purse", "https://emoji.aranja.com/static/emoji-data/img-apple-160/1f45b.png"),
            new InventoryItem(11, "Tophat", "https://emoji.aranja.com/static/emoji-data/img-apple-160/1f3a9.png"),
            new InventoryItem(12, "Watch", "https://emoji.aranja.com/static/emoji-data/img-apple-160/231a.png"),
            new InventoryItem(13, "Sunglasses", "https://emoji.aranja.com/static/emoji-data/img-apple-160/1f576-fe0f.png"),
            new InventoryItem(14, "Womans Hat", "https://emoji.aranja.com/static/emoji-data/img-apple-160/1f452.png"),
            new InventoryItem(15, "Sandal", "https://emoji.aranja.com/static/emoji-data/img-apple-160/1f461.png"),
            new InventoryItem(16, "Bracelet", "https://emoji.aranja.com/static/emoji-data/img-apple-160/1f4ff.png"),
            new InventoryItem(17, "Ring", "https://emoji.aranja.com/static/emoji-data/img-apple-160/1f48d.png"),
            new InventoryItem(18, "Suit", "https://emoji.aranja.com/static/emoji-data/img-apple-160/1f454.png"),
            new InventoryItem(19, "Dress", "https://emoji.aranja.com/static/emoji-data/img-apple-160/1f457.png"),
            new InventoryItem(20, "Eyeglasses", "https://emoji.aranja.com/static/emoji-data/img-apple-160/1f453.png")
        );
    }

    private void triggerLockRequest(int lockDuration) {
        // Create a custom span for the lock request
        Span span = tracer.spanBuilder("inventory.trigger_lock")
                .setAttribute("operation", "external_service_call")
                .setAttribute("target.service", "ads")
                .setAttribute("lock.duration", lockDuration)
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            span.addEvent("preparing.lock.request");
            
            RestTemplate restTemplate = new RestTemplate();
            String url = "http://" + System.getenv("ADS_SERVICE_HOST") + "/simulate-lock";
            span.setAttribute("target.url", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("lock_duration", lockDuration);

            span.addEvent("sending.lock.request");
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            span.setAttribute("response.status", response.getStatusCode().value());
            span.addEvent("lock.request.completed");
            span.setStatus(StatusCode.OK);
            
            System.out.println("Lock request sent. Status: " + response.getStatusCode());
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            System.err.println("Failed to trigger lock: " + e.getMessage());
        } finally {
            span.end();
        }
    }

}
