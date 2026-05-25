package ru.smartfridge.analytics.service;

import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.smartfridge.grpc.AnalyzeItemRequest;
import ru.smartfridge.grpc.FridgeItemAnalyticsGrpc;
import ru.smartfridge.grpc.ItemAnalysisResponse;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class FridgeItemAnalyticsServiceImpl extends FridgeItemAnalyticsGrpc.FridgeItemAnalyticsImplBase {

    private static final Logger log = LoggerFactory.getLogger(FridgeItemAnalyticsServiceImpl.class);

    @Override
    public void analyzeItem(AnalyzeItemRequest request, StreamObserver<ItemAnalysisResponse> responseObserver) {
        log.info("gRPC запрос: анализ продукта id={}, «{}» (категория: {}, дней до истечения: {})",
                request.getItemId(), request.getProductName(),
                request.getCategory(), request.getDaysUntilExpiration());

        // Вычисляем метрики
        String storageZone = determineStorageZone(request.getCategory(), request.getDaysUntilExpiration());
        String healthRisk = assessHealthRisk(request.getDaysUntilExpiration());
        double freshnessScore = calculateFreshnessScore(request.getDaysUntilExpiration());
        String priority = determineConsumptionPriority(request.getDaysUntilExpiration());

        // Формируем ответ через Builder
        ItemAnalysisResponse response = ItemAnalysisResponse.newBuilder()
                .setItemId(request.getItemId())
                .setStorageZone(storageZone)
                .setHealthRiskLevel(healthRisk)
                .setFreshnessScore(freshnessScore)
                .setConsumptionPriority(priority)
                .build();

        log.info("gRPC ответ: продукт id={}, зона={}, риск={}, свежесть={}, приоритет={}",
                response.getItemId(), storageZone, healthRisk, freshnessScore, priority);

        // Отправляем ответ и завершаем RPC
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * Определяет рекомендуемую зону хранения на основе категории и срока годности.
     */
    private String determineStorageZone(String category, int daysUntilExpiration) {
        if (daysUntilExpiration <= 0) {
            return "Trash"; // Просрочено
        }

        return switch (category != null ? category.toLowerCase() : "") {
            case "молочные продукты", "dairy" -> daysUntilExpiration <= 3 ? "Door" : "Back";
            case "мясное", "meat" -> "Freezer";
            case "овощи", "vegetables" -> "Crisper Drawer";
            case "фрукты", "fruits" -> "Crisper Drawer";
            case "напитки", "beverages" -> "Door";
            case "колбасы", "sausages" -> daysUntilExpiration <= 5 ? "Door" : "Back";
            default -> "Middle Shelf";
        };
    }

    /**
     * Оценивает риск для здоровья на основе оставшегося срока годности.
     */
    private String assessHealthRisk(int daysUntilExpiration) {
        if (daysUntilExpiration <= 0) return "CRITICAL";
        if (daysUntilExpiration <= 2) return "HIGH";
        if (daysUntilExpiration <= 5) return "MEDIUM";
        return "LOW";
    }

    /**
     * Вычисляет балл свежести (0.0 — 10.0).
     */
    private double calculateFreshnessScore(int daysUntilExpiration) {
        if (daysUntilExpiration <= 0) return 0.0;
        if (daysUntilExpiration <= 1) return 2.0;
        if (daysUntilExpiration <= 3) return 5.0;
        if (daysUntilExpiration <= 7) return 7.5;
        return 9.5;
    }

    /**
     * Определяет приоритет употребления.
     */
    private String determineConsumptionPriority(int daysUntilExpiration) {
        if (daysUntilExpiration <= 0) return "EXPIRED";
        if (daysUntilExpiration <= 2) return "IMMEDIATE";
        if (daysUntilExpiration <= 5) return "SOON";
        return "NORMAL";
    }
}