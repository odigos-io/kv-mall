package dev.keyval.kvshop.pricing;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

@RestController
public class PricingController {

    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("pricing-service", "1.0.0");

    private PriceResult getPriceFromDb(int id) {
        // Create a custom span for database price lookup
        Span span = tracer.spanBuilder("db.price.lookup")
                .setAttribute("db.operation", "select")
                .setAttribute("product.id", id)
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            System.out.println("getPriceFromDb thread: " + Thread.currentThread().getName() + "price is: " + id);
            
            // Simulate database processing time
            span.addEvent("starting.price.calculation");
            
            // Random double between 1 and 50
            double price = Math.random() * 50 + 1;

            // Round to 2 decimal places
            price = Math.round(price * 100.0) / 100.0;

            span.setAttribute("price.calculated", price);
            span.addEvent("price.calculation.completed");
            span.setStatus(StatusCode.OK);

            return new PriceResult(id, price);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @GetMapping("/price")
    public PriceResult getPrice(@RequestParam int id) {
        // Create a custom span for the price service operation
        Span span = tracer.spanBuilder("pricing.get_price")
                .setAttribute("service.name", "pricing")
                .setAttribute("product.id", id)
                .setAttribute("http.method", "GET")
                .setAttribute("http.endpoint", "/price")
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            System.out.println("Current thread: " + Thread.currentThread().getName());
            
            span.addEvent("starting.rxjava.processing");
            
            // Use RxJava to fetch price asynchronously from db and block until result is available
            PriceResult result = Observable.just(id)
                    .map(price -> {
                        // Add event to show RxJava transformation
                        Span.current().addEvent("applying.price.multiplier");
                        return price * 10;
                    })
                    .map(this::getPriceFromDb)
                .subscribeOn(Schedulers.io())
                .blockingSingle();
            
            span.addEvent("rxjava.processing.completed");
            span.setAttribute("price.final", result.getPrice());
            span.setStatus(StatusCode.OK);
            
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
