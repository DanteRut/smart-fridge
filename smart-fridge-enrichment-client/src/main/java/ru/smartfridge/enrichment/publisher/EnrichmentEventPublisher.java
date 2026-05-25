package ru.smartfridge.enrichment.publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import ru.smartfridge.enrichment.config.RabbitMQConfig;
import ru.smartfridge.events.EventEnvelope;
import ru.smartfridge.events.FridgeItemEvent;
import ru.smartfridge.events.RoutingKeys;

@Component
public class EnrichmentEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(EnrichmentEventPublisher.class);
    private static final String SOURSE = "smart-fridge-enrichment-client";

    private final RabbitTemplate rabbitTemplate;

    public EnrichmentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishEnriched(FridgeItemEvent.Enriched enrichedEvent){
        try {
            EventEnvelope eventEnvelope = EventEnvelope.wrap(enrichedEvent, SOURSE, RoutingKeys.ITEM_ENRICHED);
            rabbitTemplate.convertAndSend(RoutingKeys.EXCHANGE, RoutingKeys.ITEM_ENRICHED, enrichedEvent);
            log.info("Событие отправлено: {} [itemId={}]", RoutingKeys.ITEM_ENRICHED, enrichedEvent.itemId());
        } catch (Exception e) {
            log.error("Не удалось отправить событие {}: {}", RoutingKeys.ITEM_ENRICHED, e.getMessage());
        }
    }
}
