# ЗОНА 2: GraphQL — полная справочная шпаргалка (20–40 мин)

> Формат: таблицы + «как ответить» + 🔗 проект. Якорные фразы выделены.

---

## 1. Зачем GraphQL: какие боли REST он решает

- **Over-fetching**: `GET /api/posts/1/likes` отдаёт полные объекты пользователей, когда нужны только `name` и `avatar` → на мобильных: трафик, парсинг, батарея.
- **Under-fetching**: под каждый экран новые эндпоинты (`?include=likes`, `?include=comments`) → **количество эндпоинтов растёт экспоненциально**; при этом данные размазаны (PostgreSQL/Redis/MongoDB/ML-сервис) → клиент сам делает N запросов = **«N+1 на уровне HTTP»** (три запроса по 800 мс = 2.5 с).
- **Решение Facebook (2012, опенсорс с 2015, GraphQL Foundation)**: вместо множества «глупых» эндпоинтов — **один «умный» endpoint**, который принимает описание нужной структуры и **придаёт данным форму, какую запросил клиент**. Форма ответа = форма запроса.

## 2. Архитектура

| Компонент | Роль |
|---|---|
| **Schema** | контракт: какие типы доступны, какие операции возможны |
| **Resolvers** | функции, «знающие», откуда взять данные каждого поля |
| **Data Sources** | любые: SQL/NoSQL, REST API, gRPC, файлы, кэш |

Ключ к «агрегации микросервисов»: резолвер одного поля может ходить в PostgreSQL, соседнего — в Redis; **схема соединяет их в один ответ для клиента**. Это и есть **BFF-паттерн** из списка «когда использовать GraphQL».

## 3. SDL (Schema Definition Language)

**Скаляры**: `Int`, `Float`, `String`, `Boolean`, `ID` (сериализуется строкой). Кастомные — `Date`, `DateTime` (нужна регистрация — у нас `DateTimeScalarRegistration` + graphql-dgs-extended-scalars).

**Модификаторы**: `String!` — non-null; `[String]` — список; `[String!]!` — обязательный список обязательных. Читай слева направо: `!` после типа — про элемент, после `]` — про список.

**Типы и связи**:
```graphql
type Product {
  id: ID!
  name: String!
  category: String
  items(page: Int = 0, size: Int = 20): FridgeItemConnection!   # связь-субзапрос
}
```
**Input** — структурированные аргументы (мутации/фильтры): `input CreateProductInput { name: String!, category: String }`. **Enum** — фиксированный набор. **Interface/Union** — полиморфизм (`union SearchResult = User | Post`).

💡 В нашей схеме **три input'а на сущность** (Create/Update/Patch) — та же семантика POST/PUT/PATCH из REST. В GraphQL, в отличие от REST+Jackson, **absent ≠ null различимы** → обнулить категорию патчем можно.

## 4. Три операции

| | Семантика | Исполнение |
|---|---|---|
| **query** | чтение (R) | поля **параллельно** — читать безопасно |
| **mutation** | изменение (CUD) | **строго последовательно** — мутация 2 не начнётся до завершения 1 (целостность) |
| **subscription** | push-подписка | сервер шлёт данные при каждом событии, пока клиент слушает; транспорт **WebSocket/SSE** |

```graphql
type Query { product(id: ID!): Product  products(page: Int, size: Int): ProductConnection! }
type Mutation { createProduct(input: CreateProductInput!): Product! }
type Subscription { commentAdded(postId: ID!): Comment! }
```

**Частичный успех**: упало одно поле → в `data` то, что успело, + массив `errors` (в REST ошибка заменяет весь ответ).

## 5. Аргументы, variables, fragments, директивы

- **Аргументы** — на любом поле любого уровня: `products(first: 5, orderBy: {...})`.
- **Variables**:
```graphql
query GetUser($userId: ID!, $limit: Int = 10) { user(id: $userId) { posts(first: $limit) { title } } }
```
variables едут **отдельным JSON**: `{"userId":"42","limit":5}`.

**Билет «зачем variables, если можно захардкодить» — три технические причины:**
1. **Query становится константой → сервер кэширует parse+validate** (дорогие шаги) и исполняет с разными variables — аналог **prepared statements в SQL**.
2. **Persisted Queries**: клиент шлёт **хэш** запроса из белого списка — возможно, только потому, что текст постоянен. Минус трафик + произвольные запросы заблокированы (безопасность).
3. **Типобезопасная передача значений**: без конкатенации строк, без экранирования внутри JSON-строки, без «инъекций структуры» (подставленный в строку текст мог бы изменить сам запрос; через variables — просто значение типа `ID!`).

- **Fragments** — DRY: `fragment UserBasicInfo on User { id name email }`, вставка `...UserBasicInfo`.
- **Директивы**: `@include(if:)`, `@skip(if:)`, `@deprecated(reason:)` (эволюция схемы!), `@defer` (медленные поля вторым пакетом), `@stream` (элементы списков по мере готовности).

