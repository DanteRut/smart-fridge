package ru.smartfridge.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import ru.smartfridge.contract.dto.ProductResponse;
import ru.smartfridge.events.EventEnvelope;
import ru.smartfridge.events.ProductEvent;
import ru.smartfridge.events.RoutingKeys;

@Component
public class ProductEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProductEventPublisher.class);
    private static final String SOURCE = "smart-fridge-service";

    private final RabbitTemplate rabbitTemplate;

    public ProductEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishCreated(ProductResponse p) {
        send(RoutingKeys.PRODUCT_CREATED, new ProductEvent.Created(p.getId(), p.getName(), p.getCategory()));
    }

    public void publishUpdated(ProductResponse p) {
        send(RoutingKeys.PRODUCT_UPDATED, new ProductEvent.Updated(p.getId(), p.getName(), p.getCategory()));
    }

    public void publishDeleted(ProductResponse p) {
        send(RoutingKeys.PRODUCT_DELETED, new ProductEvent.Deleted(p.getId(), p.getName()));
    }

    private void send(String routingKey, ProductEvent event) {
        try {
            EventEnvelope env = EventEnvelope.wrap(event, SOURCE, routingKey);
            rabbitTemplate.convertAndSend(RoutingKeys.EXCHANGE, routingKey, env);
            log.info("Sent event {} [eventId={}]", routingKey, env.metadata().eventId());
        } catch (Exception e) {
            log.error("Failed to send event {}: {}", routingKey, e.getMessage());
        }
    }
}