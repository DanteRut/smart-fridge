package ru.smartfridge.audit.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.smartfridge.audit.config.AuditRabbitConfig;
import ru.smartfridge.events.EventEnvelope;

@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    @RabbitListener(queues = AuditRabbitConfig.AUDIT_QUEUE)
    public void onEvent(EventEnvelope envelope) {
        log.info("AUDIT received: routingKey={}, eventId={}, payload={}",
                envelope.metadata().routingKey(),
                envelope.metadata().eventId(),
                envelope.payload());
    }
}