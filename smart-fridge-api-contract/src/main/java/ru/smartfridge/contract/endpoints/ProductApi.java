package ru.smartfridge.contract.endpoints;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.smartfridge.contract.dto.*;

@Tag(name = "Products", description = "Управление типами продуктов")
@RequestMapping(value = "/api/products", produces = MediaType.APPLICATION_JSON_VALUE)
public interface ProductApi {

    @Operation(summary = "Список продуктов")
    @GetMapping
    PagedModel<EntityModel<ProductResponse>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    );

    @Operation(summary = "Получить продукт по ID")
    @ApiResponse(responseCode = "404", description = "Продукт не найден",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    EntityModel<ProductResponse> getProductById(@PathVariable Long id);

    @Operation(summary = "Создать продукт")
    @ApiResponse(responseCode = "400", description = "Ошибка валидации",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<EntityModel<ProductResponse>> createProduct(@Valid @RequestBody ProductRequest request);

    @Operation(summary = "Полное обновление продукта (PUT)")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    EntityModel<ProductResponse> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest request);

    @Operation(summary = "Частичное обновление продукта (PATCH)")
    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    EntityModel<ProductResponse> patchProduct(@PathVariable Long id, @Valid @RequestBody PatchProductRequest request);

    @Operation(summary = "Удалить продукт (и все его единицы в холодильнике)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteProduct(@PathVariable Long id);

    @Operation(summary = "Единицы продукта в холодильнике (суб-ресурс)")
    @GetMapping("/{id}/items")
    PagedModel<EntityModel<FridgeItemResponse>> getItemsByProduct(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    );
}