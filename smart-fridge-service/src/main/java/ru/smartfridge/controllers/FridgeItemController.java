package ru.smartfridge.controllers;

import org.springframework.data.domain.*;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.smartfridge.assemblers.FridgeItemModelAssembler;
import ru.smartfridge.contract.dto.*;
import ru.smartfridge.contract.endpoints.FridgeItemApi;
import ru.smartfridge.service.FridgeItemService;

@RestController
public class FridgeItemController implements FridgeItemApi {

    private final FridgeItemService itemService;
    private final FridgeItemModelAssembler itemModelAssembler;
    private final PagedResourcesAssembler<FridgeItemResponse> pagedItemsAssembler;

    public FridgeItemController(FridgeItemService itemService,
                                FridgeItemModelAssembler itemModelAssembler,
                                PagedResourcesAssembler<FridgeItemResponse> pagedItemsAssembler) {
        this.itemService = itemService;
        this.itemModelAssembler = itemModelAssembler;
        this.pagedItemsAssembler = pagedItemsAssembler;
    }

    @Override
    public EntityModel<FridgeItemResponse> getItemById(Long id) {
        return itemModelAssembler.toModel(itemService.findById(id));
    }

    @Override
    public PagedModel<EntityModel<FridgeItemResponse>> getAllItems(Long productId, int page, int size) {
        PagedResponse<FridgeItemResponse> paged = itemService.findAll(productId, page, size);

        Page<FridgeItemResponse> springPage = new PageImpl<>(
                paged.content(),
                PageRequest.of(paged.pageNumber(), paged.pageSize()),
                paged.totalElements()
        );

        return pagedItemsAssembler.toModel(springPage, itemModelAssembler);
    }

    @Override
    public ResponseEntity<EntityModel<FridgeItemResponse>> createItem(FridgeItemRequest request) {
        FridgeItemResponse created = itemService.create(request);
        EntityModel<FridgeItemResponse> model = itemModelAssembler.toModel(created);

        return ResponseEntity.created(model.getRequiredLink("self").toUri()).body(model);
    }

    @Override
    public EntityModel<FridgeItemResponse> updateItem(Long id, UpdateFridgeItemRequest request) {
        return itemModelAssembler.toModel(itemService.update(id, request));
    }

    @Override
    public EntityModel<FridgeItemResponse> patchItem(Long id, PatchFridgeItemRequest request) {
        return itemModelAssembler.toModel(itemService.patch(id, request));
    }

    @Override
    public void deleteItem(Long id) {
        itemService.delete(id);
    }
}