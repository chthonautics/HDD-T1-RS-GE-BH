package cl.usm.hddt1rsgebh.controllers;

import cl.usm.hddt1rsgebh.dto.Scale;
import cl.usm.hddt1rsgebh.integration.ExternalScaleClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScaleController.class)
class ScaleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExternalScaleClient externalScaleClient;

    @Test
    void getScaleByIdReturnsTheScaleFromTheClient() throws Exception {
        when(externalScaleClient.getScaleSpecifications("7"))
                .thenReturn(new Scale("7", "Bascula", "Acme", 100.0, 0.1, 0.05));

        mockMvc.perform(get("/scales/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("7"))
                .andExpect(jsonPath("$.name").value("Bascula"))
                .andExpect(jsonPath("$.max_capacity").value(100.0)); // SNAKE_CASE naming
    }

    @Test
    void getScaleByIdReturns404WhenClientHasNoScale() throws Exception {
        when(externalScaleClient.getScaleSpecifications("missing")).thenReturn(null);

        mockMvc.perform(get("/scales/missing"))
                .andExpect(status().isNotFound());
    }
}
