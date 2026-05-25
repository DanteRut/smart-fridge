package ru.smartfridge.analytics.config;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import ru.smartfridge.analytics.service.FridgeItemAnalyticsServiceImpl;

import java.io.IOException;

@Component
public class GrpcServerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerLifecycle.class);

    @Value("${grpc.server.port:9090}")
    private int grpcPort;
    private Server server;
    private boolean running = false;

    @Override
    public void start() {
        try {
            server = ServerBuilder.forPort(grpcPort)
//                    .addService(new FridgeItemAnalyticsServiceImpl())
                    .build()
                    .start();
            running = true;
            log.info("gRPC-сервер запущен на порту {}", grpcPort);
            log.info("Сервис: FridgeItemAnalytics.AnalyzeItem()");
        } catch (IOException e) {
            throw new RuntimeException("Не удалось запустить gRPC-сервер на порту " + grpcPort, e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            log.info("Остановка gRPC-сервера!");
            server.shutdown();
            running = false;
            log.info("gRPC-сервер остановлен");
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
