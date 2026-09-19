package be.ucll.itintegrationproject.NL_14_backend.exception;

public class UserNotFoundException extends RuntimeException {
  public UserNotFoundException(String username) {
    super("User not found: " + username);
  }
}
