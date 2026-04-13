package ru.smartfridge.controllers;

import org.springframework.hateoas.RepresentationModel;
import org.springframework.web.bind.annotation.*;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@RestController
@RequestMapping("/api")
public class RootController {

    @GetMapping
    public RepresentationModel<?> getRoot() {
        RepresentationModel<?> root = new RepresentationModel<>();
        root.add(
                linkTo(methodOn(ProductController.class).getAllProducts(0, 20)).withRel("products"),
                linkTo(methodOn(FridgeItemController.class).getAllItems(null, 0, 20)).withRel("items")
        );
        return root;
    }
}