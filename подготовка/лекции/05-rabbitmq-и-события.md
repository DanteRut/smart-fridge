# Лекция 5. RabbitMQ и событийный контракт

> От синхронного мира (спросил — жду ответа) переходим к асинхронному (отправил — пошёл дальше). Брокер сообщений развязывает сервисы во времени и по нагрузке. В этой лекции: модель AMQP, типы обменников, Spring AMQP, и — самое экзаменационное — событийный контракт твоего проекта с sealed interface (❗Б2.3).

---

## 1. Зачем брокер сообщений

### 1.1 Два способа общения сервисов

| | Синхронно (REST, gRPC) | Асинхронно (брокер) |
|---|---|---|
| Кто инициирует | клиент | издатель события |
| Ждёт ли отправитель ответа | да | **нет** |
| Что если получатель упал | ошибка/таймаут у клиента | сообщение **ждёт в очереди** |
| Связанность | отправитель знает адрес получателя | отправитель знает только обменник |
| Нагрузка | пик бьёт прямо по получателю | очередь сглаживает пики |

**Брокер сообщений** (RabbitMQ) — посредник: издатель кладёт сообщение в брокер, потребитель забирает, когда готов. Сервисы не знают друг о друге и не обязаны быть живы одновременно — **временная развязка (decoupling)**.

Три выгоды развязки, одну из которых спрашивают всегда:
1. **Отказоустойчивость**: потребитель перезапустился — сообщения накопились и будут обработаны.
2. **Сглаживание пиков**: всплеск запросов не валит потребителя, очередь работает буфером.
3. **Расширяемость**: новый сервис-потребитель подписывается на событие **без изменения издателя**.

Цена (💡 называть самому): eventual consistency (данные расходятся на время), сложнее отладка (сообщения «летают»), нужен сам брокер как инфраструктура, и — лекция 6 — проблема гарантий доставки.

### 1.2 Паттерны

- **Publish/Subscribe** — издатель не знает, кто получит; подписчики получают копии.
- **Observer** — объект рассылает уведомления подписчикам; брокер — распределённая реализация той же идеи.

### 1.3 Запуск RabbitMQ

```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:management
# Management UI: http://localhost:15672  (guest/guest)
```

Порты: **5672** — AMQP (по нему ходят приложения), **15672** — веб-интерфейс управления. **vhost** — виртуальный хост внутри брокера: изолированное пространство сущностей (очереди/обменники) и прав; в одном брокере живут несмешивающиеся окружения (`/dev`, `/prod`).

---

## 2. Модель AMQP: producer → exchange → queue → consumer

```
producer ──(message + routing key)──► EXCHANGE ──(bindings)──► queue1 ──► consumer1
                                          │─────────────────► queue2 ──► consumer2
```

Ключевые сущности:

- **Producer** — отправитель. Он **никогда не пишет в очередь напрямую** — только в обменник.
- **Exchange (обменник)** — маршрутизатор. Получает сообщение + **routing key** и решает, в какие очереди его скопировать, по правилам **binding**.
- **Queue (очередь)** — буфер сообщений; FIFO; сообщения живут, пока потребитель их не заберёт и не подтвердит.
- **Binding** — связь «обменник ↔ очередь» с правилом (routing key / паттерн).
- **Consumer** — получатель; подписан на очередь.

💡 Почему producer не пишет в очередь: чтобы **издатель не знал потребителей**. Издатель знает обменник и routing key; появляется новый сервис — его очередь просто биндят к тому же обменнику, код издателя не меняется.

### 2.1 Типы обменников ❗

| Тип | Правило маршрутизации | Сценарий |
|---|---|---|
| **Direct** | routing key = точно ключу binding | точечная доставка: «ошибки — в errors-очередь» |
| **Fanout** | всем очередям с binding, ключ игнорируется | широковещание: все сервисы получают одно и то же (лекция 6, СОП 9) |
| **Topic** | паттерны: `*` = ровно одно слово, `#` = ноль и больше слов | гибкие подписки на срезы событий |
| **Headers** | по заголовкам сообщения | редкий, экзотика |

