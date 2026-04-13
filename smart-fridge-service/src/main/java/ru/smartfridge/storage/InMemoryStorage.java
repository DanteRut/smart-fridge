package ru.smartfridge.storage;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import ru.smartfridge.contract.dto.FridgeItemResponse;
import ru.smartfridge.contract.dto.ProductResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemoryStorage {

    public final Map<Long, ProductResponse> products = new ConcurrentHashMap<>();
    public final Map<Long, FridgeItemResponse> items = new ConcurrentHashMap<>();

    public final AtomicLong productSequence = new AtomicLong(0);
    public final AtomicLong itemSequence = new AtomicLong(0);

    @PostConstruct
    public void init() {
        // Products
        ProductResponse milk = ProductResponse.builder()
                .id(productSequence.incrementAndGet())
                .name("Молоко 2.5%")
                .category("Молочные продукты")
                .itemsCount(2) // упрощение как в практике (пока не пересчитываем автоматически)
                .build();

        ProductResponse sausage = ProductResponse.builder()
                .id(productSequence.incrementAndGet())
                .name("Колбаса докторская")
                .category("Мясное")
                .itemsCount(1)
                .build();

        products.put(milk.getId(), milk);
        products.put(sausage.getId(), sausage);

        // Items (единицы в холодильнике)
        long item1Id = itemSequence.incrementAndGet();
        items.put(item1Id, FridgeItemResponse.builder()
                .id(item1Id)
                .product(milk)
                .quantity(1)
                .unit("pcs")
                .expiresAt(LocalDate.now().plusDays(2))
                .createdAt(LocalDateTime.now())
                .build());

        long item2Id = itemSequence.incrementAndGet();
        items.put(item2Id, FridgeItemResponse.builder()
                .id(item2Id)
                .product(milk)
                .quantity(1)
                .unit("pcs")
                .expiresAt(LocalDate.now().plusDays(5))
                .createdAt(LocalDateTime.now())
                .build());

        long item3Id = itemSequence.incrementAndGet();
        items.put(item3Id, FridgeItemResponse.builder()
                .id(item3Id)
                .product(sausage)
                .quantity(1)
                .unit("pcs")
                .expiresAt(LocalDate.now())
                .createdAt(LocalDateTime.now())
                .build());
    }
}