package ru.smartfridge.events;

import java.time.LocalDate;

public sealed interface FridgeItemEvent permits FridgeItemEvent.Created, FridgeItemEvent.Updated, FridgeItemEvent.Deleted {

    record Created(Long id, Long productId, Integer quantity, String unit, LocalDate expiresAt) implements FridgeItemEvent {}
    record Updated(Long id, Long productId, Integer quantity, String unit, LocalDate expiresAt) implements FridgeItemEvent {}
    record Deleted(Long id, Long productId) implements FridgeItemEvent {}
}