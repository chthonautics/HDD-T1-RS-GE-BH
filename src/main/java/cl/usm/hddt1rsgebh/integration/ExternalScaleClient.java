package cl.usm.hddt1rsgebh.integration;

import cl.usm.hddt1rsgebh.dto.Scale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ExternalScaleClient {
    private final RestClient client;

    public ExternalScaleClient(@Value("${sansaweigh.api.url:''}") String url) {
        this.client = RestClient.builder().baseUrl(url).build();
    }

    @Cacheable(value = "scaleSpecs", key = "#id")
    public Scale getScaleSpecifications(String id){
        return this.client.get().uri("/"+id).retrieve().body(Scale.class);
    }
}
