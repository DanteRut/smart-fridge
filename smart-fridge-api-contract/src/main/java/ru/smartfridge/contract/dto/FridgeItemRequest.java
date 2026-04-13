package ru.smartfridge.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

@Schema(description = "Запрос на добавление единицы продукта в холодильник (POST)")
public record FridgeItemRequest(
        @Schema(description = "ID продукта", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "productId обязателен")
        Long productId,

        @Schema(description = "Количество", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "quantity обязателен")
        @Positive(message = "quantity должен быть > 0")
        Integer quantity,

        @Schema(description = "Единицы измерения", example = "pcs", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "unit обязателен")
        String unit,

        @Schema(description = "Срок годности (дата)", example = "2026-04-20", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "expiresAt обязателен")
        LocalDate expiresAt
) {}