## 6. Ошибки: модель GraphQL

- **Всегда HTTP 200**; ошибки — в теле: `{"data": {...частично...}, "errors": [{"message", "path", "extensions"}]}`.
- Обработчик — `DataFetcherExceptionHandler` → `TypedGraphQLError` (типизированные ошибки: NOT_FOUND, INTERNAL…).
- **Сравнение с REST** (готовая фраза): *«REST: ошибка заменяет ответ целиком (статус). GraphQL: ошибки едут вместе с данными — модель прикладного уровня, независимая от транспорта. Поэтому шлюз `{data:null, errors}` — это GraphQL-модель, и клиент, проверяющий только HTTP-код, за неё зацепится»*.

🔗 В проекте: `GraphQLExceptionHandler implements DataFetcherExceptionHandler` — `ResourceNotFoundException` → `TypedGraphQLError.newNotFoundBuilder()`, остальное → internal; рядом REST-овский `GlobalExceptionHandler` — **два параллельных механизма ошибок** (аргумент для В13).

## 7. DGS (Netflix) на Java

**Подход Schema First**: (1) схема `src/main/resources/schema/*.graphqls` → (2) фетчеры `@DgsComponent` → (3) конфиг. Фреймворк при старте сверяет фетчеры со схемой.

```properties
spring.graphql.graphiql.enabled=true      # GraphiQL: IDE запросов в браузере
spring.graphql.path=/graphql
dgs.graphql.schema-locations=classpath*:graphql/**/*.graphqls
spring.graphql.schema.introspection.enabled=true
```

**Аннотации:**

| Аннотация | Значение |
|---|---|
| `@DgsComponent` | регистрирует класс (спец. `@Component`) |
| `@DgsQuery` / `@DgsMutation` / `@DgsSubscription` | resolver для Query/Mutation/Subscription |
| `@InputArgument` | маппинг аргумента запроса |
| `@DgsData(parentType, field)` | resolver **поля типа** (не Query) |
| `@DgsDataLoader(name)` | батч-загрузчик (N+1) |

**Поле вложенного типа** (`items` у Product):
```java
@DgsData(parentType = "Product", field = "items")
public FridgeItemConnectionGql items(DgsDataFetchingEnvironment dfe, @InputArgument Integer page, ...) {
    ProductResponse product = dfe.getSource();   // родительский объект
    return toConnection(itemService.findAllByProductId(product.getId(), ...));
}
```
**Механика**: фетчер вызывается **на каждый объект каждого запрошенного поля**; `getSource()` = родитель. Фетчер → сервис → хранилище (без бизнес-логики в фетчере).

