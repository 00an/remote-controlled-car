package be.ucll.itintegrationproject.NL_14_backend.exception;

public class RideNotFoundException extends RuntimeException {
  public RideNotFoundException(Long id) {
    super("Ride not found: " + id);
  }
}
