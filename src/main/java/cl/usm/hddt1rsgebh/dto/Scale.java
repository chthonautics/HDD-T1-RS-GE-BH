package cl.usm.hddt1rsgebh.dto;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Scale {
    private String id;
    private String name;
    private String brand;
    private Double maxCapacity;
    private Double precision;
    private Double maxCalibrationOffset;
}
