package ru.smartfridge.graphql.types;

import ru.smartfridge.contract.dto.FridgeItemResponse;
import java.util.List;

public record FridgeItemConnectionGql(List<FridgeItemResponse> content, PageInfoGql pageInfo, int totalElements) {}