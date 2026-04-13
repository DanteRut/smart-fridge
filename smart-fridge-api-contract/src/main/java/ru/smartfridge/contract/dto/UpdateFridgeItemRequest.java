package ru.smartfridge.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

@Schema(description = "Полное обновление единицы продукта (PUT). productId не меняется.")
public record UpdateFridgeItemRequest(
        @NotNull(message = "quantity обязателен")
        @Positive(message = "quantity должен быть > 0")
        Integer quantity,

        @NotBlank(message = "unit обязателен")
        String unit,

        @NotNull(message = "expiresAt обязателен")
        LocalDate expiresAt
) {}