Topic-примеры (выучить наизусть ❗): для routing key `book.created.hardcover`:

| Паттерн | Совпадёт? |
|---|---|
| `book.created.*` | да |
| `book.*.hardcover` | да |
| `book.#` | да |
| `*.created.*` | да |
| `book.created` | **нет** (нужно ровно 3 слова) |

Сценарий из практики: сервис статистики слушает `book.#` (все события о книгах), сервис уведомлений — `*.created` (все события создания).

---

## 3. Producer на Spring Boot

### 3.1 Конфигурация (твой `RabbitMQConfig`)

```java
@Configuration
public class RabbitMQConfig {

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    @Bean
    public TopicExchange eventsExchange() {
        return ExchangeBuilder.topicExchange(RoutingKeys.EXCHANGE).durable(true).build();
    }
}
```

Что здесь важно:
- **MessageConverter** — сериализация объектов в JSON и обратно; producer и consumer обязаны договориться о формате (это тоже контракт!).
- **RabbitTemplate** — клиент для отправки (`convertAndSend(exchange, routingKey, object)`).
- **TopicExchange durable(true)** — обменник переживёт перезагрузку брокера (полный разбор durability — лекция 6).

### 3.2 Отправка

```java
rabbitTemplate.convertAndSend(RoutingKeys.EXCHANGE, RoutingKeys.PRODUCT_CREATED, envelope);
```

---

## 4. Consumer на Spring Boot

```java
@Component
public class AuditEventListener {

    @RabbitListener(queues = AuditRabbitConfig.AUDIT_QUEUE)
    public void onEvent(EventEnvelope envelope) {
        log.info("AUDIT received: routingKey={}, eventId={}, payload={}",
                envelope.metadata().routingKey(),
                envelope.metadata().eventId(),
                envelope.payload());
    }
}
```

`@RabbitListener` регистрирует потребителя: Spring сам подключится к брокеру, подпишется на очередь, будет вызывать метод на каждое сообщение. По умолчанию **подтверждение автоматическое** — и это опасно, почему — целая лекция 6.

Конфигурация consumer'а (твой `AuditRabbitConfig`): очередь + биндинг к обменнику:

```java
@Bean
public Queue auditQueue() {
    return QueueBuilder.durable(AUDIT_QUEUE).build();
}

@Bean
public Binding auditBinding(Queue auditQueue, TopicExchange eventsExchange) {
    // слушаем ВСЕ события
    return BindingBuilder.bind(auditQueue).to(eventsExchange).with("#");
}
```

---

## 5. Событийный контракт твоего проекта ❗❗

Это уровень архитектуры, а не библиотек: события — такие же контрактные артефакты, как REST DTO, и живут в отдельном модуле `smart-fridge-events-contract`.

### 5.1 RoutingKeys — словарь событий

```java
public final class RoutingKeys {
    public static final String EXCHANGE = "smartfridge.events";

    public static final String PRODUCT_CREATED = "product.created";
    public static final String PRODUCT_UPDATED = "product.updated";
    public static final String PRODUCT_DELETED = "product.deleted";

    public static final String ITEM_CREATED = "item.created";
    public static final String ITEM_UPDATED = "item.updated";
    public static final String ITEM_DELETED = "item.deleted";
}
```

Соглашение о ключах: `сущность.действие` — topic-обменник позволяет подписываться срезами (`product.#`, `*.created`, `#`).

### 5.2 EventEnvelope — конверт события

```java
public record EventEnvelope(Metadata metadata, Object payload) {
    public record Metadata(String eventId, Instant occurredAt,
                           String source, String routingKey) {}

    public static EventEnvelope wrap(Object payload, String source, String routingKey) {
        return new EventEnvelope(
            new Metadata(UUID.randomUUID().toString(), Instant.now(), source, routingKey),
            payload);
    }
}
```

