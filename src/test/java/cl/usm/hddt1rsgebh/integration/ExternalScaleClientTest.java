package cl.usm.hddt1rsgebh.integration;

import cl.usm.hddt1rsgebh.dto.Scale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.MediaType;

/*
 * The external sansaweigh API is faked with MockRestServiceServer, which is
 * bound directly to the RestClient builder, so no real socket is ever opened.
 * Retry delays are set to 0 so the fallback path runs instantly.
 */
@ExtendWith(MockitoExtension.class)
class ExternalScaleClientTest {

    private static final String BASE_URL = "http://sansaweigh.test/api/v1/scales";

    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache cache;

    // builds the client with a RestClient bound to the given mock server
    private ExternalScaleClient clientFor(MockServerHolder holder, long[] retryDelays) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        holder.server = MockRestServiceServer.bindTo(builder).build();
        return new ExternalScaleClient(builder.build(), cacheManager, retryDelays);
    }

    private static final class MockServerHolder {
        MockRestServiceServer server;
    }

    @Test
    void returnsScaleFromApiAndStoresItInTheCacheOnSuccess() {
        MockServerHolder holder = new MockServerHolder();
        ExternalScaleClient client = clientFor(holder, new long[]{0});
        when(cacheManager.getCache("scaleSpecs")).thenReturn(cache);

        holder.server.expect(requestTo(BASE_URL + "/7"))
                .andRespond(withSuccess("""
                        {"id":"7","name":"Bascula","brand":"Acme",
                         "maxCapacity":100.0,"precision":0.1,"maxCalibrationOffset":0.05}
                        """, MediaType.APPLICATION_JSON));

        Scale result = client.getScaleSpecifications("7");

        assertEquals("7", result.getId());
        assertEquals("Bascula", result.getName());
        assertEquals(100.0, result.getMaxCapacity());
        holder.server.verify();
        verify(cache).put("7", result); // network result is cached as a fallback
    }

    @Test
    void fallsBackToCachedValueWhenTheApiKeepsFailing() {
        MockServerHolder holder = new MockServerHolder();
        // one retry => two attempts; both fail, then we serve the cache
        ExternalScaleClient client = clientFor(holder, new long[]{0});
        Scale cached = new Scale("7", "Cached", "Acme", 100.0, 0.1, 0.05);
        when(cacheManager.getCache("scaleSpecs")).thenReturn(cache);
        when(cache.get("7", Scale.class)).thenReturn(cached);

        holder.server.expect(ExpectedCount.times(2), requestTo(BASE_URL + "/7"))
                .andRespond(withServerError());

        Scale result = client.getScaleSpecifications("7");

        assertEquals(cached, result);
        holder.server.verify();
        verify(cache, never()).put(any(), any()); // nothing new to cache on failure
    }

    @Test
    void returnsSentinelScaleWhenApiFailsAndNothingIsCached() {
        MockServerHolder holder = new MockServerHolder();
        ExternalScaleClient client = clientFor(holder, new long[]{0});
        when(cacheManager.getCache("scaleSpecs")).thenReturn(cache);
        when(cache.get("7", Scale.class)).thenReturn(null);

        holder.server.expect(ExpectedCount.times(2), requestTo(BASE_URL + "/7"))
                .andRespond(withServerError());

        Scale result = client.getScaleSpecifications("7");

        assertEquals("-1", result.getId()); // sentinel: id -1, blank/zero fields
        holder.server.verify();
    }
}
