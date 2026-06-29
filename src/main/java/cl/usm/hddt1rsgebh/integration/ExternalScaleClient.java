package cl.usm.hddt1rsgebh.integration;

import cl.usm.hddt1rsgebh.dto.Scale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ExternalScaleClient {
    private static final Logger log = LoggerFactory.getLogger(ExternalScaleClient.class);

    private static final String CACHE_NAME = "scaleSpecs";
    // delays applied before each retry; one entry per retry (1s, 5s, 10s)
    private static final long[] DEFAULT_RETRY_DELAYS_MS = {1000, 5000, 10000};

    private final RestClient client;
    private final CacheManager cacheManager;
    private final long[] retryDelaysMs;

    @Autowired
    public ExternalScaleClient(@Value("${sansaweigh.api.url:''}") String url,
                               RestClient.Builder builder, CacheManager cacheManager) {
        this(builder.baseUrl(url).build(), cacheManager, DEFAULT_RETRY_DELAYS_MS);
    }

    // visible for testing: lets tests bind a mock RestClient and shorten the
    // retry delays so the fallback path doesn't actually sleep for ~16s
    ExternalScaleClient(RestClient client, CacheManager cacheManager, long[] retryDelaysMs) {
        this.client = client;
        this.cacheManager = cacheManager;
        this.retryDelaysMs = retryDelaysMs;
    }

    /*
     * Fetches a scale from the external API, retrying on failure with
     * increasing delays (1s, 5s, 10s). Only when every attempt fails does it
     * fall back to the cached value. This is intentionally API-first (not
     * cache-first): every call hits the network, so the cache is purely a
     * fallback for when the external service is unreachable.
     */
    public Scale getScaleSpecifications(String id){
        for (int attempt = 0; attempt <= retryDelaysMs.length; attempt++) {
            if (attempt > 0 && !sleep(retryDelaysMs[attempt - 1])) {
                break; // interrupted; stop retrying and fall back to cache
            }
            try {
                Scale scale = this.client.get().uri("/" + id).retrieve().body(Scale.class);
                cachePut(id, scale);
                return scale;
            } catch (RestClientException e) {
                log.warn("Scale fetch for id {} failed (attempt {}/{}): {}",
                        id, attempt + 1, retryDelaysMs.length + 1, e.getMessage());
            }
        }

        Scale cached = cacheGet(id);
        if (cached != null) {
            log.warn("External scale API unavailable for id {}; serving cached value", id);
            return cached;
        }

        log.error("External scale API unavailable for id {} and no cached value present; "
                + "returning sentinel scale", id);
        return emptyScale();
    }

    // sentinel returned when the API is unreachable and nothing is cached:
    // id -1, all other fields blank/zero
    private Scale emptyScale() {
        return new Scale("-1", "", "", 0.0, 0.0, 0.0);
    }

    // returns false if the thread was interrupted while sleeping
    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void cachePut(String id, Scale scale) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null && scale != null) {
            cache.put(id, scale);
        }
    }

    private Scale cacheGet(String id) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        return cache == null ? null : cache.get(id, Scale.class);
    }
}
