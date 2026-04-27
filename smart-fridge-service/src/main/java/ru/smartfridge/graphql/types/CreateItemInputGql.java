package ru.smartfridge.graphql.types;

import java.time.LocalDate;

public record CreateItemInputGql(String productId, Integer quantity, String unit, LocalDate expiresAt) {}