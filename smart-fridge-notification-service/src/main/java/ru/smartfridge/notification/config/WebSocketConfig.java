package ru.smartfridge.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import ru.smartfridge.notification.websocket.NotificationWebSocketHandler;

@Configuration
public class WebSocketConfig implements WebSocketConfigurer {
    private final NotificationWebSocketHandler notificationWebSocketHandler;

    public WebSocketConfig(NotificationWebSocketHandler notificationWebSocketHandler) {
        this.notificationWebSocketHandler = notificationWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        try {
            registry.addHandler(notificationWebSocketHandler, "/ws/notification")
                    .setAllowedOrigins("*");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
