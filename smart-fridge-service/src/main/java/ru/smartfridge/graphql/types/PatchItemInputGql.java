package ru.smartfridge.graphql.types;

import java.time.LocalDate;

public record PatchItemInputGql(Integer quantity, String unit, LocalDate expiresAt) {}