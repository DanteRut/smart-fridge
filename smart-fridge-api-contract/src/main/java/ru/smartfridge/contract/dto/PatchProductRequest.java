package ru.smartfridge.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Частичное обновление продукта (PATCH). Передайте только изменяемые поля.")
public record PatchProductRequest(
        @Schema(description = "Новое название продукта", example = "Колбаса сервелат")
        @Size(max = 200, message = "Название не может превышать 200 символов")
        String name,

        @Schema(description = "Новая категория", example = "Колбасы")
        @Size(max = 100, message = "Категория не может превышать 100 символов")
        String category
) {}