package ru.smartfridge.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GrpcAnalyticsServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(GrpcAnalyticsServerApplication.class, args);
    }
}