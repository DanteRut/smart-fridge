package ru.smartfridge.notification.websocket;

import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(NotificationWebSocketHandler.class);

    /**
     * Потокобезопасный реестр активных WebSocket-сессий.
     *
     * Почему ConcurrentHashMap.newKeySet(), а не CopyOnWriteArraySet?
     * - CopyOnWriteArraySet создает копию массива при КАЖДОМ добавлении/удалении.
     *   При частых подключениях/отключениях это создает огромное давление на GC.
     * - ConcurrentHashMap.newKeySet() работает за O(1) без копирования,
     *   идеально подходя для сценариев с частыми мутациями и частыми чтениями (broadcast).
     */
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        LOGGER.info("WebSocket подключён: {} (всего активных: {})", session.getId(), sessions.size());

        // Отправляем приветственное сообщение, чтобы клиент понял, что канал работает
        String welcome = """
                {"type":"CONNECTED","message":"Подключено к Smart Fridge Notifications","activeConnections":%d}"""
                .formatted(sessions.size());
        session.sendMessage(new TextMessage(welcome));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        LOGGER.info("WebSocket отключён: {} (причина: {}, всего активных: {})", session.getId(), status, sessions.size());
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception{
        // Обработка входящих сообщений от клиента (например, ping/pong для keep-alive)
        String payload = message.getPayload();
        if ("ping".equalsIgnoreCase(payload.trim())) {
            session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        LOGGER.warn("WebSocket ошибка: {}", exception.getMessage(), exception);
        // При ошибке транспорта (обрыв связи) принудительно удаляем сессию
        sessions.remove(session);
        // Пытаемся закрыть сессию на уровне Spring, если она еще жива
        if (session.isOpen()) {
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (IOException e) {
                LOGGER.debug("Ошибка при закрытии сессии после transport error", e);
            }
        }
    }

    /**
     * Широковещательная рассылка JSON-сообщения всем подключённым клиентам.
     *
     * Ключевой принцип fault-tolerance: один "мертвый" клиент не должен
     * блокировать рассылку для остальных. Поэтому try/catch внутри цикла.
     *
     * @param json строка JSON для отправки
     */
    public void broadcast(String json) {
        if (sessions.isEmpty()){
            return;
        }

        TextMessage message = new TextMessage(json);
        int sent = 0;
        int failed = 0;


        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                    sent++;
                } catch (IOException e) {
                    LOGGER.warn("Ошибка при отправке сообщения: {}", e.getMessage(), e);
                    sessions.remove(session);
                    failed++;
                }
            } else {
                sessions.remove(session);
                failed++;
            }
        }

        if (sent>0||failed>0){
            LOGGER.info("Отправлено сообщений: {} (успешно: {}, неуспешно: {})", sent+failed, sent, failed);
        }
    }

    /**
     * Количество активных сессий:
     */
    public int getActiveConnections() {
        return sessions.size();
    }

}
