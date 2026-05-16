package ru.smartfridge.events;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope(
        Metadata metadata,
        Object payload
) {
    public record Metadata(
            String eventId,
            Instant occurredAt,
            String source,
            String routingKey
    ) {}

    public static EventEnvelope wrap(Object payload, String source, String routingKey) {
        return new EventEnvelope(
                new Metadata(UUID.randomUUID().toString(), Instant.now(), source, routingKey),
                payload
        );
    }
}