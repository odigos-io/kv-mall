package dev.keyval.kvshop.frontend;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AdsService {
    private final RestTemplate restTemplate;
    private final String adsServiceHost;

    public AdsService(@Value("${ADS_SERVICE_HOST}") String adsServiceHost,
                      RestTemplateBuilder restTemplateBuilder) {
        this.adsServiceHost = adsServiceHost;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }

    public List<Ad> getads() {
        Ad[] result = restTemplate.getForObject("http://" + adsServiceHost + "/ads", Ad[].class);
        return Arrays.asList(result);
    }
}
