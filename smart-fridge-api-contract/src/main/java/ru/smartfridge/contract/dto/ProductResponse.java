package ru.smartfridge.contract.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

@Getter
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(collectionRelation = "products", itemRelation = "product")
@Schema(description = "Информация о продукте")
public class ProductResponse extends RepresentationModel<ProductResponse> {

    @Schema(description = "ID продукта", example = "1")
    private final Long id;

    @Schema(description = "Название", example = "Колбаса докторская")
    private final String name;

    @Schema(description = "Категория", example = "Мясное")
    private final String category;

    @Schema(description = "Количество единиц этого продукта в холодильнике", example = "2")
    private final Integer itemsCount;
}