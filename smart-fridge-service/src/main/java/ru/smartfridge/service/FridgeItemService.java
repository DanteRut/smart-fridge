package ru.smartfridge.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import ru.smartfridge.contract.dto.*;
import ru.smartfridge.contract.exception.ResourceNotFoundException;
import ru.smartfridge.event.FridgeItemEventPublisher;
import ru.smartfridge.storage.InMemoryStorage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class FridgeItemService {

    private final InMemoryStorage storage;
    private final ProductService productService;
    private final FridgeItemEventPublisher eventPublisher;

    public FridgeItemService(InMemoryStorage storage, @Lazy ProductService productService, FridgeItemEventPublisher eventPublisher) {
        this.storage = storage;
        this.productService = productService;
        this.eventPublisher = eventPublisher;
    }

    public FridgeItemResponse findById(Long id) {
        return Optional.ofNullable(storage.items.get(id))
                .orElseThrow(() -> new ResourceNotFoundException("FridgeItem", id));
    }

    public PagedResponse<FridgeItemResponse> findAll(Long productId, int page, int size) {
        Stream<FridgeItemResponse> stream = storage.items.values().stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId()));

        if (productId != null) {
            stream = stream.filter(i -> i.getProduct() != null && i.getProduct().getId().equals(productId));
        }

        List<FridgeItemResponse> all = stream.toList();

        int totalElements = all.size();
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 1;

        int from = page * size;
        int to = Math.min(from + size, totalElements);

        List<FridgeItemResponse> content = (from >= totalElements) ? List.of() : all.subList(from, to);

        return new PagedResponse<>(content, page, size, totalElements, totalPages, page >= totalPages - 1);
    }

    public PagedResponse<FridgeItemResponse> findAllByProductId(Long productId, int page, int size) {
        // Явно проверяем существование продукта → если нет, получим 404
        productService.findById(productId);
        return findAll(productId, page, size);
    }

    public FridgeItemResponse create(FridgeItemRequest request) {
        ProductResponse product = productService.findById(request.productId());

        long id = storage.itemSequence.incrementAndGet();

        FridgeItemResponse created = FridgeItemResponse.builder()
                .id(id)
                .product(product)
                .quantity(request.quantity())
                .unit(request.unit())
                .expiresAt(request.expiresAt())
                .createdAt(LocalDateTime.now())
                .build();

        storage.items.put(id, created);
        eventPublisher.publishCreated(created);
        return created;
    }

    public FridgeItemResponse update(Long id, UpdateFridgeItemRequest request) {
        FridgeItemResponse existing = findById(id);

        FridgeItemResponse updated = FridgeItemResponse.builder()
                .id(id)
                .product(existing.getProduct()) // productId не меняется
                .quantity(request.quantity())
                .unit(request.unit())
                .expiresAt(request.expiresAt())
                .createdAt(existing.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        storage.items.put(id, updated);
        eventPublisher.publishUpdated(updated);
        return updated;
    }

    public FridgeItemResponse patch(Long id, PatchFridgeItemRequest request) {
        FridgeItemResponse existing = findById(id);

        FridgeItemResponse updated = FridgeItemResponse.builder()
                .id(id)
                .product(existing.getProduct())
                .quantity(request.quantity() != null ? request.quantity() : existing.getQuantity())
                .unit(request.unit() != null ? request.unit() : existing.getUnit())
                .expiresAt(request.expiresAt() != null ? request.expiresAt() : existing.getExpiresAt())
                .createdAt(existing.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        storage.items.put(id, updated);
        eventPublisher.publishUpdated(updated);
        return updated;
    }

    public void delete(Long id) {
        FridgeItemResponse item = findById(id);
        storage.items.remove(id);
        eventPublisher.publishDeleted(id, item.getProduct().getId());
    }

    public void deleteItemsByProductId(Long productId) {
        List<Long> toDelete = storage.items.values().stream()
                .filter(i -> i.getProduct() != null && i.getProduct().getId().equals(productId))
                .map(FridgeItemResponse::getId)
                .toList();

        toDelete.forEach(storage.items::remove);
    }
}