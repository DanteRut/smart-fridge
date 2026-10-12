# Лекция 4. GraphQL на Java: DGS (Netflix)

> Как теория из лекции 3 становится кодом. Всё на примере твоего smart-fridge: schema.graphqls, DataFetcher'ы, N+1 и DataLoader (❗Б4.2), обработка ошибок и protection-конфигурация. После этой лекции ты должен уметь открыть проект и объяснить каждую строку GraphQL-части.

---

## 1. Зачем фреймворк

GraphQL — **спецификация** (язык запросов + система типов), а не библиотека. Чтобы запустить его на сервере, нужен фреймворк, который: парсит и валидирует запросы, строит план исполнения, вызывает твои резолверы, собирает ответ.

В курсе используется **DGS — Domain Graph Service** от **Netflix**: фреймворк, встраивающий GraphQL в Spring Boot парой зависимостей. На нём построены продакшен-графы Netflix — и твой лабораторный проект.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.netflix.graphql.dgs</groupId>
            <artifactId>graphql-dgs-platform-dependencies</artifactId>
            <version>11.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependency>
    <groupId>com.netflix.graphql.dgs</groupId>
    <artifactId>graphql-dgs-spring-graphql-starter</artifactId>
</dependency>
<!-- Расширенные скаляры: DateTime, Date и другие -->
<dependency>
    <groupId>com.netflix.graphql.dgs</groupId>
    <artifactId>graphql-dgs-extended-scalars</artifactId>
</dependency>
```

---

## 2. Три этапа реализации

1. **Описать схему (SDL)** — `src/main/resources/schema/*.graphqls`
2. **Реализовать Data Fetcher'ы** — `@DgsComponent` + `@DgsQuery` / `@DgsMutation`
3. **Настроить Spring Boot** — минимум конфигурации

### 2.1 Конфигурация (твой `application.properties`)

```properties
spring.graphql.graphiql.enabled=true      # встроенная консоль GraphiQL
spring.graphql.path=/graphql              # сам endpoint
spring.graphql.schema.locations=classpath*:graphql/
dgs.graphql.schema-locations=classpath*:graphql/**/*.graphqls
spring.graphql.schema.introspection.enabled=true
```

**GraphiQL** — IDE запросов в браузере (`http://localhost:8080/graphiql`): автодополнение по схеме, история, мгновенное исполнение. Главный инструмент отладки GraphQL-части.

---

## 3. Схема: schema.graphqls

Подход **Schema First** (Contract First для GraphQL): сначала контракт — типы данных, которые может получить клиент; фреймворк проверяет, что резолверы покрывают схему, при старте.

Фрагмент твоей схемы:

```graphql
scalar Date
scalar DateTime

type Query {
  product(id: ID!): Product
  products(page: Int = 0, size: Int = 20): ProductConnection!
  item(id: ID!): FridgeItem
  items(productId: ID, page: Int = 0, size: Int = 20): FridgeItemConnection!
}

type Mutation {
  createProduct(input: CreateProductInput!): Product!
  updateProduct(id: ID!, input: UpdateProductInput!): Product!
  patchProduct(id: ID!, input: PatchProductInput!): Product!
  deleteProduct(id: ID!): Boolean!
  # + зеркально для items
}

type Product {
  id: ID!
  name: String!
  category: String
  itemsCount: Int
  items(page: Int = 0, size: Int = 20): FridgeItemConnection!   # связь-субзапрос
}

type FridgeItem {
  id: ID!
  product: Product!
  quantity: Int!
  unit: String!
  expiresAt: Date!
  createdAt: DateTime!
  updatedAt: DateTime
}

type PageInfo {
  pageNumber: Int!
  pageSize: Int!
  totalPages: Int!
  last: Boolean!
}

type ProductConnection {
  content: [Product!]!
  pageInfo: PageInfo!
  totalElements: Int!
}
# FridgeItemConnection — аналогично

input CreateProductInput { name: String!, category: String }
input UpdateProductInput { name: String!, category: String }
input PatchProductInput  { name: String,  category: String }   # все nullable = PATCH
# + inputs для items
```

💡 Три вещи, которые стоит уметь отметить на защите:

