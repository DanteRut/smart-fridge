package ru.smartfridge.graphql.types;

public record PageInfoGql(int pageNumber, int pageSize, int totalPages, boolean last) {}