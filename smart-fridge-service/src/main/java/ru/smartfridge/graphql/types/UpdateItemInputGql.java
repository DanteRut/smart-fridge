package ru.smartfridge.graphql.types;

import java.time.LocalDate;

public record UpdateItemInputGql(Integer quantity, String unit, LocalDate expiresAt) {}