package be.ucll.itintegrationproject.NL_14_backend.controller;

import be.ucll.itintegrationproject.NL_14_backend.controller.DTO.ControllerInput;
import be.ucll.itintegrationproject.NL_14_backend.service.ControlInputLoggingService;
import be.ucll.itintegrationproject.NL_14_backend.service.ControllerBroadcastLoggingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class ControllerWebSocketHub {
  private static final Logger log = LoggerFactory.getLogger(ControllerWebSocketHub.class);

  // Only replay the cached payload to newly-connecting sessions if it is recent
  // enough to be meaningful. Anything older is just noise and can briefly
  // override a live local state on the dashboard during reconnect.
  private static final long LATEST_PAYLOAD_FRESH_MS = 1000;

  private final ObjectMapper objectMapper;
  private final ControlInputLoggingService loggingService;
  private final ControllerBroadcastLoggingService broadcastLoggingService;
  private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
  private volatile String latestPayload;
  private volatile long latestPayloadEpochMs;

  public ControllerWebSocketHub(
      ObjectMapper objectMapper,
      ControlInputLoggingService loggingService,
      ControllerBroadcastLoggingService broadcastLoggingService) {
    this.objectMapper = objectMapper;
    this.loggingService = loggingService;
    this.broadcastLoggingService = broadcastLoggingService;
  }

  public void register(WebSocketSession session) {
    sessions.add(session);
    if (latestPayload == null || !session.isOpen()) return;
    if (System.currentTimeMillis() - latestPayloadEpochMs > LATEST_PAYLOAD_FRESH_MS) return;
    send(session, latestPayload);
  }

  public void unregister(WebSocketSession session) {
    sessions.remove(session);
  }

  public void broadcast(ControllerInput input) {
    try {
      String payload = objectMapper.writeValueAsString(input);
      dispatch(payload);
      loggingService.log(input, "wheel");
      broadcastLoggingService.log(input, "wheel");
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Unable to serialize controller input", e);
    }
  }

  public void broadcast(String payload) {
    dispatch(payload);
    loggingService.logRaw(payload, "keyboard");
    broadcastLoggingService.logRaw(payload, "keyboard");
  }

  private void dispatch(String payload) {
    latestPayload = payload;
    latestPayloadEpochMs = System.currentTimeMillis();
    for (WebSocketSession session : sessions) {
      send(session, payload);
    }
  }

  public String getLatestPayload() {
    return latestPayload;
  }

  private void send(WebSocketSession session, String payload) {
    if (!session.isOpen()) {
      sessions.remove(session);
      return;
    }
    try {
      session.sendMessage(new TextMessage(payload));
    } catch (IOException e) {
      log.debug("Removing websocket session after send failure: {}", session.getId(), e);
      sessions.remove(session);
      try {
        session.close();
      } catch (IOException ignored) {
        // ignore close failures for stale sessions
      }
    }
  }
}
