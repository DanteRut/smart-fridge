# Лекция 7. gRPC и Protocol Buffers

> Если REST — «ресурсы через HTTP-глаголы», то gRPC — «вызываем методы удалённого сервиса, как локальные». Скорость за счёт HTTP/2 и бинарного protobuf, строгость за счёт IDL и кодогенерации. Экзаменационное ядро: правила совместимости protobuf — что происходит с новыми полями (❗Б3.1) и что ломает совместимость при неизменных номерах (❗Б3.4).

---

## 1. RPC: удалённый вызов процедур

### 1.1 Идея

**RPC (Remote Procedure Call)** — класс технологий, позволяющих программам вызывать функции **в другом адресном пространстве** (как правило, на удалённых машинах), чтобы это выглядело **как обычный локальный вызов**. Цель — прозрачность: вызывающему не нужно знать, что процедура на другой машине.

Характерные черты **локального** вызова:
- **асимметричность** — одна сторона инициатор;
- **синхронность** — вызывающая приостанавливается до возврата.

### 1.2 Проблемы удалённого вызова (почему это сложнее, чем кажется)

- Разные машины = **разные адресные пространства**: нельзя передать указатель; параметры и результаты **копируются по сети** (сериализация).
- RPC обязан использовать нижележащую систему связи (TCP/UDP), но **это не должно быть видно** ни в сигнатурах, ни в телах процедур.
- Машины могут быть неодинаковыми (endianness, размеры типов) — нужен единый формат.

### 1.3 Stub — заглушка ❗

Как достигается прозрачность: вместо локальной процедуры вызывается **её двойник — stub (заглушка)**:

```
клиент → client stub (marshal параметров в сообщение) → сеть →
       → server stub (unmarshal, вызывает настоящую функцию) → сервер
результат идёт обратно тем же путём
```

- **Клиентская заглушка** вызывается с тем же списком параметров, но вместо выполнения упаковывает параметры в сообщение и отправляет на сервер, затем ждёт ответное сообщение.
- **Серверный процесс** ждёт вызов, извлекает параметры, выполняет вычисления, отправляет ответ клиенту.
- Упаковка/распаковка = **сериализация (marshalling)**.

Исторические RPC-технологии (узнавать в лицо): DCE/RPC, **DCOM** (Microsoft), CORBA, **Java RMI**, **SOAP**, XML-RPC, JSON-RPC. Все умерли или ниши — а вот идея stub'ов и IDL триумфально вернулась в gRPC.

---

## 2. Проблематика REST-мира и ответ gRPC

### 2.1 Что болит ❗

- Много сервисов = много API → много разных интеграций.
- Каждый раз пишется **обвязка**: HTTP-клиент, client factory, договорённости о сериализации, форматы дат, кейсинг, DTO для транспорта — «много возможностей для ошибок».
- **REST — не Contract First**: целостность контрактов не контролируется. Swagger генерирует документацию **по коду** — «а хотелось бы наоборот: код по документации».
- JSON/XML громоздки — трафик и парсинг.

### 2.2 Что предлагает gRPC ❗

**gRPC** — кроссплатформенный RPC-фреймворк от Google: простой, отлично подходит для микросервисов и API, со встроенной поддержкой балансировки, трассировки, аутентификации и health-check'ов, клиентские библиотеки на ~10 языках.

1. **Contract First.** Сначала строго описываем пересылку в protobuf-файле, кладём в репозиторий — «с этого момента он зафиксирован». (Code First существует, но только когда весь бэкенд на одном стеке, например .NET.)
2. **Строгая типизация** из коробки + автоматическая валидация.
3. **Кодогенерация клиента и сервера.** Описал контракт — штатный инструмент при билде создаёт заглушки. Следствие: **нельзя сбилдить проект, не поменяв клиент и сервер, если поменялся контракт** — несовместимость ловится на компиляции (тот же принцип, что контракт-Maven-модуль в лекции 2 и sealed в лекции 5).
4. **Сериализация из коробки** — protobuf: компактный и быстрый.
5. **Стриминг**: сервер → клиент, клиент → сервер, двунаправленный (duplex).
6. Производительность — за счёт **HTTP/2** (мультиплексирование многих запросов в одном соединении, бинарные фреймы) и **Protocol Buffers** (бинарный формат).

💡 Триумвират принципов курса: «ошибка несовместимости должна падать на сборке» — контрактный Maven-модуль (REST), sealed switch (события), кодогенерация из .proto (gRPC). Умей провести эту параллель — она производит впечатление системного понимания.

---

## 3. Protocol Buffers (proto3)

### 3.1 Базовый синтаксис

