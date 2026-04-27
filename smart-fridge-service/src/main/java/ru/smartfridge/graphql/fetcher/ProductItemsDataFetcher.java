package ru.smartfridge.graphql.fetcher;

import com.netflix.graphql.dgs.*;
import ru.smartfridge.contract.dto.FridgeItemResponse;
import ru.smartfridge.contract.dto.PagedResponse;
import ru.smartfridge.contract.dto.ProductResponse;
import ru.smartfridge.graphql.types.FridgeItemConnectionGql;
import ru.smartfridge.graphql.types.PageInfoGql;
import ru.smartfridge.service.FridgeItemService;

@DgsComponent
public class ProductItemsDataFetcher {

    private final FridgeItemService itemService;

    public ProductItemsDataFetcher(FridgeItemService itemService) {
        this.itemService = itemService;
    }

    @DgsData(parentType = "Product", field = "items")
    public FridgeItemConnectionGql items(DgsDataFetchingEnvironment dfe,
                                         @InputArgument Integer page,
                                         @InputArgument Integer size) {

        ProductResponse product = dfe.getSource();

        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 20;

        PagedResponse<FridgeItemResponse> paged =
                itemService.findAllByProductId(product.getId(), pageNum, pageSize);

        return new FridgeItemConnectionGql(
                paged.content(),
                new PageInfoGql(paged.pageNumber(), paged.pageSize(), paged.totalPages(), paged.last()),
                (int) paged.totalElements()
        );
    }
}