package cl.usm.hddt1rsgebh.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/*
* entity used to save data to the repository
* and data which is returned to the user upon request
*/

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "RegistroPesaje")
public class Shipment {
    @Id
    private String id;

    private String scaleId;
    private String packageId;
    private Double weight; // in Sa
    private WeightCategory weightCategory;
    private Status status;
    private Instant updatedAt;
}
