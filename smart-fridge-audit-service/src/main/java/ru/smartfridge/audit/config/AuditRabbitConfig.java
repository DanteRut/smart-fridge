package ru.smartfridge.audit.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.smartfridge.events.RoutingKeys;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import tools.jackson.databind.json.JsonMapper;


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

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    /**
     * rabbitListenerContainerFactory — дефолтное имя,
     * которое Spring будет использовать для всех @RabbitListener, если не указать containerFactory явно.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        return factory;
    }
}