package ru.smartfridge.graphql.types;

import ru.smartfridge.contract.dto.ProductResponse;
import java.util.List;

public record ProductConnectionGql(List<ProductResponse> content, PageInfoGql pageInfo, int totalElements) {}