```proto
syntax = "proto3";

option java_multiple_files = true;
option java_package = "edu.rutmiit.demo.grpcservicedemo";
option java_outer_classname = "LibraryProtos";

import "google/protobuf/empty.proto";

package library;

service LibraryService {
  rpc AllBooks (google.protobuf.Empty) returns (GetBooksResponse) {}
  rpc GetBook  (GetBookRequest)        returns (GetBookResponse)  {}
  rpc CreateBook (CreateBookRequest)   returns (google.protobuf.Empty) {}
  rpc UpdateBook (UpdateBookRequest)   returns (google.protobuf.Empty) {}
  rpc DeleteBook (DeleteBookRequest)   returns (google.protobuf.Empty) {}
}

message Book {
  string isbn = 1;
  string title = 2;
  string author = 3;
  string publisher = 4;
  int32  year = 5;

  enum BookGenre {
    FICTION = 0;        // первая константа ОБЯЗАНА быть 0
    NON_FICTION = 1;
    SCIENCE = 2;
    TECHNOLOGY = 3;
  }
  BookGenre genre = 6;
}

message GetBooksResponse { repeated Book books = 1; }
message GetBookRequest  { string isbn = 1; }
message GetBookResponse { Book book = 1; }
message CreateBookRequest { Book book = 1; }
message UpdateBookRequest { string isbn = 1; Book book = 2; }
message DeleteBookRequest { string isbn = 1; }
```

Словарь:
- **service** — контракт: набор rpc-методов;
- **message** — структура данных (как DTO, но с **номерами полей**);
- **repeated** — список;
- `google.protobuf.Empty` — «ничего не вернуть/не принять»;
- **enum** начинается с **0** — это требование proto3, и оно не случайно (0 = значение по умолчанию, см. 3.3).

### 3.2 Номера полей и wire format ❗❗

**Номер поля — это его идентичность в бинарном формате.** На проводе сообщение — последовательность пар **(номер поля, wire type) + значение**. **Имена полей в байтах не передаются.**

Wire types (типы кодирования):

| Wire type | Кодирует | Как определяется размер |
|---|---|---|
| 0 — varint | int32/int64/uint/bool/enum | переменная длина |
| 1 — 64-bit | fixed64, double | 8 байт |
| 2 — length-delimited | string, bytes, message, repeated | длина + байты |
| 5 — 32-bit | fixed32, float | 4 байта |

Декодер читает пару (номер, wire type) и смотрит в свою схему:
- номер **знаком** → читает значение;
- номер **незнаком** → **пропускает** его по wire type (знает, сколько байт занять) и идёт дальше. Поле как бы не существует.

### 3.3 Совместимость в обе стороны ❗❗ (Б3.1)

Добавили новое поле (новый номер). Два направления:

**Старый клиент читает новое сообщение:**
- декодер не находит новый номер в своей (старой) схеме → **пропускает неизвестное поле** по его wire type и продолжает разбор. Без ошибки, без падения. (В proto3 c версии 3.5 неизвестные поля даже **сохраняются** и пере-сериализуются при пересылке — данные не теряются.)
- Для старого клиента нового поля просто не существует.

**Старый клиент шлёт сообщение без нового поля → новый сервер:**
- сервер получает **значение по умолчанию**: числовые → `0`; `string` → `""`; `bool` → `false`; `enum` → значение с номером 0; message → «не установлено».

**Ловушка proto3:** «0» неотличим от «не задано». Если поле «количество на складе» = 0, получатель не знает — ноль ли это или поле не пришло. Лечение: модификатор **`optional`** (explicit presence) — тогда у поля появляется `hasField()`/`has*()`, «не задано» становится различимо.

### 3.4 Что ломает совместимость при неизменных номерах ❗❗ (Б3.4)

Гарантия protobuf **структурная** («одинаковый номер + одинаковый wire type = совместимо»), а не смысловая. Что ломает её, когда номера не тронули:

| Действие | Что произойдёт |
|---|---|
| **Сменить тип с другим wire type** (`string isbn = 1` → `int64 isbn = 1`) | декодер встречает неожиданный wire type у знакомого номера → **ошибка декодирования** или мусор |
| **Сменить тип внутри одного wire type** (`int32` → `int64` → `enum`, все varint) | **декодируется без ошибок**, но значения искажаются: усечение, отрицательные числа, «мусорные» enum — тихий сбой, хуже падения |
| `singular` ↔ `repeated` | одно значение станет списком из одного / наоборот |
| message ↔ скаляр | ломается структура |
| **Удалить поле без `reserved` и занять номер новым типом** | старый клиент интерпретирует **новые байты по своей старой схеме** — тихий мусор |
| Переименовать поле | бинарно безопасно (имён на проводе нет!), но ломает JSON-маппинг и код |

