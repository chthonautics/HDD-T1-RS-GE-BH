package cl.usm.hddt1rsgebh.repositories;

import cl.usm.hddt1rsgebh.entities.Shipment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ShipmentRepository extends MongoRepository<Shipment, String> {
    List<Shipment> findByUpdatedAtBetween(Instant start, Instant end);
}
