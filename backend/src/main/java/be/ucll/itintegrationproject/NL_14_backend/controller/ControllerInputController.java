package be.ucll.itintegrationproject.NL_14_backend.controller;

import be.ucll.itintegrationproject.NL_14_backend.controller.DTO.ControllerInput;
import be.ucll.itintegrationproject.NL_14_backend.service.ControlInputLoggingService;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/controller")
public class ControllerInputController {

  private volatile Process scriptProcess = null;
  private final ControllerWebSocketHub webSocketHub;
  private final ControlInputLoggingService loggingService;

  @Value("${controller.script.path:../steering-wheel-input/main.py}")
  private String scriptPath;

  public ControllerInputController(
      ControllerWebSocketHub webSocketHub, ControlInputLoggingService loggingService) {
    this.webSocketHub = webSocketHub;
    this.loggingService = loggingService;
  }

  @PostMapping
  public ResponseEntity<Void> receive(@RequestBody ControllerInput input) {
    webSocketHub.broadcast(input);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/latest")
  public ResponseEntity<String> latest() {
    String payload = webSocketHub.getLatestPayload();
    if (payload == null) {
      payload = "{\"axes\":[0.0,1.0,1.0],\"buttons\":[],\"hats\":[],\"timestamp\":0}";
    }
    return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, "application/json").body(payload);
  }

  @PostMapping("/script/start")
  public ResponseEntity<Map<String, Object>> startScript() {
    if (scriptProcess != null && scriptProcess.isAlive()) {
      return ResponseEntity.ok(Map.of("status", "already_running", "pid", scriptProcess.pid()));
    }
    try {
      File script = new File(scriptPath).getCanonicalFile();
      if (!script.exists()) {
        return ResponseEntity.badRequest()
            .body(
                Map.of(
                    "status",
                    "error",
                    "message",
                    "Script not found: " + script.getAbsolutePath()));
      }
      ProcessBuilder pb =
          new ProcessBuilder("python", script.getAbsolutePath())
              .redirectErrorStream(true)
              .inheritIO();
      scriptProcess = pb.start();
      loggingService.startSession();
      return ResponseEntity.ok(
          Map.of(
              "status", "started",
              "pid", scriptProcess.pid(),
              "path", script.getAbsolutePath()));
    } catch (IOException e) {
      return ResponseEntity.internalServerError()
          .body(Map.of("status", "error", "message", e.getMessage()));
    }
  }

  @PostMapping("/script/stop")
  public ResponseEntity<Map<String, Object>> stopScript() {
    if (scriptProcess == null || !scriptProcess.isAlive()) {
      return ResponseEntity.ok(Map.of("status", "not_running"));
    }
    scriptProcess.destroy();
    try {
      scriptProcess.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
    if (scriptProcess.isAlive()) {
      scriptProcess.destroyForcibly();
    }
    scriptProcess = null;
    loggingService.endSession();
    return ResponseEntity.ok(Map.of("status", "stopped"));
  }

  @GetMapping("/script/status")
  public ResponseEntity<Map<String, Object>> scriptStatus() {
    boolean running = scriptProcess != null && scriptProcess.isAlive();
    return ResponseEntity.ok(Map.of("running", running, "pid", running ? scriptProcess.pid() : -1));
  }

  @PostMapping("/session/start")
  public ResponseEntity<Map<String, Object>> startSession() {
    try {
      var existing = loggingService.getCurrentSessionId();
      if (existing != null) {
        return ResponseEntity.ok(
            Map.of("status", "already_active", "sessionId", existing.toString()));
      }
      var sessionId = loggingService.startSession();
      return ResponseEntity.ok(Map.of("status", "started", "sessionId", sessionId.toString()));
    } catch (Exception e) {
      return ResponseEntity.internalServerError()
          .body(
              Map.of(
                  "status",
                  "error",
                  "type",
                  e.getClass().getSimpleName(),
                  "message",
                  e.getMessage() == null ? "" : e.getMessage()));
    }
  }

  @PostMapping("/session/stop")
  public ResponseEntity<Map<String, Object>> stopSession() {
    try {
      var existing = loggingService.getCurrentSessionId();
      if (existing == null) {
        return ResponseEntity.ok(Map.of("status", "not_active"));
      }
      loggingService.endSession();
      return ResponseEntity.ok(Map.of("status", "stopped", "sessionId", existing.toString()));
    } catch (Exception e) {
      return ResponseEntity.internalServerError()
          .body(
              Map.of(
                  "status",
                  "error",
                  "type",
                  e.getClass().getSimpleName(),
                  "message",
                  e.getMessage() == null ? "" : e.getMessage()));
    }
  }
}
