package ru.smartfridge.contract.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;

@OpenAPIDefinition(
        info = @Info(
                title = "Smart Fridge API",
                version = "1.0.0",
                description = "REST API для управления продуктами и содержимым умного холодильника."
        ),
        servers = @Server(url = "http://localhost:8080", description = "Local development")
)
public final class SmartFridgeApiContractConfig {
    private SmartFridgeApiContractConfig() {}
}