**Правила инженера:**
- номера полей не менять и не переиспользовать; удалённые — объявлять **`reserved`**:
```proto
message Person {
  reserved 2, 15, 9 to 11;
  reserved "email", "old_name";
  string name = 1;
}
```
- не менять типы полей;
- ломающие изменения схем ловить в CI (buf breaking и подобное).

**Вывод-формула:** protobuf защищает формат, а не смысл. Совместимость — **дисциплина владельца контракта**, а не свойство библиотеки.

---

## 4. Кодогенерация

### 4.1 Что генерируется

Из .proto генерируются: DTO (со Builder'ами), клиентские заглушки (stub'ы), серверные базовые классы, сериализация/десериализация — **весь транспортный слой**. Поддержка: Java, Kotlin, C#, Go, Python, PHP, Ruby + мобильные платформы.

### 4.2 Maven-настройка (из практики СОП 8)

```xml
<dependency>
    <groupId>io.grpc</groupId> <artifactId>grpc-stub</artifactId>
</dependency>
<dependency>
    <groupId>io.grpc</groupId> <artifactId>grpc-protobuf</artifactId>
</dependency>
<dependency>
    <groupId>io.grpc</groupId> <artifactId>grpc-netty-shaded</artifactId>
</dependency>

<!-- определение платформы (os) для protoc -->
<extensions>
    <extension>
        <groupId>kr.motd.maven</groupId> <artifactId>os-maven-plugin</artifactId>
    </extension>
</extensions>

<!-- генерация при билде -->
<plugin>
    <groupId>org.xolstice.maven.plugins</groupId>
    <artifactId>protobuf-maven-plugin</artifactId>
    <configuration>
        <protocArtifact>com.google.protobuf:protoc:${protobuf.version}:exe:${os.detected.classifier}</protocArtifact>
        <pluginId>grpc-java</pluginId>
        <pluginArtifact>io.grpc:protoc-gen-grpc-java:${grpc.version}:exe:${os.detected.classifier}</pluginArtifact>
    </configuration>
    <executions>
        <execution>
            <goals> <goal>compile</goal> <goal>compile-custom</goal> </goals>
        </execution>
    </executions>
</plugin>
```

Gradle-вариант из лекции: `./gradlew generateProto`, затем `./gradlew build`. Spring-интеграция: стартер `net.devh:grpc-server-spring-boot-starter`.

---

## 5. Сервер

### 5.1 Реализация сервиса

Наследуемся от сгенерированного базового класса, переопределяем методы, пишем в **StreamObserver**:

```java
public class LibraryService extends LibraryServiceGrpc.LibraryServiceImplBase {

    private final Map<String, Book> books = new HashMap<>();

    @Override
    public void getBook(GetBookRequest request, StreamObserver<GetBookResponse> responseObserver) {
        Book book = books.get(request.getIsbn());
        if (book == null) {
            responseObserver.onError(new StatusException(Status.NOT_FOUND));   // ❗ код ошибки
        } else {
            responseObserver.onNext(GetBookResponse.newBuilder().setBook(book).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void createBook(CreateBookRequest request, StreamObserver<Empty> responseObserver) {
        String isbn = request.getBook().getIsbn();
        if (books.containsKey(isbn)) {
            responseObserver.onError(new StatusException(Status.ALREADY_EXISTS));
        } else {
            books.put(isbn, request.getBook());
            responseObserver.onNext(Empty.newBuilder().build());
        }
        responseObserver.onCompleted();
    }
}
```

Три ключевых метода observer'а: **`onNext(сообщение)`** (отдать значение), **`onCompleted()`** (закончить), **`onError(StatusException)`** (ошибка с кодом).

### 5.2 Коды ошибок gRPC (аналоги HTTP-статусов)

| Код | Смысл | HTTP-аналог |
|---|---|---|
| `OK` | успех | 200 |
| `INVALID_ARGUMENT` | кривые параметры | 400 |
| `NOT_FOUND` | нет сущности | 404 |
| `ALREADY_EXISTS` | конфликт создания | 409 |
| `PERMISSION_DENIED` | нет прав | 403 |
| `UNAVAILABLE` | сервис недоступен | 503 |
| `INTERNAL` | внутренняя ошибка | 500 |

💡 Обрати внимание: опять «модель ошибок прикладного уровня» — статус-коды gRPC едут **внутри** протокола, независимо от HTTP-кодов underneath. Параллель с RFC 7807 (лекция 2) и GraphQL `errors` (лекция 4) напрашивается сама.

### 5.3 Запуск сервера (голый вариант из лекции)

```java
Server server = ServerBuilder.forPort(8080)
        .addService(new LibraryService())
        .build();
server.start();
System.out.println("Server started on port 8080");
server.awaitTermination();
```

---

