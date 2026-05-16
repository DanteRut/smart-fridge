package ru.smartfridge.events;

public sealed interface ProductEvent permits ProductEvent.Created, ProductEvent.Updated, ProductEvent.Deleted {

    record Created(Long id, String name, String category) implements ProductEvent {}
    record Updated(Long id, String name, String category) implements ProductEvent {}
    record Deleted(Long id, String name) implements ProductEvent {}
}