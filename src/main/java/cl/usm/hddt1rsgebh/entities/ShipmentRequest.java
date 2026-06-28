package cl.usm.hddt1rsgebh.entities;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.annotation.Id;

/*
* entity used for data posted by the user
*/

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentRequest {
    @Id
    private String id;

    @NotBlank(message = "Scale ID is required")
    private String scaleId;

    @NotBlank(message = "Package ID is required")
    private String packageId;

    @NotNull(message = "Weight in kilograms is required")
    private Double weight; // in kg
}
