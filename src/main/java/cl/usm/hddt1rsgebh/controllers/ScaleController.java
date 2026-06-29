package cl.usm.hddt1rsgebh.controllers;

import cl.usm.hddt1rsgebh.dto.Scale;
import cl.usm.hddt1rsgebh.integration.ExternalScaleClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ScaleController {
    private ExternalScaleClient externalScaleClient;

    @Autowired
    public ScaleController(ExternalScaleClient externalScaleClient) {
        this.externalScaleClient = externalScaleClient;
    }

    @GetMapping("/scales/{id}")
    public ResponseEntity<Scale> getScaleById(@PathVariable String id){
        Scale scale = this.externalScaleClient.getScaleSpecifications(id);
        if (scale == null){
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(scale);
    }
}
