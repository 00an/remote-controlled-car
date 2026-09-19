package be.ucll.itintegrationproject.NL_14_backend.service;

import be.ucll.itintegrationproject.NL_14_backend.controller.DTO.ControllerInput;
import be.ucll.itintegrationproject.NL_14_backend.model.ControlInputLogDoc;
import be.ucll.itintegrationproject.NL_14_backend.repository.ControlInputLogMongoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ControlInputLoggingService {

  private static final Logger log = LoggerFactory.getLogger(ControlInputLoggingService.class);

  private final ControlInputLogMongoRepository repository;
  private final ObjectMapper objectMapper;

  @Value("${controller.logging.min-interval-ms:200}")
  private long minIntervalMs;

  @Value("${controller.logging.quantize-step:0.01}")
  private double quantizeStep;

  // Hysteresis on the pedal deadzone: the value has to climb above
  // `wake-threshold` to leave the rest state, and drop below
  // `sleep-threshold` to return to it. Prevents flicker at the boundary.
  @Value("${controller.logging.deadzone:0.05}")
  private double deadzone; // kept for back-compat; treated as sleep-threshold

  @Value("${controller.logging.wake-threshold:0.08}")
  private double wakeThreshold;

  @Value("${controller.logging.full-threshold:0.96}")
  private double fullThreshold;

  private enum PedalState {
    REST,
    ACTIVE
  }

  private PedalState throttleState = PedalState.REST;
  private PedalState brakeState = PedalState.REST;

  private volatile UUID currentSessionId;

  private final Object dedupLock = new Object();
  private double lastSteering;
  private double lastThrottle;
  private double lastBrake;
  private long lastWriteEpochMs;
  private boolean hasLastSample;

  public ControlInputLoggingService(
      ControlInputLogMongoRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  public UUID startSession() {
    synchronized (dedupLock) {
      hasLastSample = false;
      throttleState = PedalState.REST;
      brakeState = PedalState.REST;
    }
    currentSessionId = UUID.randomUUID();
    return currentSessionId;
  }

  public void endSession() {
    currentSessionId = null;
    synchronized (dedupLock) {
      hasLastSample = false;
      throttleState = PedalState.REST;
      brakeState = PedalState.REST;
    }
  }

  public UUID getCurrentSessionId() {
    return currentSessionId;
  }

  @Async
  public void log(ControllerInput input, String source) {
    if (input == null) return;
    try {
      persist(input, source);
    } catch (Exception e) {
      log.warn("Failed to persist control input log", e);
    }
  }

  @Async
  public void logRaw(String payload, String source) {
    if (payload == null || payload.isBlank()) return;
    try {
      ControllerInput input = objectMapper.readValue(payload, ControllerInput.class);
      persist(input, source);
    } catch (JsonProcessingException e) {
      log.debug("Skipping unparseable controller payload for logging");
    } catch (Exception e) {
      log.warn("Failed to persist control input log", e);
    }
  }

  private void persist(ControllerInput input, String source) {
    if (currentSessionId == null) return;

    List<Double> axes = input.axes();
    // axes[0] = steering raw, range -1..+1 (left..right) — store as-is, quantized
    // axes[1] = gas raw, range -1..+1 where +1=rest, -1=floored
    // axes[2] = brake raw, same convention
    double steering = quantize(clampToUnit(axisOrZero(axes, 0)));
    double throttle = quantize(normalizePedalWithHysteresis(axisOrZero(axes, 1), true));
    double brake = quantize(normalizePedalWithHysteresis(axisOrZero(axes, 2), false));

    if (!shouldPersist(steering, throttle, brake)) return;

    ControlInputLogDoc entry = new ControlInputLogDoc();
    entry.setRecordedAt(Instant.now());
    entry.setClientTimestamp(input.timestamp());
    entry.setSteering(steering);
    entry.setThrottle(throttle);
    entry.setBrake(brake);
    entry.setButtons(input.buttons());
    entry.setHats(input.hats());
    entry.setSource(source == null ? "unknown" : source);
    entry.setSessionId(currentSessionId);
    repository.save(entry);
  }

  private boolean shouldPersist(double steering, double throttle, double brake) {
    long now = System.currentTimeMillis();
    synchronized (dedupLock) {
      if (hasLastSample) {
        boolean unchanged =
            steering == lastSteering && throttle == lastThrottle && brake == lastBrake;
        if (unchanged) return false;
        if (now - lastWriteEpochMs < minIntervalMs) return false;
      }
      lastSteering = steering;
      lastThrottle = throttle;
      lastBrake = brake;
      lastWriteEpochMs = now;
      hasLastSample = true;
      return true;
    }
  }

  private static double axisOrZero(List<Double> axes, int index) {
    if (axes == null || index >= axes.size() || axes.get(index) == null) return 0.0;
    return axes.get(index);
  }

  private double quantize(double value) {
    if (quantizeStep <= 0) return value;
    return Math.round(value / quantizeStep) * quantizeStep;
  }

  /**
   * Map a wheel-style pedal axis (+1=rest, -1=fully pressed) to a clean 0..1 "applied pressure"
   * value with hysteresis. The pedal has to climb above `wakeThreshold` to leave rest, and drop
   * below `deadzone` (sleep) to return — so sensor noise sitting right on the threshold doesn't
   * flicker.
   */
  private double normalizePedalWithHysteresis(double rawAxis, boolean isThrottle) {
    double pressed = (1.0 - clampToUnit(rawAxis)) / 2.0;

    if (pressed >= fullThreshold) {
      setState(isThrottle, PedalState.ACTIVE);
      return 1.0;
    }

    if (getState(isThrottle) == PedalState.REST) {
      if (pressed < wakeThreshold) return 0.0;
      setState(isThrottle, PedalState.ACTIVE);
      return pressed;
    }

    if (pressed < deadzone) {
      setState(isThrottle, PedalState.REST);
      return 0.0;
    }
    return pressed;
  }

  private PedalState getState(boolean isThrottle) {
    return isThrottle ? throttleState : brakeState;
  }

  private void setState(boolean isThrottle, PedalState state) {
    if (isThrottle) throttleState = state;
    else brakeState = state;
  }

  private static double clampToUnit(double v) {
    if (v < -1.0) return -1.0;
    if (v > 1.0) return 1.0;
    return v;
  }
}