Зачем конверт, а не «голое» событие:
- **`eventId` (UUID)** — уникальный идентификатор события: ключ **идемпотентности** потребителя (лекция 6) и сквозной трассировки;
- **`occurredAt`** — когда произошло (не «когда обработано»!);
- **`source`** — какой сервис издал;
- **`routingKey`** — событие самоописываемо даже в логах/DLQ.

💡 Envelope — это стандартный приём интеграции (у Spring Cloud Stream, Kafka-экосистем и т.д. конверты со своими метаданными везде). Вопрос «зачем eventId» — почти наверняка follow-up в любом билете про события.

### 5.3 Sealed interface: события как алгебраический тип ❗❗

```java
public sealed interface ProductEvent
        permits ProductEvent.Created, ProductEvent.Updated, ProductEvent.Deleted {

    record Created(Long id, String name, String category) implements ProductEvent {}
    record Updated(Long id, String name, String category) implements ProductEvent {}
    record Deleted(Long id, String name) implements ProductEvent {}
}
```

(`FridgeItemEvent` — аналогично: `Created/Updated/Deleted` с полями quantity, unit, expiresAt.)

**Что даёт sealed по сравнению с enum** — два разных ответа на «что дал такого, чего не дал бы enum»:

1. **Разные payload у разных вариантов.** Enum — фиксированный набор значений *одного* типа: у `DELETED` не было бы «своих» полей, пришлось бы городить общий класс с nullable-полями (`name`, `category` = null у Deleted) и надеяться, что никто не прочитает их у не того варианта. **Sealed + records = тип-сумма (ADT)**: каждый вариант — свой тип со своими полями, компилятор проверяет доступ к ним.
2. **Исчерпывающий switch по типам с деструктуризацией:**

```java
switch (event) {
    case ProductEvent.Created c    -> log.info("создан {} ({})", c.name(), c.category());
    case ProductEvent.Updated u    -> log.info("обновлён {}", u.name());
    case ProductEvent.Deleted d    -> log.info("удалён {}", d.name());
    // без default!
}
```

Поскольку `permits` перечисляет **всех наследников**, компилятор знает иерархию целиком: switch-выражение без `default` **не скомпилируется**, если появился новый тип. Эволюция контракта проверяется **на этапе сборки** — тот же ход, что контракт-Maven-модуль в REST (лекция 2) и кодогенерация в gRPC (лекция 7): ошибка несовместимости переносится из продакшена в компиляцию.

Плюс третий пункт: **закрытость** — `permits` запрещает кому-то извне подсунуть свой тип события.

### 5.4 Чем sealed слабее, чем кажется ❗ (продолжение Б2.3)

1. **`default` гасит всю магию.** Достаточно одной ветки `default -> игнорировать` — и компилятор больше **никогда** не напомнит о новых типах. Исчерпываемость — опциональная гарантия, дисциплина команды.
2. **Компилятор защищает только то, о чём знает на момент сборки.** Событие приезжает **по сети в рантайме**. Продюсер задеплоил раньше и шлёт новый тип `Enriched` → консьюмер со старым классом:
   - без `default`: switch-выражение бросит `MatchException` — сообщение упадёт, уйдёт в ретрай/DLQ, **проблему заметят** (громкий сбой);
   - с `default -> игнорировать`: **тихая потеря** событий — никто никогда не узнает.
3. **Централизация.** Все наследники обязаны жить в одном модуле с интерфейсом: сторонний сервис не расширит тип, не изменив общий контракт (это защита, но и бюрократия эволюции).

**Инженерный вывод (формулируй сам):** правильная ветка default — не «игнорировать», а `log.warn + метрика unknown_event_type + отправка в DLQ`. Новый тип тогда хотя бы заметен. Плюс: разворачивать консьюмеров раньше продюсеров (сначала все умеют читать новое — потом начинаем писать), версионировать контракт событий.

