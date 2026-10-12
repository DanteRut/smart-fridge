# Лекция 2. REST-контракт: DTO, валидация, ошибки, HATEOAS

> Самая важная лекция курса: из неё выросли 5 вопросов прошлого экзамена. Главные идеи: (1) контракт — это артефакт, а не устная договорённость; (2) PUT и PATCH — разная семантика, и у JSON есть скрытая ловушка «null vs отсутствует»; (3) ошибки — часть контракта; (4) HATEOAS переносит знание API на сервер — и за это надо платить.

---

## 1. Зачем нужен контракт

### 1.1 «Это работает, но всё пойдёт не так» ❗

Типичный REST-контроллер, который пишут новички:

```java
@RestController
@RequestMapping("/authors")
public class AuthorController {
    @GetMapping("/{id}")
    public Author getAuthor(@PathVariable Long id) {
        return repository.findById(id).orElseThrow();
    }
}
```

Отдаёт `{ "id": 1, "firstName": "Лев", "lastName": "Толстой" }`. Работает. А потом:

1. **Поле переименовали.** Бэкенд переименовал `name` → `fullName`. Фронтенд упал в продакшене. Никто не знал, что это что-то сломает — **не было контракта**.
2. **PUT вместо PATCH.** Мобильное приложение отправило `PATCH {"bio": "новая биография"}`, а сервер ожидал все поля (PUT-семантика). `email`, `phone`, `birthDate` затёрлись нулями.
3. **Клиент жёстко зашил URL.** Сервис переехал с `/api/v1/authors` на `/api/v2/users` — 250 мест в клиентском коде правили руками.

**Вывод лектора: «Проблема не в коде. Проблема в отсутствии договорённостей».**

### 1.2 API-контракт

**API-контракт** — явное соглашение между сервером и клиентом:
- какие URL существуют;
- какие данные принимаются и возвращаются;
- какие ошибки возможны.

И самая сильная форма этого соглашения — **контракт как Maven-зависимость**: отдельный модуль с интерфейсами и DTO. Если сервер нарушил контракт — **проект не собирается**. Точка. Ошибка переносится с продакшена на этап компиляции — это тот же ход, что с sealed interface (лекция 5) и gRPC-кодогенерацией (лекция 7): **проверка контракта на этапе сборки**.

### 1.3 Структура проекта

```
demo-rest/                        агрегатор (packaging=pom)
├── pom.xml                       со списком модулей
├── books-api-contract/           МОДУЛЬ КОНТРАКТА (никакой логики!)
│   └── src/main/java/
│       ├── config/               BooksApiContractConfig (OpenAPI)
│       ├── dto/                  AuthorRequest, AuthorResponse, ...
│       ├── endpoints/            AuthorApi, BookApi (интерфейсы)
│       ├── exception/            ResourceNotFoundException, ...
│       └── validation/           @ValidIsbn, IsbnValidator
└── demo-rest/                    МОДУЛЬ-РЕАЛИЗАЦИЯ (Spring Boot app)
    └── src/main/java/
        ├── controllers/          AuthorController implements AuthorApi
        ├── assemblers/           AuthorModelAssembler
        ├── service/              AuthorService, BookService
        ├── storage/              InMemoryStorage
        └── exception/            GlobalExceptionHandler
```

Агрегатор собирает модули в правильном порядке:

```xml
<packaging>pom</packaging>
<modules>
    <module>books-api-contract</module>  <!-- порядок важен: зависимости раньше -->
    <module>demo-rest</module>
</modules>
```

**Maven Reactor** сам строит граф зависимостей и соблюдает порядок сборки: контракт соберётся раньше реализации. Запуск: `mvn spring-boot:run`.

💡 В твоём проекте ровно эта схема, только модулей четыре: `smart-fridge-api-contract` (REST-контракт), `smart-fridge-service` (реализация REST+GraphQL), `smart-fridge-events-contract` (контракт событий), `smart-fridge-audit-service` (потребитель событий).

---

## 2. Интерфейс как контракт

### 2.1 Контрактный интерфейс

Все знания о URL, методах, кодах ответов, документации и валидации живут **в одном месте** — интерфейсе контракта:

