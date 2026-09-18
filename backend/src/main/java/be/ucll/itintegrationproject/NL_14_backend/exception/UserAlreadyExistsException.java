package be.ucll.itintegrationproject.NL_14_backend.exception;

public class UserAlreadyExistsException extends RuntimeException {
  public UserAlreadyExistsException(String username) {
    super("Username already in use: " + username);
  }
}