## 6. Клиент и типы вызовов

### 6.1 Клиент

```java
ManagedChannel channel = ManagedChannelBuilder
        .forAddress("localhost", 8080)
        .usePlaintext()                     // без TLS — только для разработки!
        .build();

LibraryServiceGrpc.LibraryServiceBlockingStub stub =
        LibraryServiceGrpc.newBlockingStub(channel);

GetBookResponse response = stub.getBook(
        GetBookRequest.newBuilder().setIsbn("978-0134685991").build());
```

`BlockingStub` — синхронные вызовы; есть асинхронные варианты (`FutureStub`, `StreamObserver`-based). Клиент использует **сгенерированный из того же .proto** код — вот почему контракт не расходится.

### 6.2 Четыре типа вызовов ❗

| Тип | Направление | Сценарий |
|---|---|---|
| **Unary** | запрос → ответ | обычный CRUD (всё выше — unary) |
| **Server streaming** | сервер шлёт поток ответов | котировки, логи, подписка на обновления |
| **Client streaming** | клиент шлёт поток запросов | загрузка батчей/файлов, телеметрия |
| **Bidirectional streaming** | оба потока независимо | чаты, живые синхронизации, «пинг-понг» |

Unary — синхронный по духу (клиент блокируется до ответа); стриминги — асинхронные, через StreamObserver с обеих сторон.

---

## 7. Interceptors и балансировка

**Interceptors (перехватчики)** — аналог middleware: перехватывают вызовы для логирования, метрик, аутентификации. Встраиваются и на сервере, и на клиенте.

**Балансировка нагрузки**: выполняется **на стороне клиента** — «round-robin» по списку адресов, полученному от LB-сервера; на LB можно настроить более сложные политики выдачи списка. (Свяжи с Consul — лекция 9: registry как раз такой источник живых адресов.)

---

## 8. Критика gRPC ❗ (уметь называть — экзамен любит честность)

Из лекции:
- сложный runtime — при багах внутри него «вообще непонятно, что делать»;
- проблемы с load balancing;
- streaming практически никому не нужен, а его наличие усложняет рантайм и создаёт баги;
- **бинарный протокол мешает дебагу** (в tcpdump не почитаешь), инструменты сырые и сложные;
- не поддерживает HTTP/1.1 — **балансировщики/прокси, не умеющие HTTP/2, становятся проблемой**;
- чаще всего время соединения и передачи — не бутылочное горлышко, и выигрыш не так велик, как в маркетинге.

💡 Сбалансированный вывод: gRPC — для **внутреннего** межсервисного трафика (кластер, свой контроль инфраструктуры, важна скорость и типизация); REST — для **внешних** публичных API (клиенты-браузеры, кэширование CDN, читаемость). Ровно так и делают, и ровно это практиковалось в СОП 9: gRPC внутрь кластера + события наружу.

---

## 🔗 В проекте smart-fridge

gRPC в проекте нет — и это надо уметь объяснять: «в моём проекте межсервисное взаимодействие асинхронное (события в audit-service), синхронных межсервисных вызовов нет. Если бы появился analytics-service (как в СОП 9), синхронный расчёт я бы сделал по gRPC: analytics-api-contract с .proto, кодогенерация при билде, unary-методы Calculate/History».

Что назвать по построению: отдельный .proto → генерация stub'ов → `@GrpcService`-класс (net.devh стартер) → код ошибки `NOT_FOUND` вместо 404.

## ⚠️ Экзаменационные развилки

- **Б3.1**: раздел 3.3 — пропускаем неизвестные поля / дефолты; не забудь ловушку «0 vs не задано».
- **Б3.4**: раздел 3.4 — таблица ломающих действий + формула «защита структурная, не смысловая; совместимость — дисциплина».
- Follow-up: «что такое stub» (1.3), «почему enum с нуля» (3.1/3.3), «как gRPC ловит несовместимость» (2.2 п.3).

## ✅ Самопроверка

1. Что такое RPC и stub? Кто маршалит параметры?
2. Почему «REST — не Contract First»? Что генерирует Swagger и чего хотелось бы наоборот?
3. Нарисуй wire format: что именно едет по сети и почему имена полей не передаются?
4. Два сценария Б3.1 — опиши оба по памяти, включая дефолты по типам.
5. Три действия, ломающие совместимость при неизменных номерах. Какое из них самое коварное и почему?
6. Зачем `reserved`? Что будет без него?
7. Четыре типа вызовов gRPC и по одному сценарию на каждый.
8. Назови три претензии к gRPC из критики. Когда gRPC — неправильный выбор?

---
*Назад: [Лекция 6](06-надежность-доставки.md) · Дальше: [Лекция 8. WebSocket](08-websocket-и-realtime.md)*
