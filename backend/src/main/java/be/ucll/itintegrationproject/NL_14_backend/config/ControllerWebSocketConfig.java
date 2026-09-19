package be.ucll.itintegrationproject.NL_14_backend.config;

import be.ucll.itintegrationproject.NL_14_backend.controller.ControllerWebSocketHandler;
import be.ucll.itintegrationproject.NL_14_backend.controller.ControllerWebSocketHub;
import java.net.URL;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class ControllerWebSocketConfig implements WebSocketConfigurer {
  private final CorsProperties corsProperties;
  private final ControllerWebSocketHub webSocketHub;

  public ControllerWebSocketConfig(
      CorsProperties corsProperties, ControllerWebSocketHub webSocketHub) {
    this.corsProperties = corsProperties;
    this.webSocketHub = webSocketHub;
  }

  @Bean
  public ControllerWebSocketHandler controllerWebSocketHandler() {
    return new ControllerWebSocketHandler(webSocketHub);
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(controllerWebSocketHandler(), "/ws")
        .setAllowedOrigins(
            corsProperties.allowedOrigins().stream().map(URL::toString).toArray(String[]::new));
  }
}
