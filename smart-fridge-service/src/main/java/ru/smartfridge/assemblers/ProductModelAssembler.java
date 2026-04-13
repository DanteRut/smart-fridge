package ru.smartfridge.assemblers;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import ru.smartfridge.contract.dto.ProductResponse;
import ru.smartfridge.controllers.ProductController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@Component
public class ProductModelAssembler implements RepresentationModelAssembler<ProductResponse, EntityModel<ProductResponse>> {

    @Override
    public EntityModel<ProductResponse> toModel(ProductResponse product) {
        return EntityModel.of(product,
                linkTo(methodOn(ProductController.class).getProductById(product.getId())).withSelfRel(),
                linkTo(methodOn(ProductController.class).getAllProducts(0, 20)).withRel("collection"),
                linkTo(methodOn(ProductController.class).getItemsByProduct(product.getId(), 0, 20)).withRel("items")
        );
    }
}