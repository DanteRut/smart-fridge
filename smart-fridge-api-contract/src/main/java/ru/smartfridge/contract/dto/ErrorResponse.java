package ru.smartfridge.contract.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Стандартный ответ об ошибке (RFC 7807 Problem Details)")
public record ErrorResponse(
        @Schema(description = "HTTP статус-код", example = "404") int status,
        @Schema(description = "URI-идентификатор типа ошибки", example = "https://api.example.com/problems/resource-not-found")
        String type,
        @Schema(description = "Краткое название ошибки", example = "Ресурс не найден") String title,
        @Schema(description = "Детали", example = "Product with id=99 not found") String detail,
        @Schema(description = "URI запроса", example = "/api/products/99") String instance,
        @Schema(description = "Время возникновения ошибки (UTC)") Instant timestamp,
        @Schema(description = "Ошибки по полям (только для 400)") List<FieldError> fieldErrors
) {
    public record FieldError(String field, Object rejectedValue, String message) {}
}