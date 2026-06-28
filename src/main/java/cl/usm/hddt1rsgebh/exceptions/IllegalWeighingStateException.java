package cl.usm.hddt1rsgebh.exceptions;

/*
 * thrown when a shipment status transition violates a domain rule
 * (invalid flow, or weighing a heavy package at a forbidden time)
 */
public class IllegalWeighingStateException extends RuntimeException {
    public IllegalWeighingStateException(String message) {
        super(message);
    }
}
