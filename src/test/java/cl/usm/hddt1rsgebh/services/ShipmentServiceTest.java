package cl.usm.hddt1rsgebh.services;

import cl.usm.hddt1rsgebh.entities.Shipment;
import cl.usm.hddt1rsgebh.entities.ShipmentRequest;
import cl.usm.hddt1rsgebh.entities.Status;
import cl.usm.hddt1rsgebh.entities.WeightCategory;
import cl.usm.hddt1rsgebh.exceptions.IllegalWeighingStateException;
import cl.usm.hddt1rsgebh.integration.ExternalScaleClient;
import cl.usm.hddt1rsgebh.repositories.ShipmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");

    @Mock
    private ExternalScaleClient client;
    @Mock
    private ShipmentRepository repository;
    @InjectMocks
    private ShipmentService service;

    // --- unit conversion -----------------------------------------------------

    @Test
    void kgToSaAndSaToKgAreInverseUsingTheSansaRatio() {
        // 1 Sa = 1.337 Kg
        assertEquals(1.337, service.kgToSa(1.0), 1e-9);
        assertEquals(1.0, service.saToKg(1.337), 1e-9);
        // round trip
        assertEquals(42.0, service.saToKg(service.kgToSa(42.0)), 1e-9);
    }

    // --- isPrime -------------------------------------------------------------

    static Stream<Arguments> primeCases() {
        return Stream.of(
                arguments(null, false),
                arguments(-7.0, false),
                arguments(0.0, false),
                arguments(1.0, false),
                arguments(2.0, true),
                arguments(3.0, true),
                arguments(4.0, false),
                arguments(17.0, true),
                arguments(25.0, false),
                arguments(7919.0, true),   // prime
                arguments(7917.0, false),  // composite
                arguments(4.5, false),     // non-integer is never prime
                arguments(Double.POSITIVE_INFINITY, false)
        );
    }

    @ParameterizedTest
    @MethodSource("primeCases")
    void isPrimeClassifiesNumbers(Double input, boolean expected) {
        assertEquals(expected, service.isPrime(input));
    }

    // --- createShipment ------------------------------------------------------

    @Test
    void createShipmentPersistsConvertedWeightAndInitialStatus() {
        ShipmentRequest request = new ShipmentRequest(null, "5", "PKG-1", 20.0);

        boolean result = service.createShipment(request);

        assertTrue(result);
        ArgumentCaptor<Shipment> captor = ArgumentCaptor.forClass(Shipment.class);
        verify(repository).save(captor.capture());
        Shipment saved = captor.getValue();

        assertNull(saved.getId(), "id is left for Mongo to generate");
        assertEquals("5", saved.getScaleId());
        assertEquals("PKG-1", saved.getPackageId());
        assertEquals(20.0 * 1.337, saved.getWeight(), 1e-9, "weight is stored in Sa");
        assertEquals(Status.INGRESADO, saved.getStatus());
        assertNotNull(saved.getUpdatedAt());
    }

    @ParameterizedTest
    @CsvSource({
            "7,LIVIANO",   // 9.359 Sa  (<= 10)
            "8,MEDIANO",   // 10.696 Sa (> 10)
            "37,MEDIANO",  // 49.469 Sa (<= 50)
            "38,PESADO",   // 50.806 Sa (> 50)
            "100,PESADO"
    })
    void createShipmentDerivesWeightCategoryFromSa(double kg, WeightCategory expected) {
        service.createShipment(new ShipmentRequest(null, "1", "PKG", kg));

        ArgumentCaptor<Shipment> captor = ArgumentCaptor.forClass(Shipment.class);
        verify(repository).save(captor.capture());
        assertEquals(expected, captor.getValue().getWeightCategory());
    }

    @Test
    void createShipmentReturnsFalseWhenPersistenceFails() {
        doThrow(new DataAccessResourceFailureException("mongo down"))
                .when(repository).save(any(Shipment.class));

        assertFalse(service.createShipment(new ShipmentRequest(null, "1", "PKG", 1.0)));
    }

    // --- updateShipment: status flow (rule 1) --------------------------------

    static Stream<Arguments> allowedTransitions() {
        // the flow is walkable in both directions to correct mistakes
        return Stream.of(
                arguments(Status.INGRESADO, Status.PESADO),
                arguments(Status.PESADO, Status.INGRESADO),
                arguments(Status.PESADO, Status.APROBADO),
                arguments(Status.APROBADO, Status.PESADO),
                arguments(Status.PESADO, Status.RECHAZADO),
                arguments(Status.RECHAZADO, Status.PESADO),
                arguments(Status.APROBADO, Status.DESPACHADO),
                arguments(Status.DESPACHADO, Status.APROBADO),
                arguments(Status.RECHAZADO, Status.DESPACHADO),
                arguments(Status.DESPACHADO, Status.RECHAZADO)
        );
    }

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void updateShipmentAcceptsValidTransitions(Status from, Status to) {
        // LIVIANO keeps us clear of the heavy-weighing time/prime rules
        Shipment shipment = shipment("1", WeightCategory.LIVIANO, from);

        assertTrue(service.updateShipment(shipment, to));
        assertEquals(to, shipment.getStatus());
        assertNotNull(shipment.getUpdatedAt());
        verify(repository).save(shipment);
    }

    static Stream<Arguments> illegalTransitions() {
        return Stream.of(
                arguments(Status.INGRESADO, Status.INGRESADO), // no-op
                arguments(Status.INGRESADO, Status.APROBADO),  // skips PESADO
                arguments(Status.INGRESADO, Status.DESPACHADO),
                arguments(Status.PESADO, Status.DESPACHADO),
                arguments(Status.APROBADO, Status.RECHAZADO)   // siblings, not connected
        );
    }

    @ParameterizedTest
    @MethodSource("illegalTransitions")
    void updateShipmentRejectsInvalidTransitions(Status from, Status to) {
        Shipment shipment = shipment("1", WeightCategory.LIVIANO, from);

        assertThrows(IllegalWeighingStateException.class,
                () -> service.updateShipment(shipment, to));
        verify(repository, never()).save(any());
    }

    @Test
    void updateShipmentReturnsFalseWhenPersistenceFails() {
        Shipment shipment = shipment("1", WeightCategory.LIVIANO, Status.INGRESADO);
        doThrow(new DataAccessResourceFailureException("mongo down"))
                .when(repository).save(any(Shipment.class));

        assertFalse(service.updateShipment(shipment, Status.PESADO));
    }

    // --- updateShipment: heavy-weighing rules (rules 2 & 3) -------------------

    @ParameterizedTest
    @CsvSource({"20", "23", "0", "5"}) // 20:00 inclusive .. 06:00 exclusive
    void cannotWeighHeavyPackagesAtNight(int hour) {
        // even day + non-prime scale so only the time rule can fire
        ZonedDateTime night = ZonedDateTime.of(2026, 6, 30, hour, 30, 0, 0, ZONE);

        IllegalWeighingStateException ex = assertThrows(IllegalWeighingStateException.class,
                weighHeavy(night, "4"));
        assertTrue(ex.getMessage().contains("20:00 and 06:00"));
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"6", "12", "19"}) // 06:00 inclusive .. 20:00 exclusive
    void canWeighHeavyPackagesDuringTheDay(int hour) {
        ZonedDateTime day = ZonedDateTime.of(2026, 6, 30, hour, 0, 0, 0, ZONE);

        assertDoesNotThrow(weighHeavy(day, "4"));
    }

    @Test
    void primeScaleCannotWeighHeavyPackagesOnOddDays() {
        // 2026-06-15 is odd, 10:00 is within the allowed window, scale 7 is prime
        ZonedDateTime oddDay = ZonedDateTime.of(2026, 6, 15, 10, 0, 0, 0, ZONE);

        IllegalWeighingStateException ex = assertThrows(IllegalWeighingStateException.class,
                weighHeavy(oddDay, "7"));
        assertTrue(ex.getMessage().contains("odd days"));
    }

    @Test
    void primeScaleCanWeighHeavyPackagesOnEvenDays() {
        ZonedDateTime evenDay = ZonedDateTime.of(2026, 6, 30, 10, 0, 0, 0, ZONE);
        assertDoesNotThrow(weighHeavy(evenDay, "7"));
    }

    @Test
    void nonPrimeScaleCanWeighHeavyPackagesOnOddDays() {
        ZonedDateTime oddDay = ZonedDateTime.of(2026, 6, 15, 10, 0, 0, 0, ZONE);
        assertDoesNotThrow(weighHeavy(oddDay, "4"));
    }

    // --- queries -------------------------------------------------------------

    @Test
    void getShipmentsByDateQueriesTheFullDayInSantiagoTime() {
        LocalDate date = LocalDate.of(2026, 6, 29);
        Instant expectedStart = date.atStartOfDay(ZONE).toInstant();
        Instant expectedEnd = date.plusDays(1).atStartOfDay(ZONE).toInstant();
        List<Shipment> expected = List.of(shipment("1", WeightCategory.LIVIANO, Status.INGRESADO));
        when(repository.findByUpdatedAtBetween(expectedStart, expectedEnd)).thenReturn(expected);

        assertSame(expected, service.getShipmentsByDate(date));
    }

    @Test
    void getShipmentByIdReturnsNullWhenAbsent() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertNull(service.getShipmentById("missing"));
    }

    @Test
    void getShipmentByIdReturnsTheDocumentWhenPresent() {
        Shipment shipment = shipment("1", WeightCategory.LIVIANO, Status.INGRESADO);
        when(repository.findById("abc")).thenReturn(Optional.of(shipment));
        assertSame(shipment, service.getShipmentById("abc"));
    }

    // --- helpers -------------------------------------------------------------

    // weighs a heavy package (INGRESADO -> PESADO) with "now" pinned to fixedNow
    private Executable weighHeavy(ZonedDateTime fixedNow, String scaleId) {
        return () -> {
            Shipment shipment = shipment(scaleId, WeightCategory.PESADO, Status.INGRESADO);
            try (MockedStatic<ZonedDateTime> mocked = mockStatic(ZonedDateTime.class)) {
                mocked.when(() -> ZonedDateTime.now(any(ZoneId.class))).thenReturn(fixedNow);
                service.updateShipment(shipment, Status.PESADO);
            }
        };
    }

    private static Shipment shipment(String scaleId, WeightCategory category, Status status) {
        return new Shipment("id-1", scaleId, "PKG", 1.0, category, status, Instant.now());
    }
}
