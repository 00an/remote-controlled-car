package be.ucll.itintegrationproject.NL_14_backend.controller;

import java.io.IOException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class ControllerWebSocketHandler extends TextWebSocketHandler {
  private final ControllerWebSocketHub webSocketHub;

  public ControllerWebSocketHandler(ControllerWebSocketHub webSocketHub) {
    this.webSocketHub = webSocketHub;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    webSocketHub.register(session);
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    webSocketHub.broadcast(message.getPayload());
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    webSocketHub.unregister(session);
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception)
      throws IOException {
    webSocketHub.unregister(session);
    if (session.isOpen()) {
      session.close(CloseStatus.SERVER_ERROR);
    }
  }
}
