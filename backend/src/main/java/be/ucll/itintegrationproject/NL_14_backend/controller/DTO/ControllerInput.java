package be.ucll.itintegrationproject.NL_14_backend.controller.DTO;

import java.util.List;

public record ControllerInput(
    List<Double> axes,
    List<Integer> buttons,
    List<List<Integer>> hats,
    // Boxed so a missing `timestamp` field in the payload stays null instead
    // of silently defaulting to 0 — analytics relies on null to mean
    // "client didn't send one" rather than "client says 1970".
    Long timestamp) {}
