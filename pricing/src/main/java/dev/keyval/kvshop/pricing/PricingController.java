package dev.keyval.kvshop.pricing;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PricingController {

    private PriceResult getPriceFromDb(int id) {
        System.out.println("getPriceFromDb thread: " + Thread.currentThread().getName() + "price is: " + id);
        // Random double between 1 and 50
        double price = Math.random() * 50 + 1;

        // Round to 2 decimal places
        price = Math.round(price * 100.0) / 100.0;

        return new PriceResult(id, price);
    }

    @GetMapping("/price")
    public PriceResult getPrice(@RequestParam int id) {
        System.out.println("Current thread: " + Thread.currentThread().getName());
        // Use RxJava to fetch price asynchronously from db and block until result is available
        return Observable.just(id)
                .map(price -> price * 10)
                .map(this::getPriceFromDb)
            .subscribeOn(Schedulers.io())
            .blockingSingle();
    }
}
