package be.ucll.itintegrationproject.NL_14_backend.controller;

import be.ucll.itintegrationproject.NL_14_backend.service.CameraWebSocketHub;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

public class CameraWebSocketHandler extends BinaryWebSocketHandler {

  private final CameraWebSocketHub hub;

  public CameraWebSocketHandler(CameraWebSocketHub hub) {
    this.hub = hub;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    hub.register(session);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    hub.unregister(session);
  }

  @Override
  protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
    // Pi sends frames here → broadcast to all clients
    hub.broadcast(message.getPayload().array());
  }
}
