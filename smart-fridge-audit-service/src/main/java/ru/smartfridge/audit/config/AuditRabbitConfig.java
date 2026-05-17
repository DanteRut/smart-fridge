package ru.smartfridge.audit.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.smartfridge.events.RoutingKeys;

@Configuration
public class AuditRabbitConfig {

    public static final String AUDIT_QUEUE = "smartfridge.audit.queue";

    @Bean
    public TopicExchange eventsExchange() {
        return ExchangeBuilder.topicExchange(RoutingKeys.EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable(AUDIT_QUEUE).build();
    }

    @Bean
    public Binding auditBinding(Queue auditQueue, TopicExchange eventsExchange) {
        // слушаем все события
        return BindingBuilder.bind(auditQueue).to(eventsExchange).with("#");
    }
}