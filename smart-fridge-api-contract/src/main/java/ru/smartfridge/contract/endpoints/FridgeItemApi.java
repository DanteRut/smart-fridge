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

@Tag(name = "Fridge items", description = "Управление содержимым холодильника")
@RequestMapping(value = "/api/items", produces = MediaType.APPLICATION_JSON_VALUE)
public interface FridgeItemApi {

    @Operation(summary = "Получить единицу по ID")
    @ApiResponse(responseCode = "404", description = "Единица не найдена",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    EntityModel<FridgeItemResponse> getItemById(@PathVariable Long id);

    @Operation(summary = "Список единиц (опционально фильтр по productId)")
    @GetMapping
    PagedModel<EntityModel<FridgeItemResponse>> getAllItems(
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    );

    @Operation(summary = "Добавить единицу в холодильник")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<EntityModel<FridgeItemResponse>> createItem(@Valid @RequestBody FridgeItemRequest request);

    @Operation(summary = "Полное обновление единицы (PUT)")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    EntityModel<FridgeItemResponse> updateItem(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFridgeItemRequest request
    );

    @Operation(summary = "Частичное обновление единицы (PATCH)")
    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    EntityModel<FridgeItemResponse> patchItem(
            @PathVariable Long id,
            @Valid @RequestBody PatchFridgeItemRequest request
    );

    @Operation(summary = "Удалить единицу")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteItem(@PathVariable Long id);
}