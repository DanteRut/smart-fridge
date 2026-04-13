package ru.smartfridge.contract.endpoints;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;
import ru.smartfridge.contract.dto.ProductResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(collectionRelation = "items", itemRelation = "item")
@Schema(description = "Единица продукта в холодильнике")
public class FridgeItemResponse extends RepresentationModel<FridgeItemResponse> {

    @Schema(description = "ID единицы", example = "10")
    private final Long id;

    @Schema(description = "Продукт")
    private final ProductResponse product;

    @Schema(description = "Количество", example = "1")
    private final Integer quantity;

    @Schema(description = "Единица измерения", example = "pcs")
    private final String unit;

    @Schema(description = "Срок годности", example = "2026-04-20")
    private final LocalDate expiresAt;

    @Schema(description = "Когда добавлено")
    private final LocalDateTime createdAt;

    @Schema(description = "Когда обновлено")
    private final LocalDateTime updatedAt;
}