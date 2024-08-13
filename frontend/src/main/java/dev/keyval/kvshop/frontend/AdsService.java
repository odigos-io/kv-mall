package dev.keyval.kvshop.frontend;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AdsService {
    private final String adsServiceHost;

    public AdsService(@Value("${ADS_SERVICE_HOST}") String adsServiceHost) {
        this.adsServiceHost = adsServiceHost;
    }

    public List<Ad> getads() {
        // Make http request to product service
        RestTemplate restTemplate = new RestTemplate();
        Ad[] result = restTemplate.getForObject("http://" + adsServiceHost + "/ads", Ad[].class);
    
        // Convert result to list of products
        return Arrays.asList(result);
    }
}
