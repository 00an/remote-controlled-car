package be.ucll.itintegrationproject.NL_14_backend.service;

import be.ucll.itintegrationproject.NL_14_backend.controller.DTO.ControllerInput;
import be.ucll.itintegrationproject.NL_14_backend.model.ControllerBroadcastDoc;
import be.ucll.itintegrationproject.NL_14_backend.repository.ControllerBroadcastMongoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Persists every payload broadcast to the ESP32 (raw axes/buttons/hats) into the {@code
 * controller_broadcasts} collection. Unlike {@link ControlInputLoggingService}, this layer applies
 * no quantize / dedup / hysteresis — the goal is to capture exactly what the ESP received so the
 * data&ai dashboard can quantify what the filtering layer drops.
 */
@Service
public class ControllerBroadcastLoggingService {

  private static final Logger log =
      LoggerFactory.getLogger(ControllerBroadcastLoggingService.class);

  private final ControllerBroadcastMongoRepository repository;
  private final ObjectMapper objectMapper;
  private final ControlInputLoggingService sessionTracker;

  @Value("${controller.broadcast-logging.enabled:true}")
  private boolean enabled;

  public ControllerBroadcastLoggingService(
      ControllerBroadcastMongoRepository repository,
      ObjectMapper objectMapper,
      ControlInputLoggingService sessionTracker) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.sessionTracker = sessionTracker;
  }

  @Async
  public void log(ControllerInput input, String source) {
    if (!enabled || input == null) return;
    try {
      persist(input, source);
    } catch (Exception e) {
      log.warn("Failed to persist controller broadcast", e);
    }
  }

  @Async
  public void logRaw(String payload, String source) {
    if (!enabled || payload == null || payload.isBlank()) return;
    try {
      ControllerInput input = objectMapper.readValue(payload, ControllerInput.class);
      persist(input, source);
    } catch (JsonProcessingException e) {
      log.debug("Skipping unparseable broadcast payload for logging");
    } catch (Exception e) {
      log.warn("Failed to persist controller broadcast", e);
    }
  }

  private void persist(ControllerInput input, String source) {
    var sessionId = sessionTracker.getCurrentSessionId();
    if (sessionId == null) return;

    ControllerBroadcastDoc doc = new ControllerBroadcastDoc();
    doc.setRecordedAt(Instant.now());
    doc.setClientTimestamp(input.timestamp());
    doc.setAxes(input.axes());
    doc.setButtons(input.buttons());
    doc.setHats(input.hats());
    doc.setSource(source == null ? "unknown" : source);
    doc.setSessionId(sessionId);
    repository.save(doc);
  }
}