1. **Тройка input'ов на сущность** — это та же семантика POST/PUT/PATCH из REST, выраженная в SDL. И GraphQL здесь **выразительнее**: отсутствие аргумента и `null` различимы, так что «обнулить категорию» патчем возможно (в отличие от REST-части проекта — см. лекцию 2, раздел 4.2).
2. **Connection + PageInfo** — паттерн пагинации: эквивалент REST'овского `PagedResponse`, но собственными типами схемы.
3. **Кастомные скаляры** `Date`/`DateTime` — стандартный набор SDL из пяти скаляров не знает про даты; регистрация — в `DateTimeScalarRegistration`.

---

## 4. Data Fetchers = Resolvers

### 4.1 Концепция

**Data Fetcher отвечает за возврат данных для одного поля**; в терминах концепции GraphQL это и есть **resolver** — он «знает, откуда взять данные». Правило из курса: data fetcher обращается к **сервисам**, сервисы — к хранилищам. Никакой бизнес-логики в фетчерах.

### 4.2 Аннотации DGS ❗

| Аннотация | Что делает |
|---|---|
| `@DgsComponent` | регистрирует класс как GraphQL-компонент (специализированный `@Component`) |
| `@DgsQuery` | метод — resolver для `type Query` |
| `@DgsMutation` | метод — resolver для `type Mutation` |
| `@DgsSubscription` | метод — resolver для `type Subscription` |
| `@InputArgument` | маппинг аргумента из GraphQL-запроса |
| `@DgsData(parentType, field)` | resolver для **поля произвольного типа** (не Query/Mutation) |
| `@DgsDataLoader(name)` | объявление батч-загрузчика (см. N+1) |

### 4.3 Query-фетчеры (твой ProductDataFetcher)

```java
@DgsComponent
public class ProductDataFetcher {

    private final ProductService productService;
    public ProductDataFetcher(ProductService productService) { ... }

    @DgsQuery
    public ProductResponse product(@InputArgument Long id) {
        return productService.findById(id);          // бросит ResourceNotFoundException → errors
    }

    @DgsQuery
    public ProductConnectionGql products(@InputArgument Integer page,
                                         @InputArgument Integer size) {
        PagedResponse<ProductResponse> paged = productService.findAll(pageOr(page), sizeOr(size));
        return toConnection(paged);
    }
}
```

### 4.4 Mutation-фетчеры

Аналогично, `@DgsMutation`; input'ы схемы приходят как аргументы (`@InputArgument CreateProductInput input`).

### 4.5 Поле вложенного типа ❗ (ключевой файл проекта)

Поле `items` у типа `Product` — не Query, а поле объекта. Его резолвер помечается `@DgsData`:

```java
@DgsComponent
public class ProductItemsDataFetcher {

    private final FridgeItemService itemService;

    @DgsData(parentType = "Product", field = "items")
    public FridgeItemConnectionGql items(DgsDataFetchingEnvironment dfe,
                                         @InputArgument Integer page,
                                         @InputArgument Integer size) {
        ProductResponse product = dfe.getSource();   // «родительский» объект

        PagedResponse<FridgeItemResponse> paged =
                itemService.findAllByProductId(product.getId(), pageNum, pageSize);

        return new FridgeItemConnectionGql(paged.content(), new PageInfoGql(...), ...);
    }
}
```

**Механика исполнения (обязательно понимать):** DGS исполняет резолвер **на каждый объект каждого запрошенного поля**. `getSource()` возвращает родительский объект — так резолвер знает, чьи `items` доставать.

---

## 5. N+1 проблема ❗❗

### 5.1 Что происходит

```graphql
query {
  products {            # резолвер списка: 1 вызов
    name
    items {             # резолвер поля: вызывается на КАЖДЫЙ продукт!
      id
    }
  }
}
```

50 продуктов → 1 вызов `products` + **50 вызовов** `items` → в проде с БД это 51 поход в базу. В твоём проекте — 51 вызов `FridgeItemService`. Для in-memory-хранилища это дёшево, но архитектурная проблема та же самая: **резолверы независимы и ничего не знают друг о друге, батчей нет**.

