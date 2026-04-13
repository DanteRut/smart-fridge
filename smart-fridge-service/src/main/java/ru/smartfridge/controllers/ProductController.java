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
import ru.smartfridge.contract.endpoints.ProductApi;
import ru.smartfridge.service.FridgeItemService;
import ru.smartfridge.service.ProductService;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
public class ProductController implements ProductApi {

    private final ProductService productService;
    private final FridgeItemService itemService;

    private final PagedResourcesAssembler<ProductResponse> pagedProductsAssembler;
    private final PagedResourcesAssembler<FridgeItemResponse> pagedItemsAssembler;

    public ProductController(ProductService productService,
                             FridgeItemService itemService,
                             PagedResourcesAssembler<ProductResponse> pagedProductsAssembler,
                             PagedResourcesAssembler<FridgeItemResponse> pagedItemsAssembler) {
        this.productService = productService;
        this.itemService = itemService;
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

        return pagedProductsAssembler.toModel(springPage, product ->
                EntityModel.of(product,
                        linkTo(methodOn(ProductController.class).getProductById(product.getId())).withSelfRel(),
                        linkTo(methodOn(ProductController.class).getAllProducts(0, 20)).withRel("collection"),
                        linkTo(methodOn(ProductController.class).getItemsByProduct(product.getId(), 0, 20)).withRel("items")
                )
        );
    }

    @Override
    public EntityModel<ProductResponse> getProductById(Long id) {
        ProductResponse product = productService.findById(id);
        return EntityModel.of(product,
                linkTo(methodOn(ProductController.class).getProductById(id)).withSelfRel(),
                linkTo(methodOn(ProductController.class).getAllProducts(0, 20)).withRel("collection"),
                linkTo(methodOn(ProductController.class).getItemsByProduct(id, 0, 20)).withRel("items")
        );
    }

    @Override
    public ResponseEntity<EntityModel<ProductResponse>> createProduct(ProductRequest request) {
        ProductResponse created = productService.create(request);

        EntityModel<ProductResponse> model = EntityModel.of(created,
                linkTo(methodOn(ProductController.class).getProductById(created.getId())).withSelfRel(),
                linkTo(methodOn(ProductController.class).getAllProducts(0, 20)).withRel("collection"),
                linkTo(methodOn(ProductController.class).getItemsByProduct(created.getId(), 0, 20)).withRel("items")
        );

        return ResponseEntity
                .created(model.getRequiredLink("self").toUri())
                .body(model);
    }

    @Override
    public EntityModel<ProductResponse> updateProduct(Long id, ProductRequest request) {
        ProductResponse updated = productService.update(id, request);
        return EntityModel.of(updated,
                linkTo(methodOn(ProductController.class).getProductById(id)).withSelfRel(),
                linkTo(methodOn(ProductController.class).getAllProducts(0, 20)).withRel("collection"),
                linkTo(methodOn(ProductController.class).getItemsByProduct(id, 0, 20)).withRel("items")
        );
    }

    @Override
    public EntityModel<ProductResponse> patchProduct(Long id, PatchProductRequest request) {
        ProductResponse updated = productService.patch(id, request);
        return EntityModel.of(updated,
                linkTo(methodOn(ProductController.class).getProductById(id)).withSelfRel(),
                linkTo(methodOn(ProductController.class).getAllProducts(0, 20)).withRel("collection"),
                linkTo(methodOn(ProductController.class).getItemsByProduct(id, 0, 20)).withRel("items")
        );
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

        return pagedItemsAssembler.toModel(springPage, item ->
                EntityModel.of(item,
                        linkTo(methodOn(FridgeItemController.class).getItemById(item.getId())).withSelfRel(),
                        linkTo(methodOn(FridgeItemController.class).getAllItems(id, 0, 20)).withRel("collection"),
                        linkTo(methodOn(ProductController.class).getProductById(id)).withRel("product")
                )
        );
    }
}