🔗 Схема: `Query` (product/products/item/items), `Mutation` (полный CRUD ×2), `Product.items` — связь, `Connection`+`PageInfo` (пагинация по-graphql'овски = аналог REST `PagedResponse`).

## 8. N+1 (билет «7 запросов к БД вместо 2»)

**Название: N+1 (вложенный).**

**Механика**: резолвер на каждое поле каждого объекта, по умолчанию **без батчей**. Запрос `{ book { author { books { content } } } }`:
`book` → 1, `author` → 1, `books` → 1, `content` → **по одному на каждую книгу автора** (N=4) = **7**.

**Почему REST двумя**: REST-эндпоинты проектировались под экраны — внутри JOIN'ы и пакетные выборки. GraphQL отдал клиенту право собирать форму → потерял контроль над числом походов за данными. **Универсальная формула**: N+1 возникает везде, где *на каждый элемент списка — отдельный запрос* (у REST — на уровне HTTP-клиента, у GraphQL — резолверов).

**Лечение — DataLoader (batch loading)**: резолвер **не ходит за данными**, кладёт ключ в очередь; после разбора уровня DataLoader делает **один** пакетный запрос + **request-scoped кэш** (тот же ключ в том же запросе не грузится):
```java
@DgsDataLoader(name = "directors")
public class DirectorsDataLoader implements BatchLoader<String, Director> {
    public CompletionStage<List<Director>> load(List<String> ids) {
        return CompletableFuture.supplyAsync(() -> directorService.findByIds(ids)); // WHERE id IN (...)
    }
}
// резолвер поля: dfe.getDataLoader("directors").load(show.directorId())
```
1+N вызовов → 1+1.

**Follow-up**: как найти в проде — трассировка (TraceId/SpanId, Zipkin: россыпь однотипных спанов), latency-метрики. Когда терпимо — дешёвый источник (кэш/in-memory), малые N. DataLoader не панацея: тяжёлые связи лучше JOIN'ом в резолвере списка.

🔗 **Проект**: `ProductItemsDataFetcher` ходит в `FridgeItemService` **на каждый продукт** → `products { items { id } }` при 50 = **51 вызов** — наш живой N+1; лечение — `@DgsDataLoader` по productId + `findByIds`.

## 9. Безопасность GraphQL (билеты depth)

GraphQL **уязвимее REST из-за гибкости запросов** → явные защиты:

1. **Depth Limiting** — против бесконечной рекурсии `user→posts→author→posts→…` (DoS). У нас `MaxQueryDepthInstrumentation(20)`.
2. **Cost/Complexity Analysis** — каждому полю **вес**; спискам множитель по лимиту; сумма считается **до исполнения**; cost > threshold → отказ.

**Билет «depth 5 — сервер лёг»**: глубина меряет **вертикаль**, нагрузку создают **данные и резолверы**. Четыре способа при глубине 5:
- огромный список: `products(first: 100000)` — глубина 2, стоимость огромна;
- **дорогой резолвер**: поле за ним ходит в ML-сервис — «один уровень», стоит секунды;
- **алиасы**: `a: book(id:"1") b: book(id:"2") … z: book(id:"26")` — 26 подзапросов на одном уровне, глубина не растёт;
- **introspection**: выгрузка всей схемы — неглубоко, тяжело.

→ Depth как единственная метрика мерит форму, не стоимость. Нужны **веса**: скаляр ≈1, поле с походом в БД ≈5–10, внешний сервис ≈20–50, списки = вес × лимит; + лимиты пагинации, таймауты, introspection off в проде.

3. **Persisted Queries** — белый список хэшей; произвольные запросы заблокированы (в связке с variables — раздел 5).
4. **Introspection** в проде отключать/ограничивать.

🔗 **Проект**: `MaxQueryDepthInstrumentation(20)` + `MaxQueryComplexityInstrumentation(200)`; комментарий из своего кода: *«по умолчанию каждое поле весит 1»* — дефолтный complexity считает **количество полей**, не стоимость (`items` весит как `name`) → нужны кастомные веса.

## 10. GraphQL vs REST — когда что

| GraphQL | REST |
|---|---|
| гибкие структуры для фронтенда | простые CRUD с предсказуемой формой |
| мобильные с ограниченным каналом | **публичные API** (чужие разработчики, простота, лимиты на эндпоинт) |
| агрегация микросервисов (**BFF**) | **HTTP-кэширование критично (CDN/прокси)** |
| глубоко связанные модели, быстрая итерация фронтенда | опыт команды |

**Кэш — три уровня (частый follow-up)**: сетевой HTTP-кэш (браузер/прокси/**CDN**, по URL+Cache-Control — у GraphQL **не работает**: всё POST на один `/graphql`); клиентский кэш приложения (Apollo — в памяти, не разделяется, сервер не разгружает); persisted queries (кэш **текста**, не ответов). «Есть кэш» ≠ «есть сетевой кэш».

## 11. Билет: «GraphQL-схему — в тот же модуль, что и REST-контроллеры»

**Опасность: два контракта с разными моделями на одних ресурсах.**
1. **Ошибки**: REST — статусы + `@ControllerAdvice`; GraphQL — всегда 200 + `DataFetcherExceptionHandler`. У нас **уже два разных класса** — на десятках эндпоинтов правила расползутся.
2. **Кэш**: REST — по URL (ETag/CDN); GraphQL — нет. **Защита**: лимиты на эндпоинт vs depth/complexity/APQ.
3. **Ресурсы**: один пул потоков/JVM — деградация одного валит другой.
4. **Релизы**: один артефакт — нельзя выкатить по отдельности.

**Сценарий 1 (нагрузка)**: мобильные клиенты массово бьют в GraphQL тяжёлыми запросами → пул/CPU заняты → **B2B-клиенты REST ловят таймауты**, хотя их API не трогали. Масштабировать только GraphQL нельзя (один модуль); резать общие сервисы посреди эксплуатации — больно.

**Сценарий 2 (скорости изменений)**: фронт итерирует схему еженедельно; REST v1 заморожен для партнёров на квартал. Один модуль = один релизный цикл: правка схемы тянет деплой REST (регрессии, тесты, откат — тоже обоих), либо команды блокируют друг друга. У двух API **естественно разные скорости изменений** — общее проживание её убивает.

**Как правильно**: резолверы+схема и REST — **в разных модулях** (при разной нагрузке — разные сервисы), общие только доменные сервисы. *Признать*: у нас «упрощённая версия» — два API в одном приложении, но код физически разведён по пакетам; дальше разделил бы модулями.

## 12. 🔗 Проект — GraphQL-хуки

- `schema.graphqls` целиком: Query/Mutation, Product↔FridgeItem, Connection/PageInfo, три input'а на сущность, скаляры Date/DateTime.
- `ProductDataFetcher`/`FridgeItemDataFetcher` (`@DgsQuery`), `ProductItemsDataFetcher` (`@DgsData` + `getSource()` — **наш N+1**).
- `GraphQLExceptionHandler` (`TypedGraphQLError`) vs `GlobalExceptionHandler` — два механизма ошибок.
- `GraphQLSecurityConfig`: depth 20 / complexity 200, дефолт «1 за поле» — слабость названа в нашем же комментарии.
- Persisted queries и subscriptions не реализованы — назвать как точки роста (`@DgsSubscription` + WebSocket).
