package ru.smartfridge.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import ru.smartfridge.contract.dto.FridgeItemResponse;
import ru.smartfridge.events.EventEnvelope;
import ru.smartfridge.events.FridgeItemEvent;
import ru.smartfridge.events.RoutingKeys;

@Component
public class FridgeItemEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(FridgeItemEventPublisher.class);
    private static final String SOURCE = "smart-fridge-service";

    private final RabbitTemplate rabbitTemplate;

    public FridgeItemEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishCreated(FridgeItemResponse i) {
        send(RoutingKeys.ITEM_CREATED, new FridgeItemEvent.Created(
                i.getId(), i.getProduct().getId(), i.getQuantity(), i.getUnit(), i.getExpiresAt()
        ));
    }

    public void publishUpdated(FridgeItemResponse i) {
        send(RoutingKeys.ITEM_UPDATED, new FridgeItemEvent.Updated(
                i.getId(), i.getProduct().getId(), i.getQuantity(), i.getUnit(), i.getExpiresAt()
        ));
    }

    public void publishDeleted(Long itemId, Long productId) {
        send(RoutingKeys.ITEM_DELETED, new FridgeItemEvent.Deleted(itemId, productId));
    }

    private void send(String routingKey, FridgeItemEvent event) {
        try {
            EventEnvelope env = EventEnvelope.wrap(event, SOURCE, routingKey);
            rabbitTemplate.convertAndSend(RoutingKeys.EXCHANGE, routingKey, env);
            log.info("Sent event {} [eventId={}]", routingKey, env.metadata().eventId());
        } catch (Exception e) {
            log.error("Failed to send event {}: {}", routingKey, e.getMessage());
        }
    }
}