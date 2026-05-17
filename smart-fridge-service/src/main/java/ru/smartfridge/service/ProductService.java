package ru.smartfridge.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import ru.smartfridge.contract.dto.*;
import ru.smartfridge.contract.exception.ResourceNotFoundException;
import ru.smartfridge.event.ProductEventPublisher;
import ru.smartfridge.storage.InMemoryStorage;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private final InMemoryStorage storage;
    private final FridgeItemService itemService;
    private final ProductEventPublisher eventPublisher;

    public ProductService(InMemoryStorage storage, @Lazy FridgeItemService itemService, ProductEventPublisher eventPublisher) {
        this.storage = storage;
        this.itemService = itemService;
        this.eventPublisher = eventPublisher;
    }

    public PagedResponse<ProductResponse> findAll(int page, int size) {
        List<ProductResponse> all = storage.products.values().stream()
                .sorted(Comparator.comparingLong(ProductResponse::getId))
                .toList();

        int totalElements = all.size();
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 1;

        int from = page * size;
        int to = Math.min(from + size, totalElements);

        List<ProductResponse> content = (from >= totalElements) ? List.of() : all.subList(from, to);

        return new PagedResponse<>(content, page, size, totalElements, totalPages, page >= totalPages - 1);
    }

    public ProductResponse findById(Long id) {
        return Optional.ofNullable(storage.products.get(id))
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    public ProductResponse create(ProductRequest request) {
        long id = storage.productSequence.incrementAndGet();

        ProductResponse created = ProductResponse.builder()
                .id(id)
                .name(request.name())
                .category(request.category())
                .itemsCount(0)
                .build();

        storage.products.put(id, created);
        eventPublisher.publishCreated(created);
        return created;
    }

    public ProductResponse update(Long id, ProductRequest request) {
        ProductResponse existing = findById(id);

        ProductResponse updated = ProductResponse.builder()
                .id(id)
                .name(request.name())
                .category(request.category())
                .itemsCount(existing.getItemsCount())
                .build();

        storage.products.put(id, updated);
        eventPublisher.publishUpdated(updated);
        return updated;
    }

    public ProductResponse patch(Long id, PatchProductRequest request) {
        ProductResponse existing = findById(id);

        ProductResponse updated = ProductResponse.builder()
                .id(id)
                .name(request.name() != null ? request.name() : existing.getName())
                .category(request.category() != null ? request.category() : existing.getCategory())
                .itemsCount(existing.getItemsCount())
                .build();

        storage.products.put(id, updated);
        eventPublisher.publishUpdated(updated);
        return updated;
    }

    public void delete(Long id) {
        ProductResponse item = findById(id); // 404 если продукта нет
        itemService.deleteItemsByProductId(id); // каскадное удаление единиц
        storage.products.remove(id);
        eventPublisher.publishDeleted(item);
    }
}