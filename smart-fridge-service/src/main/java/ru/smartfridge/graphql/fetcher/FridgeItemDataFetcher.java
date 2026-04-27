package ru.smartfridge.graphql.fetcher;

import com.netflix.graphql.dgs.*;
import ru.smartfridge.contract.dto.*;
import ru.smartfridge.graphql.types.*;
import ru.smartfridge.service.FridgeItemService;

@DgsComponent
public class FridgeItemDataFetcher {

    private final FridgeItemService itemService;

    public FridgeItemDataFetcher(FridgeItemService itemService) {
        this.itemService = itemService;
    }

    @DgsQuery
    public FridgeItemResponse item(@InputArgument String id) {
        return itemService.findById(Long.parseLong(id));
    }

    @DgsQuery
    public FridgeItemConnectionGql items(@InputArgument String productId,
                                         @InputArgument Integer page,
                                         @InputArgument Integer size) {
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 20;

        Long pid = productId != null ? Long.parseLong(productId) : null;

        PagedResponse<FridgeItemResponse> paged = itemService.findAll(pid, pageNum, pageSize);

        return new FridgeItemConnectionGql(
                paged.content(),
                new PageInfoGql(paged.pageNumber(), paged.pageSize(), paged.totalPages(), paged.last()),
                (int) paged.totalElements()
        );
    }

    @DgsMutation
    public FridgeItemResponse createItem(@InputArgument CreateItemInputGql input) {
        return itemService.create(new FridgeItemRequest(
                Long.parseLong(input.productId()),
                input.quantity(),
                input.unit(),
                input.expiresAt()
        ));
    }

    @DgsMutation
    public FridgeItemResponse updateItem(@InputArgument String id, @InputArgument UpdateItemInputGql input) {
        return itemService.update(Long.parseLong(id),
                new UpdateFridgeItemRequest(input.quantity(), input.unit(), input.expiresAt()));
    }

    @DgsMutation
    public FridgeItemResponse patchItem(@InputArgument String id, @InputArgument PatchItemInputGql input) {
        return itemService.patch(Long.parseLong(id),
                new PatchFridgeItemRequest(input.quantity(), input.unit(), input.expiresAt()));
    }

    @DgsMutation
    public boolean deleteItem(@InputArgument String id) {
        itemService.delete(Long.parseLong(id));
        return true;
    }
}