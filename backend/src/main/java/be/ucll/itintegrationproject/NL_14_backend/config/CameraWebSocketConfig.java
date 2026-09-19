package be.ucll.itintegrationproject.NL_14_backend.config;

import be.ucll.itintegrationproject.NL_14_backend.controller.CameraWebSocketHandler;
import be.ucll.itintegrationproject.NL_14_backend.service.CameraWebSocketHub;
import java.net.URL;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class CameraWebSocketConfig implements WebSocketConfigurer {

    private final CameraWebSocketHub hub;
    private final CorsProperties corsProperties;

    public CameraWebSocketConfig(CameraWebSocketHub hub, CorsProperties corsProperties) {
        this.hub = hub;
        this.corsProperties = corsProperties;
    }

    @Bean
    public CameraWebSocketHandler cameraWebSocketHandler() {
        return new CameraWebSocketHandler(hub);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(cameraWebSocketHandler(), "/ws/camera")
                .setAllowedOrigins(
                        corsProperties.allowedOrigins().stream().map(URL::toString).toArray(String[]::new));
    }

    // Only meaningful with a real embedded servlet container (e.g. Tomcat).
    // Skipped in tests, which run against a mock web environment that has no
    // ServletContext-backed jakarta.websocket.server.ServerContainer attribute.
    @Bean
    @Profile("!test")
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(10 * 1024 * 1024);
        container.setMaxTextMessageBufferSize(10 * 1024 * 1024);
        return container;
    }
}