Это та же N+1, о которой говорит лекция 3 для REST-клиентов («N+1 на уровне HTTP»), но теперь **внутри сервера** — между резолверами и хранилищем. REST от неё страдает на уровне клиента, GraphQL — на уровне резолверов. 💡 Универсальная формулировка: N+1 возникает везде, где «на каждый элемент списка делается отдельный запрос».

### 5.2 Решение: DataLoader (batch loading)

Идея: резолвер **не ходит за данными сам**, а ставит ключ в очередь. После разбора запроса DataLoader собирает все ключи и делает **один пакетный запрос**. Плюс — request-scoped кэш: один и тот же ключ в пределах запроса грузится один раз.

```java
@DgsDataLoader(name = "directors")
public class DirectorsDataLoader implements BatchLoader<String, Director> {
    private final DirectorService directorService;

    @Override
    public CompletionStage<List<Director>> load(List<String> directorIds) {
        return CompletableFuture.supplyAsync(
            () -> directorService.findByIds(directorIds));   // ОДИН запрос WHERE id IN (...)
    }
}
```

Резолвер поля вместо прямого похода в сервис:

```java
@DgsData(parentType = "Show", field = "director")
public CompletableFuture<Director> director(DgsDataFetchingEnvironment dfe) {
    DataLoader<String, Director> loader = dfe.getDataLoader("directors");
    Show show = dfe.getSource();
    if (show.directorId() == null) {
        return CompletableFuture.completedFuture(null);
    }
    return loader.load(show.directorId());   // ставим ключ в очередь, НЕ идём в БД
}
```

Что происходит на запрос `shows { director { name } }`:
1. 50 резолверов `director` вызвались и положили 50 ключей в очередь (быстро, без I/O).
2. GraphQL дождался окончания уровня, DataLoader батчит: `findByIds([50 id])` — **один** запрос.
3. Нагрузка: 1 + 1 вместо 1 + 50.

🔗 В твоём проекте DataLoader **не реализован** — `ProductItemsDataFetcher` ходит в сервис напрямую. Это честная «точка роста», которую ты сам должен уметь назвать: «сейчас N+1 у меня есть, лечится `@DgsDataLoader` по productId + `findByIds` в сервисе».

---

## 6. Обработка ошибок ❗

### 6.1 Философия

**В GraphQL ошибки возвращаются ВМЕСТЕ с данными** (в отличие от REST, где ошибка заменяет весь ответ). Ответ всегда 200; частичный успех возможен: `data` с тем, что получилось, + массив `errors` по тому, что нет.

```json
{
  "data": { "show": null },
  "errors": [ { "message": "Show not found: 999", "path": ["show"], "extensions": {...} } ]
}
```

### 6.2 Кастомное исключение и глобальный обработчик

```java
public class ShowNotFoundException extends RuntimeException {
    public ShowNotFoundException(String id) { super("Show not found: " + id); }
}
```

```java
@Component
public class CustomDataFetchingExceptionHandler implements DataFetcherExceptionHandler {
    @Override
    public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(
            DataFetcherExceptionHandlerParameters params) {
        // логирование, маппинг типов исключений на типизированные ошибки
        return CompletableFuture.completedFuture(
            DataFetcherExceptionHandlerResult.newResult().error(graphqlError).build());
    }
}
```

### 6.3 Твой проект

`GraphQLExceptionHandler` (в `smart-fridge-service/exception`) — живой пример:

```java
if (ex instanceof ResourceNotFoundException) {
    var error = TypedGraphQLError.newNotFoundBuilder()
            .message(ex.getMessage())
            .path(handlerParameters.getPath())
            .build();
    ...
}
// всё остальное → TypedGraphQLError.newInternalErrorBuilder()
//     .message("Внутренняя ошибка сервера") ...
```

💡 Сравни с `GlobalExceptionHandler` (REST) из лекции 2: это **два разных механизма ошибок для двух API на одном приложении** — REST: статусы + ErrorResponse через `@ControllerAdvice`; GraphQL: всегда 200 + TypedGraphQLError через `DataFetcherExceptionHandler`. Это аргумент в билете Б4.3: даже для двух эндпоинтов понадобились два параллельных механизма, а с ростом кодовой базы правила ошибок будут расходиться в двух мирах.

---

## 7. Защита: depth и complexity instrumentation ❗

Твой `GraphQLSecurityConfig`:

