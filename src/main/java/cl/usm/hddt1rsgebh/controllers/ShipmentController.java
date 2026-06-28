package cl.usm.hddt1rsgebh.controllers;

import cl.usm.hddt1rsgebh.entities.Shipment;
import cl.usm.hddt1rsgebh.entities.ShipmentRequest;
import cl.usm.hddt1rsgebh.entities.StatusUpdateRequest;
import cl.usm.hddt1rsgebh.entities.Status;
import cl.usm.hddt1rsgebh.exceptions.IllegalWeighingStateException;
import cl.usm.hddt1rsgebh.services.ShipmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
public class ShipmentController {
    private ShipmentService shipmentService;

    @Autowired
    public void setShipmentService(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @GetMapping("/shipments/{date}") // ISO 8601 format
    public ResponseEntity<List<Shipment>> getById(@PathVariable String date){
        LocalDate dateObj = LocalDate.parse(date);

        List<Shipment> response = this.shipmentService.getShipmentsByDate(dateObj);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/shipments/{id}")
    public ResponseEntity<?> updateShipment(@PathVariable String id, @RequestBody StatusUpdateRequest request){

        Shipment shipment = this.shipmentService.getShipmentById(id);
        if (shipment == null){
            return ResponseEntity.notFound().build();
        }

        Status target;
        try {
            target = Status.valueOf(request.getStatus().trim());
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().body("Unknown status: " + request.getStatus());
        }

        try {
            // rules enforced by the service before persisting:
            // rule 1: the flow must be registered -> weighed -> accepted/rejected -> sent
            // rule 2: cannot weigh heavy packages between 20:00 to 06:00
            // rule 3: if scale id is prime, cannot weigh heavy packages on odd days
            return ResponseEntity.ok(this.shipmentService.updateShipment(shipment, target));
        } catch (IllegalWeighingStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/shipments/")
    public ResponseEntity<Boolean> createShipment(@RequestBody ShipmentRequest shipment){
        return ResponseEntity.ok(this.shipmentService.createShipment(shipment));
    }
}
