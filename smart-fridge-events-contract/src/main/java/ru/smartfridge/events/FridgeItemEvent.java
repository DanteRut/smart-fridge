package ru.smartfridge.events;

import java.time.LocalDate;

public sealed interface FridgeItemEvent permits FridgeItemEvent.Created, FridgeItemEvent.Updated, FridgeItemEvent.Deleted, FridgeItemEvent.Enriched {

    record Created(Long id, Long productId, Integer quantity, String unit, LocalDate expiresAt) implements FridgeItemEvent {}
    record Updated(Long id, Long productId, Integer quantity, String unit, LocalDate expiresAt) implements FridgeItemEvent {}
    record Deleted(Long id, Long productId) implements FridgeItemEvent {}

    /**
     * Событие обогащения - результат gRPC вызова к сервису аналитики.
     * Публикуется enrichment-клиентом после получения метрик от сервера.
     */
    record Enriched(
            Long itemId,
            Long productId,
            String storageZone,
            String healthRiskLevel,
            double freshnessScore,
            String consumptionPriority
    ) implements FridgeItemEvent {}
}