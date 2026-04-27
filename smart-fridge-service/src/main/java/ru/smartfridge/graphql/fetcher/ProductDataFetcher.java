package ru.smartfridge.graphql.fetcher;

import com.netflix.graphql.dgs.*;
import ru.smartfridge.contract.dto.*;
import ru.smartfridge.graphql.types.*;
import ru.smartfridge.service.ProductService;

@DgsComponent
public class ProductDataFetcher {

    private final ProductService productService;

    public ProductDataFetcher(ProductService productService) {
        this.productService = productService;
    }

    @DgsQuery
    public ProductResponse product(@InputArgument String id) {
        return productService.findById(Long.parseLong(id));
    }

    @DgsQuery
    public ProductConnectionGql products(@InputArgument Integer page, @InputArgument Integer size) {
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 20;

        PagedResponse<ProductResponse> paged = productService.findAll(pageNum, pageSize);

        return new ProductConnectionGql(
                paged.content(),
                new PageInfoGql(paged.pageNumber(), paged.pageSize(), paged.totalPages(), paged.last()),
                (int) paged.totalElements()
        );
    }

    @DgsMutation
    public ProductResponse createProduct(@InputArgument CreateProductInputGql input) {
        return productService.create(new ProductRequest(input.name(), input.category()));
    }

    @DgsMutation
    public ProductResponse updateProduct(@InputArgument String id, @InputArgument UpdateProductInputGql input) {
        return productService.update(Long.parseLong(id), new ProductRequest(input.name(), input.category()));
    }

    @DgsMutation
    public ProductResponse patchProduct(@InputArgument String id, @InputArgument PatchProductInputGql input) {
        return productService.patch(Long.parseLong(id), new PatchProductRequest(input.name(), input.category()));
    }

    @DgsMutation
    public boolean deleteProduct(@InputArgument String id) {
        productService.delete(Long.parseLong(id));
        return true;
    }
}