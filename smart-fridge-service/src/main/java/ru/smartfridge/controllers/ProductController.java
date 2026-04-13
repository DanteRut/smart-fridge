package ru.smartfridge.controllers;

import org.springframework.data.domain.*;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.smartfridge.assemblers.FridgeItemModelAssembler;
import ru.smartfridge.assemblers.ProductModelAssembler;
import ru.smartfridge.contract.dto.*;
import ru.smartfridge.contract.endpoints.ProductApi;
import ru.smartfridge.service.FridgeItemService;
import ru.smartfridge.service.ProductService;

@RestController
public class ProductController implements ProductApi {

    private final ProductService productService;
    private final FridgeItemService itemService;

    private final ProductModelAssembler productModelAssembler;
    private final FridgeItemModelAssembler itemModelAssembler;

    private final PagedResourcesAssembler<ProductResponse> pagedProductsAssembler;
    private final PagedResourcesAssembler<FridgeItemResponse> pagedItemsAssembler;

    public ProductController(ProductService productService,
                             FridgeItemService itemService,
                             ProductModelAssembler productModelAssembler,
                             FridgeItemModelAssembler itemModelAssembler,
                             PagedResourcesAssembler<ProductResponse> pagedProductsAssembler,
                             PagedResourcesAssembler<FridgeItemResponse> pagedItemsAssembler) {
        this.productService = productService;
        this.itemService = itemService;
        this.productModelAssembler = productModelAssembler;
        this.itemModelAssembler = itemModelAssembler;
        this.pagedProductsAssembler = pagedProductsAssembler;
        this.pagedItemsAssembler = pagedItemsAssembler;
    }

    @Override
    public PagedModel<EntityModel<ProductResponse>> getAllProducts(int page, int size) {
        PagedResponse<ProductResponse> paged = productService.findAll(page, size);

        Page<ProductResponse> springPage = new PageImpl<>(
                paged.content(),
                PageRequest.of(paged.pageNumber(), paged.pageSize()),
                paged.totalElements()
        );

        return pagedProductsAssembler.toModel(springPage, productModelAssembler);
    }

    @Override
    public EntityModel<ProductResponse> getProductById(Long id) {
        return productModelAssembler.toModel(productService.findById(id));
    }

    @Override
    public ResponseEntity<EntityModel<ProductResponse>> createProduct(ProductRequest request) {
        ProductResponse created = productService.create(request);
        EntityModel<ProductResponse> model = productModelAssembler.toModel(created);

        return ResponseEntity.created(model.getRequiredLink("self").toUri()).body(model);
    }

    @Override
    public EntityModel<ProductResponse> updateProduct(Long id, ProductRequest request) {
        return productModelAssembler.toModel(productService.update(id, request));
    }

    @Override
    public EntityModel<ProductResponse> patchProduct(Long id, PatchProductRequest request) {
        return productModelAssembler.toModel(productService.patch(id, request));
    }

    @Override
    public void deleteProduct(Long id) {
        productService.delete(id);
    }

    @Override
    public PagedModel<EntityModel<FridgeItemResponse>> getItemsByProduct(Long id, int page, int size) {
        PagedResponse<FridgeItemResponse> paged = itemService.findAllByProductId(id, page, size);

        Page<FridgeItemResponse> springPage = new PageImpl<>(
                paged.content(),
                PageRequest.of(paged.pageNumber(), paged.pageSize()),
                paged.totalElements()
        );

        return pagedItemsAssembler.toModel(springPage, itemModelAssembler);
    }
}