package dev.keyval.kvshop.frontend;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.*;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

@RestController
public class ProductController {

    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("frontend-service", "1.0.0");

    private final InventoryService inventoryService;
    private final PricingService pricingService;
    private final CurrencyService currencyService;
    private final CouponService couponService;
    private final AdsService adsService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    @Autowired
    public ProductController(
            InventoryService inventoryService,
            PricingService pricingService,
            CurrencyService currencyService,
            CouponService couponService,
            AdsService adsService) {
        this.inventoryService = inventoryService;
        this.pricingService = pricingService;
        this.currencyService = currencyService;
        this.couponService = couponService;
        this.adsService = adsService;
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/products")
    public List<Product> getProducts() {
        // Create a custom span for the product listing operation
        Span span = tracer.spanBuilder("frontend.get_products")
                .setAttribute("service.name", "frontend")
                .setAttribute("http.method", "GET")
                .setAttribute("http.endpoint", "/products")
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            span.addEvent("starting.inventory.fetch");
            
            CompletableFuture<List<Product>> productsFuture = CompletableFuture.supplyAsync(inventoryService::getInventory);
            List<Product> products;
            try {
                products = productsFuture.get();
                span.setAttribute("products.count", products.size());
                span.addEvent("inventory.fetch.completed");
            } catch (InterruptedException | ExecutionException e) {
                span.setStatus(StatusCode.ERROR, "Failed to fetch inventory");
                span.recordException(e);
                throw new RuntimeException(e);
            }

            // Create a child span for pricing operations
            Span pricingSpan = tracer.spanBuilder("frontend.price_products")
                    .setAttribute("operation", "batch_pricing")
                    .setAttribute("product.count", products.size())
                    .startSpan();
            
            try (Scope pricingScope = pricingSpan.makeCurrent()) {
                pricingSpan.addEvent("starting.parallel.pricing");
                
                List<Callable<Product>> pricedProducts = products
                        .stream()
                        .map(product -> (Callable<Product>) () -> {
                            // Add event for each product pricing
                            Span.current().addEvent("pricing.product", 
                                    io.opentelemetry.api.common.Attributes.of(
                                            io.opentelemetry.api.common.AttributeKey.longKey("product.id"), 
                                            (long) product.getId()
                                    ));
                            product.setPrice(pricingService.getPrice(product.getId()));
                            return product;
                        }).toList();

                List<Future<Product>> pricedProductsFutures;
                try {
                    pricedProductsFutures = executorService.invokeAll(pricedProducts);
                } catch (InterruptedException e) {
                    pricingSpan.setStatus(StatusCode.ERROR, "Interrupted during pricing");
                    pricingSpan.recordException(e);
                    throw new RuntimeException(e);
                }

                products = pricedProductsFutures.stream().map(future -> {
                    try {
                        return future.get();
                    } catch (InterruptedException | ExecutionException e) {
                        pricingSpan.recordException(e);
                        throw new RuntimeException(e);
                    }
                }).toList();
                
                pricingSpan.addEvent("parallel.pricing.completed");
                pricingSpan.setStatus(StatusCode.OK);
            } finally {
                pricingSpan.end();
            }

            // Create span for auxiliary services
            Span auxiliarySpan = tracer.spanBuilder("frontend.auxiliary_services")
                    .setAttribute("operation", "fetch_coupons_and_ads")
                    .startSpan();
            
            try (Scope auxScope = auxiliarySpan.makeCurrent()) {
                // Get coupons
                auxiliarySpan.addEvent("fetching.coupons");
                this.couponService.getCoupons();

                // Get ads
                auxiliarySpan.addEvent("fetching.ads");
                List<Ad> ads = this.adsService.getads();
                auxiliarySpan.setAttribute("ads.count", ads.size());
                
                for (Ad ad : ads) {
                    System.out.println("Ad: " + ad.getTitle() + " - " + ad.getDescription());
                }
                
                auxiliarySpan.addEvent("auxiliary.services.completed");
                auxiliarySpan.setStatus(StatusCode.OK);
            } catch (Exception e) {
                auxiliarySpan.setStatus(StatusCode.ERROR, e.getMessage());
                auxiliarySpan.recordException(e);
                throw e;
            } finally {
                auxiliarySpan.end();
            }

            span.addEvent("products.processing.completed");
            span.setAttribute("final.products.count", products.size());
            span.setStatus(StatusCode.OK);
            
            return products;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    private Observable<Double> pricObservable(int id) {
        return Observable.create(emitter -> {
            // Create span for price observation
            Span span = tracer.spanBuilder("frontend.price_observable")
                    .setAttribute("product.id", id)
                    .startSpan();
            
            try (Scope scope = span.makeCurrent()) {
                double price = pricingService.getPrice(id);
                System.out.println("Price for product with id " + id + " is $" + price);
                span.setAttribute("price.observed", price);
                span.setStatus(StatusCode.OK);
                emitter.onNext(price);
                emitter.onComplete();
            } catch (Exception e) {
                span.setStatus(StatusCode.ERROR, e.getMessage());
                span.recordException(e);
                emitter.onError(e);
            } finally {
                span.end();
            }
        });
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/buy")
    public void buyProduct(@RequestParam(name = "id") int id) {
        // Create a custom span for the purchase operation
        Span span = tracer.spanBuilder("frontend.buy_product")
                .setAttribute("service.name", "frontend")
                .setAttribute("product.id", id)
                .setAttribute("http.method", "POST")
                .setAttribute("http.endpoint", "/buy")
                .setAttribute("business.operation", "purchase")
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            span.addEvent("starting.purchase.validation");
            
            // Validate price via pricing service
            double price = pricObservable(id).subscribeOn(Schedulers.io()).blockingFirst();
            span.setAttribute("validated.price", price);
            
            span.addEvent("fetching.conversion.rate");
            int conversionRate = currencyService.getConversionRate("usd-eur");
            span.setAttribute("conversion.rate", conversionRate);

            String usdPrice = ("$" + price + " USD");
            String eurPrice = ("ִ€" + (price * conversionRate) + " EUR");
            span.setAttribute("price.usd", usdPrice);
            span.setAttribute("price.eur", eurPrice);
            
            System.out.println("Buying product with id " + id + " for " + usdPrice + " (converted to ִִִ" + eurPrice + ")");

            span.addEvent("calling.inventory.service");
            // Call inventory service to buy product
            this.inventoryService.buy(id);

            span.addEvent("applying.coupon");
            // Apply coupon
            this.couponService.applyCoupon();
            
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
}
