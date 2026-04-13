package ru.smartfridge.assemblers;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import ru.smartfridge.contract.dto.FridgeItemResponse;
import ru.smartfridge.controllers.FridgeItemController;
import ru.smartfridge.controllers.ProductController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@Component
public class FridgeItemModelAssembler implements RepresentationModelAssembler<FridgeItemResponse, EntityModel<FridgeItemResponse>> {

    @Override
    public EntityModel<FridgeItemResponse> toModel(FridgeItemResponse item) {
        Long productId = item.getProduct() != null ? item.getProduct().getId() : null;

        EntityModel<FridgeItemResponse> model = EntityModel.of(item,
                linkTo(methodOn(FridgeItemController.class).getItemById(item.getId())).withSelfRel(),
                linkTo(methodOn(FridgeItemController.class).getAllItems(null, 0, 20)).withRel("collection")
        );

        if (productId != null) {
            model.add(linkTo(methodOn(ProductController.class).getProductById(productId)).withRel("product"));
        }

        return model;
    }
}