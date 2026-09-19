package be.ucll.itintegrationproject.NL_14_backend.controller.DTO;

public record AuthenticationResponse(
    String message, String token, String username, String fullname) {}
