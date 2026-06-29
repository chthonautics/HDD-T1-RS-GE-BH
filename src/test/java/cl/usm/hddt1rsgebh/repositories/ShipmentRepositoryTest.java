package cl.usm.hddt1rsgebh.repositories;

import cl.usm.hddt1rsgebh.entities.Shipment;
import cl.usm.hddt1rsgebh.entities.Status;
import cl.usm.hddt1rsgebh.entities.WeightCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Exercises the custom findByUpdatedAtBetween query against a real MongoDB
 * (localhost:27017). Uses a dedicated test database so the application's
 * RegistroPesaje collection is never read or written; inserted documents are
 * also removed afterwards.
 */
@DataMongoTest
@TestPropertySource(properties = "spring.mongodb.uri=mongodb://localhost:27017/hddt1rsgebh_test")
class ShipmentRepositoryTest {

    @Autowired
    private ShipmentRepository repository;

    private List<String> insertedIds = List.of();

    @AfterEach
    void cleanUp() {
        insertedIds.forEach(repository::deleteById);
    }

    @Test
    void findByUpdatedAtBetweenReturnsOnlyDocumentsInsideTheRange() {
        Instant start = Instant.parse("2026-06-29T00:00:00Z");
        Instant end = Instant.parse("2026-06-30T00:00:00Z");

        Shipment before = save("before", Instant.parse("2026-06-28T23:00:00Z"));
        Shipment inside = save("inside", Instant.parse("2026-06-29T12:00:00Z"));
        Shipment after = save("after", Instant.parse("2026-06-30T01:00:00Z"));
        insertedIds = List.of(before.getId(), inside.getId(), after.getId());

        List<Shipment> result = repository.findByUpdatedAtBetween(start, end);

        assertThat(result)
                .extracting(Shipment::getPackageId)
                .contains("inside")
                .doesNotContain("before", "after");
    }

    private Shipment save(String packageId, Instant updatedAt) {
        return repository.save(new Shipment(
                null, "1", packageId, 5.0, WeightCategory.LIVIANO, Status.INGRESADO, updatedAt));
    }
}