```java
@Tag(name = "Authors", description = "Управление авторами")
@RequestMapping(value = "/api/authors", produces = APPLICATION_JSON_VALUE)
public interface AuthorApi {

    @Operation(summary = "Получить автора по ID")
    @ApiResponse(responseCode = "200", description = "Автор найден")
    @ApiResponse(responseCode = "404", description = "Автор не найден")
    @GetMapping("/{id}")
    EntityModel<AuthorResponse> getAuthorById(@PathVariable Long id);

    @Operation(summary = "Создать автора")
    @ApiResponse(responseCode = "201", description = "Автор создан")
    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    ResponseEntity<EntityModel<AuthorResponse>> createAuthor(
            @Valid @RequestBody AuthorRequest request);
}
```

### 2.2 Реализация

```java
@RestController
public class AuthorController implements AuthorApi {
    // НЕТ @RequestMapping — унаследован из AuthorApi
    // НЕТ @GetMapping — унаследован из AuthorApi
    // НЕТ @Valid — унаследован из AuthorApi

    @Override
    public EntityModel<AuthorResponse> getAuthorById(Long id) {
        return authorModelAssembler.toModel(authorService.findById(id));
    }

    @Override
    public ResponseEntity<EntityModel<AuthorResponse>> createAuthor(AuthorRequest request) {
        AuthorResponse created = authorService.create(request);
        EntityModel<AuthorResponse> model = authorModelAssembler.toModel(created);
        return ResponseEntity.created(model.getRequiredLink("self").toUri()).body(model);
    }
}
```

**Почему аннотации работают через `implements`:** Spring читает аннотации маппинга не только с класса, но и с реализуемых интерфейсов. Поэтому контроллер содержит **только бизнес-логику** — он тонкий.

❗ Экзаменационная связь (Б2.4): интерфейс контракта **зацеплен за Spring MVC** (`@GetMapping` поймёт только Spring). Если сервер переписывают на Quarkus/Micronaut — маппинги не сработают, а клиенты, использующие тот же модуль, тянут Spring-зависимости транзитивно. Разбор стратегии миграции: лекция 10, раздел 2.

---

## 3. Request DTO: records + Bean Validation

### 3.1 Record для запроса

```java
public record AuthorRequest(
    @NotBlank(message = "Имя не может быть пустым") @Size(max = 100)
    String firstName,

    @NotBlank(message = "Фамилия не может быть пустой") @Size(max = 100)
    String lastName,

    @Email(message = "Некорректный формат email")
    String email,

    @Past(message = "Дата рождения должна быть в прошлом")
    LocalDate birthDate,

    @Size(max = 2000)
    String bio
) {}
```

Почему **record** идеален для request: неизменяемый, компактный, без Lombok.

### 3.2 Механика @Valid ❗

Аннотации валидации срабатывают **автоматически при наличии `@Valid` в методе контракта**. Без `@Valid` аннотации декоративны — проверок не будет! Цепочка:

```
запрос → десериализация в AuthorRequest → Bean Validation (из-за @Valid)
       → падение? → 400 Bad Request + список ошибок полей (лекция ошибках ниже)
       → успех → контроллер → сервис
```

### 3.3 Кастомная валидация

Стандартных аннотаций не хватает — создаём свою:

```java
@Documented
@Constraint(validatedBy = IsbnValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIsbn {
    String message() default "Некорректный ISBN. Допустимы ISBN-10 и ISBN-13";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// Использование: @NotBlank @ValidIsbn String isbn
```

---

## 4. PUT vs PATCH и ловушка «null vs отсутствует» ❗❗

### 4.1 Семантика

