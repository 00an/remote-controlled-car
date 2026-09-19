package be.ucll.itintegrationproject.NL_14_backend.controller;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/status")
public class HealthController {

  @GetMapping
  public ResponseEntity<Map<String, Object>> getStatus() {
    return ResponseEntity.ok(Map.of("status", "UP", "timestamp", Instant.now().toString()));
  }
}
