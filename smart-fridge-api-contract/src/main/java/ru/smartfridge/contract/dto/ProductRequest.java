package ru.smartfridge.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Запрос на создание или полное обновление продукта (POST/PUT)")
public record ProductRequest(
        @Schema(description = "Название продукта", example = "Колбаса докторская", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Название продукта не может быть пустым")
        @Size(max = 200, message = "Название не может превышать 200 символов")
        String name,

        @Schema(description = "Категория", example = "Мясное")
        @Size(max = 100, message = "Категория не может превышать 100 символов")
        String category
) {}