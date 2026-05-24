package ru.smartfridge.events;

public final class RoutingKeys {
    public static final String EXCHANGE = "smartfridge.events";

    public static final String PRODUCT_CREATED = "product.created";
    public static final String PRODUCT_UPDATED = "product.updated";
    public static final String PRODUCT_DELETED = "product.deleted";

    public static final String ITEM_CREATED = "item.created";
    public static final String ITEM_UPDATED = "item.updated";
    public static final String ITEM_DELETED = "item.deleted";
    public static final String ITEM_ENRICHED = "item.enriched";

    public static final String ALL_PRODUCT_EVENTS = "product.*";
    public static final String ALL_ITEM_EVENTS = "product.*";
    public static final String ALL_EVENTS = "product.*";

    private RoutingKeys() {}
}