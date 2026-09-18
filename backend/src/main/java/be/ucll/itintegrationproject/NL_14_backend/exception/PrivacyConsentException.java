package be.ucll.itintegrationproject.NL_14_backend.exception;

public class PrivacyConsentException extends RuntimeException {
  public PrivacyConsentException() {
    super("You must consent to the privacy policy to create an account");
  }
}
