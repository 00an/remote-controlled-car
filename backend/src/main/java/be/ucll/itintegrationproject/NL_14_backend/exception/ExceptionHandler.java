package be.ucll.itintegrationproject.NL_14_backend.exception;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Converts all exceptions into clean JSON responses with the correct HTTP status
@RestControllerAdvice
class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(UserAlreadyExistsException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, String> handleUserAlreadyExists(UserAlreadyExistsException ex) {
    return Map.of("error", ex.getMessage());
  }

  @ExceptionHandler(UserNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Map<String, String> handleUserNotFound(UserNotFoundException ex) {
    return Map.of("error", ex.getMessage());
  }

  @ExceptionHandler(RideNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Map<String, String> handleRideNotFound(RideNotFoundException ex) {
    return Map.of("error", ex.getMessage());
  }

  @ExceptionHandler(PrivacyConsentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> handlePrivacyConsent(PrivacyConsentException ex) {
    return Map.of("error", ex.getMessage());
  }

  @ExceptionHandler(BadCredentialsException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public Map<String, String> handleBadCredentials() {
    return Map.of("error", "invalid username or password");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, List<String>> handleValidation(MethodArgumentNotValidException ex) {
    List<String> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .toList();
    return Map.of("errors", errors);
  }

  // Catch-all for anything not handled above, returns 500 + logs the stack trace
  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Map<String, String> handleGeneric(Exception ex) {
    log.error("Unhandled exception bubbled to global handler", ex);
    return Map.of(
        "error",
        "an unexpected error occurred",
        "type",
        ex.getClass().getSimpleName(),
        "message",
        ex.getMessage() == null ? "" : ex.getMessage());
  }
}
