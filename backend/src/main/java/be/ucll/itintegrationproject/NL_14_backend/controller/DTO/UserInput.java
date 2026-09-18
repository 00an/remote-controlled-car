package be.ucll.itintegrationproject.NL_14_backend.controller.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserInput(
    @NotBlank String username,
    @NotBlank @Size(min = 8, message = "password must be at least 8 characters") String password,
    @NotBlank String firstName,
    @NotBlank String lastName,
    @Email String email,
    boolean privacyConsent) {}
