package ru.smartfridge.enrichment.listener;

import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import ru.smartfridge.enrichment.config.RabbitMQConfig;
import ru.smartfridge.enrichment.publisher.EnrichmentEventPublisher;
import ru.smartfridge.events.EventEnvelope;
import ru.smartfridge.events.FridgeItemEvent;
import ru.smartfridge.events.RoutingKeys;
import ru.smartfridge.grpc.AnalyzeItemRequest;
import ru.smartfridge.grpc.FridgeItemAnalyticsGrpc;
import ru.smartfridge.grpc.ItemAnalysisResponse;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class ItemCreatedListener {
    private static final Logger log = LoggerFactory.getLogger(ItemCreatedListener.class);

    private final FridgeItemAnalyticsGrpc.FridgeItemAnalyticsBlockingStub analyticsStub;
    private final EnrichmentEventPublisher enrichmentPublisher;
    private final JsonMapper jsonMapper;

    public ItemCreatedListener(FridgeItemAnalyticsGrpc.FridgeItemAnalyticsBlockingStub analyticsStub,
                               EnrichmentEventPublisher enrichmentPublisher,
                               JsonMapper jsonMapper){
        this.analyticsStub = analyticsStub;
        this.enrichmentPublisher = enrichmentPublisher;
        this.jsonMapper = jsonMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.ENRICHMENT_QUEUE, messageConverter = "")
    public void handleItemCreated(Message message){
        try {
            JsonNode root = jsonMapper.readTree(message.getBody());
            JsonNode payloadNode = root.get("payload");

            FridgeItemEvent.Created createdEvent = jsonMapper.treeToValue(payloadNode, FridgeItemEvent.Created.class);
            String eventId = root.get("metadata").get("eventId").asText();

            log.info("Получено событие item.created: itemId={}, productId={} [eventId={}]",
                    createdEvent.id(), createdEvent.productId(), eventId);

            LocalDate expiresAt = createdEvent.expiresAt();
            int daysUntilExpiration = (expiresAt != null) ? (int) ChronoUnit.DAYS.between(LocalDate.now(), expiresAt) : 0;

            AnalyzeItemRequest grpcRequest = AnalyzeItemRequest.newBuilder()
                    .setItemId(createdEvent.id())
                    .setProductName("")
                    .setCategory("General")
                    .setQuantity(createdEvent.quantity() != null ? createdEvent.quantity() : 1)
                    .setDaysUntilExpiration(daysUntilExpiration)
                    .build();

            log.info("Вызов gRPC: FridgeItemAnalytics.AnalyzeItem(itemId={})", createdEvent.id());
            ItemAnalysisResponse grpcResponse = analyticsStub.analyzeItem(grpcRequest);

            FridgeItemEvent.Enriched enrichedEvent = new FridgeItemEvent.Enriched(
                    grpcResponse.getItemId(),
                    null,
                    grpcResponse.getStorageZone(),
                    grpcResponse.getHealthRiskLevel(),
                    grpcResponse.getFreshnessScore(),
                    grpcResponse.getConsumptionPriority()
            );

            enrichmentPublisher.publishEnriched(enrichedEvent);
            log.info("Продукт обогащен: itemId={} [eventId={}]", createdEvent.id(), eventId);

        } catch (StatusRuntimeException e) {
            log.error("gRPC ошибка при обогащении: {} ({})", e.getStatus().getDescription(), e.getStatus().getCode());
            throw new RuntimeException("gRPC-вызов завершился ошибкой", e);
        } catch (Exception e){
            log.error("Ошибка обработки item.created: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось обработать событие", e);
        }
    }
}