### 5.5 Издатель в проекте

```java
@Component
public class ProductEventPublisher {

    public void publishCreated(ProductResponse p) {
        send(RoutingKeys.PRODUCT_CREATED, new ProductEvent.Created(p.getId(), p.getName(), p.getCategory()));
    }

    private void send(String routingKey, ProductEvent event) {
        try {
            EventEnvelope env = EventEnvelope.wrap(event, SOURCE, routingKey);
            rabbitTemplate.convertAndSend(RoutingKeys.EXCHANGE, routingKey, env);
            log.info("Sent event {} [eventId={}]", routingKey, env.metadata().eventId());
        } catch (Exception e) {
            log.error("Failed to send event {}: {}", routingKey, e.getMessage());  // ← ❗
        }
    }
}
```

❗ Найди проблему сам (это Б1.4): `send` вызывается из `ProductService.create` **после** записи в storage. Если брокер недоступен — исключение ловится, пишется `log.error` — и **событие потеряно навсегда**, никто кроме лога не узнает. Это классический **dual write** (двойная запись без общей транзакции). Полное решение (Transactional Outbox) — лекция 6, раздел 5. Умея указать на эту строку в своём проекте и назвать решение, ты закрываешь билет Б1.4 «от своего кода».

### 5.6 Каскад в сервисе

```java
public void delete(Long id) {
    ProductResponse item = findById(id);       // 404 если нет
    itemService.deleteItemsByProductId(id);    // каскадное удаление единиц
    storage.products.remove(id);
    eventPublisher.publishDeleted(item);
}
```

Обрати внимание: сервис решает **бизнес-задачу** (каскад) и **издаёт события** — контроллер про это ничего не знает.

---

## 🔗 В проекте smart-fridge

- `smart-fridge-events-contract`: `RoutingKeys`, `EventEnvelope`, sealed `ProductEvent`/`FridgeItemEvent` — чистый контракт, без Spring (только jackson-annotations). ❗ Найди нюанс: модуль собирается `spring-boot-maven-plugin` с `<skip>true</skip>` — это библиотека, не приложение.
- `smart-fridge-service`: `RabbitMQConfig` (обменник, конвертер), `ProductEventPublisher`/`FridgeItemEventPublisher`, вызовы из `ProductService`/`FridgeItemService`.
- `smart-fridge-audit-service`: очередь `smartfridge.audit.queue`, биндинг `#`, listener с автоподтверждением. Точки роста, которые должен уметь назвать: нет manual ack, нет DLQ, нет дедупликации по eventId.

## ⚠️ Экзаменационные развилки

- **Б2.3** (sealed interface) → раздел 5.3–5.4 целиком, на примере собственного `ProductEvent`.
- **Б1.4** (dual write) → раздел 5.5 (уязвимость в твоём коде) → лекция 6, раздел 5 (решение).

## ✅ Самопроверка

1. Три выгоды развязки через брокер и одна цена.
2. Нарисуй модель AMQP и объясни, почему producer не пишет в очередь напрямую.
3. Типы обменников: кто куда маршрутизирует? Для `product.created` перечисли, какие topic-паттерны совпадут: `product.#`, `*.created`, `*.created.#`, `product.created`, `#.deleted`.
4. Зачем EventEnvelope четыре метаданных? Что будет делать потребитель с eventId?
5. Чем sealed+records лучше enum для событий? Два разных «что дал».
6. Опиши обе ветки поведения консьюмера при появлении `Enriched`: с default и без. Какой сбой громкий, какой тихий?
7. Покажи строку в `ProductEventPublisher`, где теряются события, и объясни, почему это не баг кода, а архитектурный пробел.

---
*Назад: [Лекция 4](04-graphql-java-dgs.md) · Дальше: [Лекция 6. Надёжная доставка](06-надежность-доставки.md)*
