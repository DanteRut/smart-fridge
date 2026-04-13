package ru.smartfridge.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

@Schema(description = "Частичное обновление единицы продукта (PATCH)")
public record PatchFridgeItemRequest(
        @Positive(message = "quantity должен быть > 0")
        Integer quantity,
        String unit,
        LocalDate expiresAt
) {}