- **PUT** = «замени ресурс целиком этим». Все поля обязательны; отсутствующие — затираются (null'ами).
- **PATCH** = «измени только переданное». Отсутствующее — не трогаем.

Один DTO не может выразить оба смысла:

```java
// POST / PUT — поля обязательны
public record AuthorRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    @Email    String email,        // необязательно, но при PUT затрётся null
    @Past     LocalDate birthDate
) {}

// PATCH — все поля необязательны, null = «не трогать» на беке
public record PatchAuthorRequest(
    @Size(max = 100) String firstName,   // null = не менять
    @Size(max = 100) String lastName,    // null = не менять
    @Email           String email,       // null = не менять
    @Past            LocalDate birthDate // null = не менять
) {}
```

Реализация PATCH на сервере:

```java
String newFirstName = request.firstName() != null
        ? request.firstName()          // пришло новое — берём
        : existing.getFirstName();     // не пришло — оставляем старое
```

### 4.2 Ловушка ❗ (это и есть экзаменационная бомба)

**В JSON «поле отсутствует» и «поле = null» — разные вещи. Но после десериализации в Java-объект различие теряется: оба превращаются в `null`.**

Следствия:
- PATCH с `{"bio": null}` неотличим от PATCH без поля `bio` вообще → «обнулить поле через PATCH нельзя» (сервер решит «не трогать»).
- Клиент, который хочет очистить одно поле, но не знает остальных, **не может этого выразить** ни через PUT (не знает остальных значений), ни через PATCH (null игнорируется).

### 4.3 Три решения на уровне контракта ❗

**Решение 1. JSON Merge Patch — RFC 7386.**
Стандарт задаёт семантику: **члена нет = не менять; член есть со значением null = очистить; есть со значением = заменить**.

```json
PATCH /api/authors/1
Content-Type: application/merge-patch+json

{ "bio": null, "email": "new@mail.ru" }
```
— bio очистить, email заменить, остальное не трогать. Клиенту — обычный JSON, максимально просто. Серверу — сложнее: дефолтный десериализатор стирает различие, нужен `JsonNullable<String>` (обёртка с тремя состояниями: absent / null / value), парсинг в `JsonNode` или кастомный десериализатор.

**Решение 2. JSON Patch — RFC 6902.**
Тело — массив явных операций:

```json
PATCH /api/authors/1
Content-Type: application/json-patch+json

[
  {"op": "replace", "path": "/bio",   "value": null},
  {"op": "replace", "path": "/email", "value": "new@mail.ru"},
  {"op": "test",    "path": "/state", "value": "draft"}
]
```

Операции: `add / replace / remove / test / copy / move`. Плюсы: намерение выражено явно (неоднозначности нет в принципе), набор применяется атомарно, готовые библиотеки. Минусы: клиенту тяжелее (строить операции и JSON-Pointer пути), громоздко для «поменять одно поле».

**Решение 3. Своё соглашение.**
Например, `{"bio": "текст", "reset": ["email", "phone"]}` — список полей на сброс. Реализация тривиальна, но это свой велосипед: сам документируешь, сам валидируешь (поле из `reset` не должно приходить со значением), кодогенераторы клиентов его не понимают.

**Сравнение** (умей воспроизводить по памяти):

| | Merge Patch (7386) | JSON Patch (6902) | Своё соглашение |
|---|---|---|---|
| Стандарт | да | да | нет |
| Простота клиенту | +++ | + | ++ |
| Простота серверу | + (нужен JsonNullable) | ++ (библиотеки) | +++ |
| Атомарность | да | да | да |
| Выразительность | поля | поля+массивы+test | что придумал |

🔗 В проекте: `PatchProductRequest`/`PatchItemInputGql` — все поля nullable, «null = не трогать» (см. `ProductService.patch`). **Честное ограничение: обнулить `category` через PATCH нельзя** — это компромисс, который ты должен уметь назвать и предложить fix (Merge Patch или JSON Patch).

---

## 5. Response DTO и обработка ошибок

### 5.1 Response DTO — это класс, не record ❗

```java
@Getter
@Builder
@EqualsAndHashCode(callSuper = false)   // _links не участвуют в equals
@JsonInclude(JsonInclude.Include.NON_NULL)  // null-поля не видны в JSON
@Relation(collectionRelation = "authors", itemRelation = "author")
@Schema(description = "Информация об авторе")
public class AuthorResponse extends RepresentationModel<AuthorResponse> {
    @Schema(description = "Уникальный идентификатор", example = "1")
    private final Long id;
    private final String firstName;
    private final String lastName;
    private final String fullName;     // вычисляемое
    private final Integer booksCount;
}
```

Три вещи, которые здесь спрашивают на экзамене:
1. **Класс, а не record**, потому что нужно наследоваться от `RepresentationModel<T>` — базового класса Spring HATEOAS, добавляющего хранилище `_links`.
2. **`@JsonInclude(NON_NULL)`** — null-поля молча выбрасываются из JSON. Поле, забытое в билдере → null → поля в ответе нет, **ни ошибки, ни лога**. (Это прямой ответ на билет «поле author есть в БД, нет в JSON» — Б4.1.)
3. **`@Relation`** — без него Spring назвал бы коллекцию в `_embedded` уродливо: `authorResponseList`. С ним — `authors`.

### 5.2 Ошибки — часть контракта: RFC 7807 Problem Details ❗❗

«Возвращать `{"error": "not found"}` — непрофессионально. Есть стандарт — RFC 7807».

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    int    status,        // 404 — дублируем код В ТЕЛЕ
    String type,          // "https://api.example.com/problems/resource-not-found"
    String title,         // "Ресурс не найден"
    String detail,        // "Author with id=99 not found"
    String instance,      // "/api/authors/99"
    Instant timestamp,
    List<FieldError> fieldErrors   // только для 400, иначе null
) {
    public record FieldError(String field, Object rejectedValue, String message) {}
}
```

Пример ответа 400:

```json
{
  "status": 400,
  "title": "Ошибка валидации",
  "instance": "/api/books",
  "fieldErrors": [
    {"field": "isbn",  "rejectedValue": "123", "message": "Некорректный ISBN"},
    {"field": "title", "rejectedValue": "",    "message": "Название не может быть пустым"}
  ]
}
```

❗ Почему это важно для билета Б1.1: поле `status` **дублирует HTTP-код внутри тела**. Семантика ошибки живёт в прикладном слое и переживёт любого посредника, переписавшего статус (шлюз, прокси, GraphQL-обёртка `{data: null, errors: [...]}` всегда с 200). Полному разбору посвящён `разбор-билетов.md` → Б1.1.

### 5.3 @ControllerAdvice — централизованная обработка ❗

```java
@ControllerAdvice   // перехватывает исключения из ВСЕХ контроллеров
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex,
                                                        HttpServletRequest req) {
        return ResponseEntity.status(NOT_FOUND).body(
            new ErrorResponse(404, BASE + "resource-not-found", "Ресурс не найден",
                ex.getMessage(), req.getRequestURI(), Instant.now(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest req) {
        List<ErrorResponse.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getRejectedValue(),
                                                    fe.getDefaultMessage()))
            .toList();
        return ResponseEntity.status(BAD_REQUEST).body(
            new ErrorResponse(400, BASE + "validation-error", "Ошибка валидации",
                null, req.getRequestURI(), Instant.now(), errors));
    }

    @ExceptionHandler(Exception.class)   // ловит всё остальное
    public ResponseEntity<ErrorResponse> handleAll(Exception ex, HttpServletRequest req) {
        // log.error("Unexpected error", ex); — логируем, не теряй стектрейс!
        return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(
            new ErrorResponse(500, BASE + "internal-error", "Внутренняя ошибка",
                "Обратитесь к поддержке", req.getRequestURI(), Instant.now(), null));
    }
}
```

**Принцип: контроллер никогда не обрабатывает ошибки сам — он только бросает исключения.** Это separation of concerns: бизнес-код чистый, политика ошибок — в одном месте.

💡 Обрати внимание на `FieldError(field, rejectedValue, message)`: клиент маппит `field` на инпут формы **строковым совпадением**. Переименовали `isbn` → `productIdentifier` — сервер честно отдаёт новое имя, клиент молча перестаёт показывать подсказки. Разбор миграции: лекция 10, раздел 3 (Б3.3).

---

## 6. HATEOAS

### 6.1 Что это

**HATEOAS (Hypermedia as the Engine of Application State)** — ограничение REST: клиент взаимодействует с приложением **через гипермедиа, которую сервер вкладывает в ответы**. API становится **self-discoverable**: вместо «клиент заранее знает все эндпоинты» — сервер подсказывает доступные действия из текущего состояния.

Без HATEOAS: фронтенд вручную строит `?page=0`, `?page=1`. Переименовали параметры пагинации — фронт сломался.
С HATEOAS: API отдаёт готовые ссылки `"next": "...", "last": "..."` — фронт просто привязывает их к кнопкам.

### 6.2 Три ключевых класса Spring HATEOAS ❗

| Класс | Что делает | JSON-эквивалент |
|---|---|---|
| `RepresentationModel<T>` | базовый класс, хранит `_links` | `"_"links: {...}` |
| `EntityModel<T>` | обёртка одного объекта + ссылки | `{ ...данные, "_links": {...} }` |
| `PagedModel<EntityModel<T>>` | страница объектов: `_embedded`, `_links` навигации, `page` метаданные | см. ниже |

```json
{
  "_embedded": { "authors": [ {...}, {...} ] },
  "_links": {
    "self":  { "href": "...?page=1&size=20" },
    "first": { "href": "...?page=0&size=20" },
    "prev":  { "href": "...?page=0&size=20" },
    "next":  { "href": "...?page=2&size=20" },
    "last":  { "href": "...?page=4&size=20" }
  },
  "page": { "size": 20, "totalElements": 100, "totalPages": 5, "number": 1 }
}
```

### 6.3 Паттерн Assembler ❗

Логика построения ссылок — в одном месте, а не размазана по контроллерам:

```java
@Component
public class AuthorModelAssembler
        implements RepresentationModelAssembler<AuthorResponse, EntityModel<AuthorResponse>> {

    @Override
    public EntityModel<AuthorResponse> toModel(AuthorResponse author) {
        return EntityModel.of(author,
            linkTo(methodOn(AuthorController.class)
                .getAuthorById(author.getId())).withSelfRel(),
            linkTo(methodOn(BookController.class)
                .getAllBooks(author.getId(), null, null, null, 0, 20)).withRel("books"),
            linkTo(methodOn(AuthorController.class)
                .getAllAuthors(0, 20)).withRel("collection")
        );
    }
}
```

**Магия `linkTo(methodOn(...))`:** это НЕ вызов метода. Spring HATEOAS создаёт **прокси**, читает аннотации маппинга (`@GetMapping("/{id}")`) и строит URL из них. Переименовал маппинг в контракте — **все ссылки обновились автоматически**. Типобезопасно: сигнатура метода поменялась — не соберётся.

🔗 В проекте: `ProductModelAssembler`, `FridgeItemModelAssembler`, `RootController` (точка входа со ссылками на коллекции).

### 6.4 Цена HATEOAS для серверной команды ❗❗ (Б3.2)

HATEOAS **не убирает сложность — переносит её на сервер**:

1. **Ссылки и имена rel стали публичным контрактом.** Клиент различает `rel` и ходит по `href` → переименовать rel или убрать ссылку = такое же ломающее изменение, как переименовать URL.
2. **Матрица «состояние × действие».** Сервер обязан в каждом состоянии каждого ресурса отдавать правильный набор ссылок (у отклонённой рукописи нет «опубликовать»). Это машина состояний, которую надо спроектировать и покрыть тестами.
3. **Каждая ссылка всегда корректна и абсолютна** — за балансировщиком придётся разбираться с `X-Forwarded-Host`, иначе клиент получит `localhost:8080` в `href`.
4. **Забыл ссылку = действия не существует.** У «правильного» HATEOAS-клиента нет способа догадаться.

**Формула для ответа:** «Захардкоженный клиент падает, когда сервер меняет URL; HATEOAS-клиент падает, когда сервер неправильно построил ссылку или забыл rel. Сложность никуда не делась — она переехала на сервер».

Плюс конфликт с UI (Б4.4): ссылка появляется/исчезает **по состоянию в рантайме**, а экран проектируется заранее. Решения (метаданные `allowed/reason` у ссылки, реестр rel, BFF) — в лекции 10, раздел 4.

---

## 7. Пагинация ❗

### 7.1 Почему в контракте НЕ Spring Data Page<T>

```java
// В контракте — простой record, никаких зависимостей Spring Data:
public record PagedResponse<T>(
    List<T> content, int pageNumber, int pageSize,
    long totalElements, int totalPages, boolean last
) {}
```

**Причина (слайд-цитата): «Потому что тогда контракт зависел бы от Spring Data — а это лишняя зависимость для клиентов».** Это образец мышления про контракт: тащим только то, что нужно потребителю. (И это зерно билета Б2.4 про переезд фреймворка.)

### 7.2 Конвертация на сервере

```java
public PagedModel<EntityModel<AuthorResponse>> getAllAuthors(int page, int size) {
    // Сервис возвращает наш простой PagedResponse
    PagedResponse<AuthorResponse> paged = authorService.findAll(page, size);

    // Оборачиваем в Spring Data Page<T>
    Page<AuthorResponse> springPage = new PageImpl<>(
        paged.content(),
        PageRequest.of(paged.pageNumber(), paged.pageSize()),
        paged.totalElements());

    // PagedResourcesAssembler строит PagedModel и добавляет ссылки навигации
    return pagedAuthorsAssembler.toModel(springPage, authorModelAssembler);
}
```

🔗 В проекте: `PagedResponse` в `smart-fridge-api-contract/dto`, ручная пагинация потоком в `ProductService.findAll` (`subList` от `page*size`), конвертация в `PagedModel` — в контроллерах и ассемблерах.

---

## 8. OpenAPI — документация в коде

Аннотации Swagger прямо на контрактном интерфейсе (видел в 2.1):

```java
@Operation(
    summary = "Создать нового автора",
    description = "Создаёт автора и возвращает его с HATEOAS-ссылками. " +
                  "Возвращает Location-заголовок с URL нового ресурса.")
