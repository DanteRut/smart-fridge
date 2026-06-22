package ru.smartfridge.notification.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.smartfridge.events.EventEnvelope;
import ru.smartfridge.events.FridgeItemEvent;
import ru.smartfridge.events.ProductEvent;
import ru.smartfridge.notification.config.RabbitMQConfig;
import ru.smartfridge.notification.websocket.NotificationWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EventNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(EventNotificationListener.class);

    private final NotificationWebSocketHandler webSocketHandler;
    private final JsonMapper jsonMapper;

    /**
     * Набор обработанных eventId для дедупликации.
     * RabbitMQ гарантирует at-least-once доставку, поэтому одно и то же событие
     * может прийти дважды (например, при рестарте consumer'а до отправки ACK).
     */
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    public EventNotificationListener(NotificationWebSocketHandler webSocketHandler, JsonMapper jsonMapper) {
        this.webSocketHandler = webSocketHandler;
        this.jsonMapper = jsonMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATIONS_QUEUE, messageConverter = "")
    public void handleEvent(Message message) {
        try {
            JsonNode root = jsonMapper.readTree(message.getBody());
            JsonNode metaNode = root.get("metadata");

            String eventId = metaNode.get("eventId").asText();
            String routingKey = metaNode.get("routingKey").asText();
            String source = metaNode.get("source").asText();
            String occurredAt = metaNode.get("occurredAt").asText();

            // Дедупликация по eventId
            if (!processedEventIds.add(eventId)) {
                log.warn("Дубликат уведомления пропущен: eventId={}", eventId);
                return;
            }

            JsonNode payloadNode = root.get("payload");

            // Формируем человекочитаемое уведомление
            String title = buildTitle(routingKey);
            String description = buildDescription(routingKey, payloadNode);
            String icon = resolveIcon(routingKey);
            String level = resolveLevel(routingKey);

            NotificationPayload payload = new NotificationPayload(
                    "NOTIFICATION", eventId, routingKey, title, description,
                    icon, level, source, occurredAt, Instant.now().toString()
            );

            String json = jsonMapper.writeValueAsString(payload);

            // Рассылаем всем подключенным браузерам
            webSocketHandler.broadcast(json);

            log.info("[NOTIFY] {} | {} (клиентов: {})",
                    routingKey, description, webSocketHandler.getActiveConnections());

        } catch (Exception e) {
            log.error("Ошибка обработки события для уведомлений: {}", e.getMessage(), e);
            // Пробрасываем исключение, чтобы Spring AMQP отправил сообщение в DLQ
            throw new RuntimeException("Не удалось обработать событие", e);
        }
    }

    private String buildTitle(String routingKey) {
        return switch (routingKey) {
            case "product.created" -> "Новый продукт";
            case "product.updated" -> "Продукт обновлён";
            case "product.deleted" -> "Продукт удалён";
            case "item.created" -> "Добавлено в холодильник";
            case "item.updated" -> "Запасы обновлены";
            case "item.deleted" -> "Удалено из холодильника";
            case "item.enriched" -> "Аналитика продукта";
            default -> "Событие: " + routingKey;
        };
    }

    private String buildDescription(String routingKey, JsonNode payload) throws Exception {
        return switch (routingKey) {
            case "product.created" -> {
                ProductEvent.Created e = jsonMapper.treeToValue(payload, ProductEvent.Created.class);
                yield String.format("Создан продукт «%s» (категория: %s)", e.name(), e.category());
            }
            case "product.deleted" -> {
                ProductEvent.Deleted e = jsonMapper.treeToValue(payload, ProductEvent.Deleted.class);
                yield String.format("Удалён продукт «%s»", e.name());
            }
            case "item.created" -> {
                FridgeItemEvent.Created e = jsonMapper.treeToValue(payload, FridgeItemEvent.Created.class);
                yield String.format("Добавлено: %d %s (годен до %s)", e.quantity(), e.unit(), e.expiresAt());
            }
            case "item.deleted" -> {
                FridgeItemEvent.Deleted e = jsonMapper.treeToValue(payload, FridgeItemEvent.Deleted.class);
                yield String.format("Закончился или выброшен продукт (id=%d)", e.productId());
            }
            case "item.enriched" -> {
                FridgeItemEvent.Enriched e = jsonMapper.treeToValue(payload, FridgeItemEvent.Enriched.class);
                yield String.format("Зона хранения: %s | Риск: %s | Приоритет: %s",
                        e.storageZone(), e.healthRiskLevel(), e.consumptionPriority());
            }
            default -> "Произошло событие " + routingKey;
        };
    }

    private String resolveIcon(String routingKey) {
        return switch (routingKey) {
            case "product.created", "item.created" -> "plus";
            case "product.deleted", "item.deleted" -> "trash";
            case "item.enriched" -> "analytics";
            default -> "bell";
        };
    }

    private String resolveLevel(String routingKey) {
        return switch (routingKey) {
            case "product.deleted", "item.deleted" -> "warning";
            case "item.enriched" -> "info";
            default -> "success";
        };
    }

    /**
     * DTO для отправки в WebSocket.
     */
    public record NotificationPayload(
            String type,
            String eventId,
            String eventType,
            String title,
            String description,
            String icon,
            String level,
            String source,
            String eventTimestamp,
            String receivedAt
    ) {}
}