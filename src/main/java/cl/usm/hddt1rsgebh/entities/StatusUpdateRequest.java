package cl.usm.hddt1rsgebh.entities;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/*
 * body of PATCH /shipments/{id}, e.g. {"status": "PESADO"}
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class StatusUpdateRequest {
    @NotBlank(message = "Status is required")
    private String status;
}