```java
@Bean
public Instrumentation maxQueryDepthInstrumentation() {
    // Ограничиваем максимальную глубину запроса: 20 уровней
    // (достаточно для GraphiQL Introspection, блокирует бесконечную рекурсию)
    return new MaxQueryDepthInstrumentation(20);
}

@Bean
public Instrumentation maxQueryComplexityInstrumentation() {
    // Ограничиваем максимальную сложность запроса: 200 единиц
    // (по умолчанию каждое запрашиваемое поле "весит" 1 единицу)
    return new MaxQueryComplexityInstrumentation(200);
}
```

**Instrumentation** — перехватчик конвейера исполнения graphql-java: до исполнения запроса анализирует его (глубину, стоимость) и может отклонить.

❗ Экзаменационная связка (Б2.2): comment в твоём же коде — «по умолчанию каждое поле весит 1 единицу» — это и есть слабость дефолтного complexity: он считает **количество полей**, а не **стоимость**. Поле `items`, за которым поход в сервис, весит столько же, сколько `name`. Для реальной защиты нужны кастомные веса полей (MutationTypeResolver/CustomComplexityCalculator в DGS) + ограничение лимитов пагинации + таймауты.

---

## 8. Полный цикл запроса (собери картинку)

```
Клиент (GraphiQL / фронтенд)
   │  POST /graphql  {"query": "...", "variables": {...}}
   ▼
DGS/Spring GraphQL endpoint
   │  1. parse → 2. validate по schema.graphqls
   │  3. Instrumentation: MaxQueryDepth(20), MaxQueryComplexity(200)
   │  4. построение плана: резолвер на каждое поле
   ▼
@DgsQuery/@DgsMutation фетчеры → ProductService/FridgeItemService → InMemoryStorage
   │  вложенные поля: @DgsData фетчеры (getSource() = родитель)
   │  [ DataLoader батчит ключи → один batch-запрос ]
   ▼
сборка ответа: {"data": {...}, "errors": [...]}  ← GraphQLExceptionHandler
```

---

## 🔗 В проекте smart-fridge — чеклист «объясни каждую строку»

- [ ] `schema.graphqls` — почему три input'а на сущность, зачем Connection/PageInfo
- [ ] `ProductDataFetcher` / `FridgeItemDataFetcher` — @DgsQuery, @InputArgument
- [ ] `ProductItemsDataFetcher` — @DgsData, getSource(), и где здесь N+1
- [ ] `GraphQLExceptionHandler` — TypedGraphQLError, почему всегда 200
- [ ] `GraphQLSecurityConfig` — что заблокирует и что НЕ заметит
- [ ] `DateTimeScalarRegistration` — зачем кастомный скаляр
- [ ] `graphql/types/*Gql` — ConnectionGql/PageInfoGql: мост между PagedResponse и схемой

## ⚠️ Экзаменационные развилки

- **Б4.2** (7 запросов к БД vs 2 в REST) → раздел 5.1: резолвер на поле на объект, без батчей; лечение — 5.2.
- **Б2.2** (depth 5, сервер лёг) → раздел 7 + лекция 3, раздел 7.2.
- **Б4.3** (GraphQL+REST в одном модуле) → раздел 6.3 (два механизма ошибок) + лекция 10, раздел 2.

## ✅ Самопроверка

1. Три этапа DGS. Почему подход называется Schema First?
2. Чем `@DgsData` отличается от `@DgsQuery`? Какой фетчер в проекте использует `@DgsData` и для какого поля?
3. Посчитай вызовы фетчеров для `products { items { id } }` при 20 продуктах. Сколько раз вызовется `FridgeItemService`?
4. Объясни механику DataLoader: что кладёт резолвер, когда происходит батч, что даёт request-scoped кэш?
5. Почему GraphQL-ответ всегда 200? Как клиент узнаёт об ошибке? Что такое частичный успех?
6. Что заблокирует твой `GraphQLSecurityConfig`, а что пропустит (пример запроса)?
7. Где в проекте два разных механизма обработки ошибок и почему их два?

---
*Назад: [Лекция 3](03-graphql-основы.md) · Дальше: [Лекция 5. RabbitMQ](05-rabbitmq-и-события.md)*
