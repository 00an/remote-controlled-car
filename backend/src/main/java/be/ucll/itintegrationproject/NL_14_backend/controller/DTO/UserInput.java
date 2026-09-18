package be.ucll.itintegrationproject.NL_14_backend.controller.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserInput(
    @NotBlank String username,
    @NotBlank String password,
    @NotBlank String firstName,
    @NotBlank String lastName,
    @Email String email,
    boolean privacyConsent) {}
