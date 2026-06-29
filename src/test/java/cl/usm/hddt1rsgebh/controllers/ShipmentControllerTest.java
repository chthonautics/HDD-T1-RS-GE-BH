package cl.usm.hddt1rsgebh.controllers;

import cl.usm.hddt1rsgebh.entities.Shipment;
import cl.usm.hddt1rsgebh.entities.Status;
import cl.usm.hddt1rsgebh.entities.WeightCategory;
import cl.usm.hddt1rsgebh.exceptions.IllegalWeighingStateException;
import cl.usm.hddt1rsgebh.services.ShipmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ShipmentController.class)
class ShipmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShipmentService shipmentService;

    @Test
    void postShipmentsDelegatesToServiceAndReturnsItsResult() throws Exception {
        when(shipmentService.createShipment(any())).thenReturn(true);

        mockMvc.perform(post("/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scale_id\":\"5\",\"package_id\":\"PKG-1\",\"weight\":20.0}"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(shipmentService).createShipment(any());
    }

    @Test
    void getShipmentsByDateReturnsTheServiceList() throws Exception {
        Shipment shipment = new Shipment("id-1", "5", "PKG-1", 26.74,
                WeightCategory.MEDIANO, Status.INGRESADO, Instant.parse("2026-06-29T12:00:00Z"));
        when(shipmentService.getShipmentsByDate(LocalDate.of(2026, 6, 29)))
                .thenReturn(List.of(shipment));

        mockMvc.perform(get("/shipments/2026-06-29"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("id-1"))
                .andExpect(jsonPath("$[0].scale_id").value("5")) // SNAKE_CASE naming
                .andExpect(jsonPath("$[0].weight_category").value("MEDIANO"));
    }

    @Test
    void patchReturns404WhenShipmentMissing() throws Exception {
        when(shipmentService.getShipmentById("nope")).thenReturn(null);

        mockMvc.perform(patch("/shipments/nope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PESADO\"}"))
                .andExpect(status().isNotFound());

        verify(shipmentService, never()).updateShipment(any(), any());
    }

    @Test
    void patchReturns400ForUnknownStatusName() throws Exception {
        when(shipmentService.getShipmentById("id-1"))
                .thenReturn(new Shipment());

        mockMvc.perform(patch("/shipments/id-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BOGUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Unknown status: BOGUS"));

        verify(shipmentService, never()).updateShipment(any(), any());
    }

    @Test
    void patchTransitionsShipmentAndReturnsServiceResult() throws Exception {
        Shipment shipment = new Shipment();
        when(shipmentService.getShipmentById("id-1")).thenReturn(shipment);
        when(shipmentService.updateShipment(shipment, Status.PESADO)).thenReturn(true);

        mockMvc.perform(patch("/shipments/id-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PESADO\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void patchReturns400WithMessageWhenWeighingRuleViolated() throws Exception {
        when(shipmentService.getShipmentById("id-1")).thenReturn(new Shipment());
        when(shipmentService.updateShipment(any(), eq(Status.PESADO)))
                .thenThrow(new IllegalWeighingStateException("Cannot weigh heavy packages between 20:00 and 06:00"));

        mockMvc.perform(patch("/shipments/id-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PESADO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Cannot weigh heavy packages between 20:00 and 06:00"));
    }
}
