package be.ucll.itintegrationproject.NL_14_backend.service;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

@Service
public class CameraWebSocketHub {

  private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

  public void register(WebSocketSession session) {
    sessions.add(session);
  }

  public void unregister(WebSocketSession session) {
    sessions.remove(session);
  }

  public void broadcast(byte[] frame) {
    for (WebSocketSession session : sessions) {
      try {
        if (session.isOpen()) {
          session.sendMessage(new BinaryMessage(frame));
        }
      } catch (IOException e) {
        sessions.remove(session);
      }
    }
  }
}
