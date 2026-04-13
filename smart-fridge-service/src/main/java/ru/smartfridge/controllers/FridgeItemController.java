package ru.smartfridge.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.smartfridge.contract.dto.*;
import ru.smartfridge.contract.endpoints.FridgeItemApi;
import ru.smartfridge.service.FridgeItemService;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
public class FridgeItemController implements FridgeItemApi {

    private final FridgeItemService itemService;
    private final PagedResourcesAssembler<FridgeItemResponse> pagedItemsAssembler;

    public FridgeItemController(FridgeItemService itemService,
                                PagedResourcesAssembler<FridgeItemResponse> pagedItemsAssembler) {
        this.itemService = itemService;
        this.pagedItemsAssembler = pagedItemsAssembler;
    }

    @Override
    public EntityModel<FridgeItemResponse> getItemById(Long id) {
        FridgeItemResponse item = itemService.findById(id);
        return EntityModel.of(item,
                linkTo(methodOn(FridgeItemController.class).getItemById(id)).withSelfRel(),
                linkTo(methodOn(FridgeItemController.class).getAllItems(null, 0, 20)).withRel("collection")
        );
    }

    @Override
    public PagedModel<EntityModel<FridgeItemResponse>> getAllItems(Long productId, int page, int size) {
        PagedResponse<FridgeItemResponse> paged = itemService.findAll(productId, page, size);

        Page<FridgeItemResponse> springPage = new PageImpl<>(
                paged.content(),
                PageRequest.of(paged.pageNumber(), paged.pageSize()),
                paged.totalElements()
        );

        return pagedItemsAssembler.toModel(springPage, item ->
                EntityModel.of(item,
                        linkTo(methodOn(FridgeItemController.class).getItemById(item.getId())).withSelfRel(),
                        linkTo(methodOn(FridgeItemController.class).getAllItems(productId, 0, 20)).withRel("collection")
                )
        );
    }

    @Override
    public ResponseEntity<EntityModel<FridgeItemResponse>> createItem(FridgeItemRequest request) {
        FridgeItemResponse created = itemService.create(request);

        EntityModel<FridgeItemResponse> model = EntityModel.of(created,
                linkTo(methodOn(FridgeItemController.class).getItemById(created.getId())).withSelfRel(),
                linkTo(methodOn(FridgeItemController.class).getAllItems(null, 0, 20)).withRel("collection")
        );

        return ResponseEntity
                .created(model.getRequiredLink("self").toUri())
                .body(model);
    }

    @Override
    public EntityModel<FridgeItemResponse> updateItem(Long id, UpdateFridgeItemRequest request) {
        FridgeItemResponse updated = itemService.update(id, request);
        return EntityModel.of(updated,
                linkTo(methodOn(FridgeItemController.class).getItemById(id)).withSelfRel(),
                linkTo(methodOn(FridgeItemController.class).getAllItems(null, 0, 20)).withRel("collection")
        );
    }

    @Override
    public EntityModel<FridgeItemResponse> patchItem(Long id, PatchFridgeItemRequest request) {
        FridgeItemResponse updated = itemService.patch(id, request);
        return EntityModel.of(updated,
                linkTo(methodOn(FridgeItemController.class).getItemById(id)).withSelfRel(),
                linkTo(methodOn(FridgeItemController.class).getAllItems(null, 0, 20)).withRel("collection")
        );
    }

    @Override
    public void deleteItem(Long id) {
        itemService.delete(id);
    }
}