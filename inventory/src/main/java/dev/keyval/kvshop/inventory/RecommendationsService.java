package dev.keyval.kvshop.inventory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.List;

@Service
public class RecommendationsService {
    private final RestTemplate restTemplate;
    private final String recommendationsServiceUrl;

    public RecommendationsService(@Value("${recommendations.service.url}") String recommendationsServiceUrl) {
        this.recommendationsServiceUrl = recommendationsServiceUrl;
        this.restTemplate = new RestTemplate();
    }

    public List<String> getRecommendations(int productId) {
        String url = recommendationsServiceUrl + "/recommendations?product_id=" + productId;
        RecommendationsResponse response = restTemplate.getForObject(url, RecommendationsResponse.class);
        return response != null ? response.getRecommendations() : List.of();
    }

    private static class RecommendationsResponse {
        private String productId;
        private List<String> recommendations;

        public String getProductId() {
            return productId;
        }

        public void setProductId(String productId) {
            this.productId = productId;
        }

        public List<String> getRecommendations() {
            return recommendations;
        }

        public void setRecommendations(List<String> recommendations) {
            this.recommendations = recommendations;
        }
    }
} 