@ApiResponse(responseCode = "201", description = "Автор успешно создан",
    content = @Content(schema = @Schema(implementation = AuthorResponse.class)))
@ApiResponse(responseCode = "400", description = "Ошибка валидации",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
```

Из аннотаций springdoc генерирует Swagger UI — живой интерактивный справочник и песочница. Документация живёт рядом с контрактом → не устаревает (стареет только если забыли обновить аннотации, и это видно при код-ревью).

---

## 9. Слои приложения — резюме архитектуры реализации

| Слой | Что делает | Не делает |
|---|---|---|
| Controller (`implements XxxApi`) | принимает запрос, отдаёт ответ | не содержит бизнес-логики |
| Assembler | DTO ↔ EntityModel + ссылки | не решает бизнес-вопросов |
| Service (`@Service`) | бизнес-логика, валидация состояний, события | ничего не знает про HTTP |
| Storage | хранение (in-memory / БД) | не знает про бизнес-правила |
| GlobalExceptionHandler | все ошибки одним местом | — |

**Dependency Injection:** вместо `BookService service = new BookService()` внутри контроллера — конструктор:

```java
@Autowired
public BookController(BookService bookService) {
    this.bookService = bookService; // Spring сам найдёт бин и передаст
}
```

Смысл: слабая связанность (контроллер не знает, как создаётся сервис), тестируемость (подкладываешь мок), единый жизненный цикл бинов.

---

## 🔗 В проекте smart-fridge

- `smart-fridge-api-contract` — весь раздел 1–3, 5, 7: `ProductApi`, `FridgeItemApi`, `ProductRequest/PatchProductRequest/ProductResponse`, `ErrorResponse`, `PagedResponse`.
- `smart-fridge-service` — реализация: контроллеры `implements ProductApi`, два ассемблера, `GlobalExceptionHandler`, `RootController`, in-memory `ConcurrentHashMap` вместо БД.
- Отличие от учебного demo-rest: в контракте появился **суб-ресурс** `GET /api/products/{id}/items` и каскадное удаление (`deleteProduct` удаляет и items) — найди в `ProductService.delete`.

## ⚠️ Экзаменационные развилки

- **Б1.1** (шлюз/200 OK) → раздел 5.2 + лекция 1 (посредники).
- **Б1.3** (обнулить поле) → раздел 4 целиком, таблица трёх решений.
- **Б3.2** (цена HATEOAS) → раздел 6.4.
- **Б3.3** (переименование isbn) → разделы 3.2 (валидация), 5.3 (FieldError) + лекция 10, раздел 3.
- **Б4.1** (поле пропало из JSON) → раздел 5.1 пункт 2 (`@JsonInclude(NON_NULL)`) + «DTO ≠ БД».
- **Б2.4** (контракт со Spring при переезде на Quarkus) → раздел 2.2 + 7.1 + лекция 10, раздел 2.

## ✅ Самопроверка

1. Три сценария из «всё пойдёт не так» — назови и объясни, почему контракт решает каждый.
2. Почему Response DTO — класс, а Request DTO — record? Два разных ответа на два разных «почему».
3. Что произойдёт с валидацией, если убрать `@Valid` из метода интерфейса?
4. Объясни ловушку «null vs отсутствует» и восстанови таблицу трёх решений PATCH.
5. Напиши по памяти структуру `ErrorResponse` и объясни, зачем `status` дублирует код в теле.
6. Что делает `linkTo(methodOn(...))` внутри и почему это защищает от рефакторинга URL?
7. Зачем в контракте `PagedResponse` вместо `Page<T>`? Какой принцип контракта это иллюстрирует?
8. Сформулируй «цену HATEOAS» четырьмя пунктами.
9. Почему контроллер «тонкий» и никогда не ловит исключения сам?

---
*Назад: [Лекция 1](01-архитектура-и-rest.md) · Дальше: [Лекция 3. GraphQL](03-graphql-основы.md)*
