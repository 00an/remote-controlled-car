package be.ucll.itintegrationproject.NL_14_backend.config;

import be.ucll.itintegrationproject.NL_14_backend.controller.CameraWebSocketHandler;
import be.ucll.itintegrationproject.NL_14_backend.service.CameraWebSocketHub;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class CameraWebSocketConfig implements WebSocketConfigurer {

    private final CameraWebSocketHub hub;

    public CameraWebSocketConfig(CameraWebSocketHub hub) {
        this.hub = hub;
    }

    @Bean
    public CameraWebSocketHandler cameraWebSocketHandler() {
        return new CameraWebSocketHandler(hub);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(cameraWebSocketHandler(), "/ws/camera")
                .setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(10 * 1024 * 1024);
        container.setMaxTextMessageBufferSize(10 * 1024 * 1024);
        return container;
    }
}