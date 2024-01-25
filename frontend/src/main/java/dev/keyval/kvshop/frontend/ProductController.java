package dev.keyval.kvshop.frontend;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.*;

@RestController
public class ProductController {

    private final InventoryService inventoryService;
    private final PricingService pricingService;
    private final CouponService couponService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    @Autowired
    public ProductController(InventoryService inventoryService, PricingService pricingService, CouponService couponService) {
        this.inventoryService = inventoryService;
        this.pricingService = pricingService;
        this.couponService = couponService;
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @GetMapping("/products")
    public List<Product> getProducts() {
        CompletableFuture<List<Product>> productsFuture = CompletableFuture.supplyAsync(inventoryService::getInventory);
        List<Product> products;
        try {
            products = productsFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        List<Callable<Product>> pricedProducts = products
                .stream()
                .map(product -> (Callable<Product>) () -> {
                    product.setPrice(pricingService.getPrice(product.getId()));
                    return product;
                }).toList();

        List<Future<Product>> pricedProductsFutures;
        try {
            pricedProductsFutures = executorService.invokeAll(pricedProducts);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        products = pricedProductsFutures.stream().map(future -> {
            try {
                return future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }).toList();

        // Get coupons
        this.couponService.getCoupons();

        return products;
    }

    private Observable<Double> pricObservable(int id) {
        return Observable.create(emitter -> {
            double price = pricingService.getPrice(id);
            System.out.println("Price for product with id " + id + " is $" + price);
            emitter.onNext(price);
            emitter.onComplete();
        });
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @PostMapping("/buy")
    public void buyProduct(@RequestParam(name ="id") int id) {
        double price = pricObservable(id)
                .subscribeOn(Schedulers.io())
                .blockingFirst();

        System.out.println("Buying product with id " + id + " for $" + price);

        // Call inventory service to buy product
        this.inventoryService.buy(id);

        // Apply coupon
        this.couponService.applyCoupon();
    }
}
