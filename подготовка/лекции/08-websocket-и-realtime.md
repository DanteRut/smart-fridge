# Лекция 8. WebSocket и real-time

> До сих пор сервер всегда «молчал, пока не спросят». Но чат, уведомление, тикер котировок требуют обратного: сервер сам шлёт данные в браузер. Эта лекция — история того, как HTTP «хакали» для real-time (AJAX → Comet → long polling → SSE) и что получилось в итоге: WebSocket. Плюс две Spring-реализации из практик: чистый WS и Notification Service (RabbitMQ → WS).

---

## 1. Проблема: HTTP не для real-time

**HTTP создан для режима «запрос-ответ»**: клиент спросил — сервер ответил. Для приложений реального времени (высокочастотная или постоянная связь, мгновенная реакция на изменения) он не оптимизирован.

Первые веб-приложения real-time (2000-е) обеспечивались **«хакингом» HTTP**. Посмотрим на эволюцию — она регулярно спрашивается как «раскрой цепочку AJAX → WebSocket».

### 1.1 AJAX (2005) — асинхронный запрос из фона

**AJAX (Asynchronous JavaScript and XML)** — асинхронный обмен с сервером в фоне и обновление частей страницы **без полной перезагрузки (постбэка)**. Технологии: HTML/CSS для представления, **DOM** для динамического отображения, XML/JSON для данных, **XMLHttpRequest (XHR)** для асинхронности, JavaScript для связки.

Модель: между пользователем и сервером появляется **движок AJAX** — клиент загружает его при старте, и дальше он **опрашивает сервер** от имени клиента и обновляет DOM.

Ограничение: это **polling** — клиент сам спрашивает «есть что новое?» снова и снова. Много пустых запросов, задержка = интервал опроса.

### 1.2 Comet (2006) — пусть сервер шлёт первым

**Comet** — модель, где **сервер** передаёт данные браузеру: асинхронность через **долговременные HTTP-соединения** — сервер шлёт обновления, когда они есть, без явного запроса. Начал с Gmail, стал стандартом де-факто для интерактивных приложений, породил две техники:

### 1.3 Long polling ❗

Более эффективная форма опроса: сервер **держит соединение открытым как можно дольше** и отвечает только когда данные появились **или** сработал таймаут. Получив ответ, клиент **сразу отправляет новый запрос**. Реализуется на том же XMLHttpRequest.

```
клиент: «есть данные?» ────────► сервер молчит... молчит... ──► «вот данные!»
клиент: «есть данные?» ────────► ...
```

Плюс против polling: данные приходят почти сразу. Минус: каждое сообщение = новое HTTP-соединение с заголовками; сервер держит много открытых соединений.

### 1.4 HTTP streaming

Сервер **непрерывно шлёт данные в одно HTTP-соединение**, которое остаётся открытым неограниченно долго: при каждом обновлении — фрагмент ответа, соединение закрывается только по явному указанию. Реализация — **chunked transfer** из HTTP/1.1: сервер шлёт фрагменты, разделённые новой строкой, клиент обрабатывает на лету.

Минус: ломает инфраструктуру — прокси/балансировщики рассчитаны на «запрос-ответ» и буферизуют/рубят длинные ответы.

### 1.5 Server-Sent Events (SSE)

**SSE** — стандартизованная (W3C) технология серверного push: **однонаправленный поток сервер → клиент** через JS API **EventSource**. Проще WS (это HTTP), автоматический реконнект, но **только сервер пишет**.

### 1.6 Сводная таблица ❗

| Техника | Направление | Транспорт | Проблема |
|---|---|---|---|
| Polling (AJAX) | клиент спрашивает | HTTP | пустые запросы, задержка |
| Long polling | сервер при подходящем моменте | HTTP (долгие запросы) | соединение на каждое сообщение |
| HTTP streaming | сервер непрерывно | HTTP (chunked) | прокси ломают поток |
| SSE | сервер → клиент постоянно | HTTP | только одно направление |
| **WebSocket** | **оба направления, полнодуплекс** | **свой протокол поверх TCP** | нужен свой сервер/инфраструктура |

### 1.7 Ключевые ограничения AJAX/Comet (формулировка из лекции) ❗

- **отсутствие гарантий порядка сообщений**;
- **задержки** (медленно коннектимся + последовательно передаём);
- **отсутствие двунаправленного потока**.

Причина одна: всё это — надстройки над протоколом, созданным для «запрос-ответ».

---

## 2. WebSocket

### 2.1 Что это ❗

**WebSocket — технология двунаправленной, полнодуплексной связи между клиентом и сервером через одно постоянное сокетное соединение.** Коммуникационный слой, близкий к «чистому» TCP, но с абстракциями для веба (безопасность, origin, согласование).

Ключевые факты:
- **соединение начинается как HTTP-запрос** (рукопожатие), а дальше WebSocket и HTTP «фундаментально различаются»;
- HTTP ориентирован на **запросы**, WebSocket — на **события**;
- сервер может писать клиенту **без запроса**.

### 2.2 Преимущества ❗

- **Производительность:** нет нового соединения на каждый запрос; сообщения крошечные — **без HTTP-заголовков** (сравни с long polling, где каждый запрос таит сотни байт заголовков).
- **Расширяемость:** конструкция позволяет подпротоколы и расширения.
- **Быстрая реакция:** событийная модель, данные без запроса клиента.

### 2.3 Рукопожатие (handshake) ❗❗

Установление соединения = **открытие рукопожатия**: обмен HTTP/1.1 запросом и ответом.

1. **Клиент всегда инициирует** — шлёт GET с заголовками апгрейда:

```http
GET /ws HTTP/1.1
Host: example.com
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
Sec-WebSocket-Version: 13
```

2. **Сервер отвечает `101 Switching Protocols`**:

```http
HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
```

(`Sec-WebSocket-Accept` = хэш от `Sec-WebSocket-Key` — сервер доказывает, что он понимает именно WS, а не случайный HTTP-сервер.)

3. С этого момента TCP-соединение остаётся открытым, обмен — лёгкими **бинарными или текстовыми фреймами**, а не HTTP-сообщениями.

❗ Экзаменационные детали:
- Если код ответа **не 101** — рукопожатие не состоялось, соединения нет.
- Заголовки запроса: **Host, Connection, Upgrade, Sec-WebSocket-Version, Sec-WebSocket-Key**; ответа: **Connection, Upgrade, Sec-WebSocket-Accept** (+ опциональные Sec-WebSocket-Extensions/Protocol, если просили).
- **101 Switching Protocols** — тот самый статус из класса 1xx (лекция 1, раздел 3.3).

### 2.4 URI: ws:// и wss://

- **ws://** — незашифрованное соединение (порт по умолчанию **80**);
- **wss://** — шифрование через TLS (порт **443**).

Остальная часть URI — как в HTTP: host, порт, путь, query. Фрагменты (`#`) не допускаются; `#` в пути экранируется как `%23`.

### 2.5 Клиентский API: события, методы, свойства ❗

Программирование WS — асинхронное, событийно-ориентированное: стороны **слушают события** (без опроса!).

**События:**

| Событие | Когда |
|---|---|
| `open` | рукопожатие прошло успешно — можно слать/принимать |
| `message` | пришли данные (текст или бинарные) |
| `error` | сбой (например, не удалось отправить) |
| `close` | соединение закрыто (сбой, завершение, ошибка TCP) |

```javascript
const socket = new WebSocket("wss://example.com/chat");
socket.addEventListener("open", (event) => socket.send("Привет!"));
socket.addEventListener("message", (event) => console.log("Сообщение: ", event.data));
socket.addEventListener("close", (event) => {
  if (event.wasClean) { /* нормальное закрытие */ } else { /* обрыв */ }
});
```

**Методы:** `send(data)` — отправка (строка, Blob, ArrayBuffer; проверяй `readyState`); `close([code, reason])` — закрытие с кодом и человекочитаемой причиной.

**Свойства:** `readyState` (CONNECTING/OPEN/CLOSING/CLOSED), `bufferedAmount` (байты в очереди на отправку — основа адаптивной отправки: сеть не успевает — притормози), `binaryType`, `url`.

### 2.6 Подпротоколы

Поверх «голого» WS обычно нужен протокол прикладного уровня — **подпротокол** (задаётся в конструкторе `new WebSocket(url, protocols)`): **STOMP**, **MQTT**, собственные. Они определяют форматы и семантику сообщений (маршруты, подписки, команды).

---

## 3. WebSocket на Java #1: чистый WS (практика СОП 10)

### 3.1 Конфигурация

```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationHandler(), "/ws").setAllowedOriginPatterns("*");
    }
}
```

### 3.2 Handler: управление сессиями

```java
@Component
public class NotificationHandler extends TextWebSocketHandler {

    // Потокобезопасный список сессий
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // ответ на сообщения клиента, ping/pong и т.п.
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session);
    }

    public int broadcast(String message) {
        TextMessage textMessage = new TextMessage(message);
        int sent = 0;
        for (WebSocketSession session : sessions) {
            if (sendMessage(session, textMessage)) sent++;
        }
        return sent;
    }

    private boolean sendMessage(WebSocketSession session, TextMessage message) {
        if (!session.isOpen()) { sessions.remove(session); return false; }
        try {
            session.sendMessage(message);
            return true;
        } catch (IOException e) {
            sessions.remove(session);
            return false;
        }
    }
}
```

❗ Две инженерные детали, которые спрашивают:
1. **Почему `CopyOnWriteArrayList`**: слушатели и HTTP-потоки работают с списком **параллельно** (подключение/отключение vs broadcast) — нужен потокобезопасный контейнер.
2. **Почему `sendMessage` в try/catch с удалением сессии**: TCP-сессия может умереть в любой момент; запись в мёртвую сессию кидает исключение. Для высоких нагрузок — `ConcurrentWebSocketSessionDecorator` (сериализует запись в одну сессию из разных потоков).

---

## 4. WebSocket на Java #2: STOMP + SockJS (лекция «WebSocket + Java»)

Когда нужны **маршруты подписок** (кто на какую тему подписан) — берут подпротокол **STOMP**:

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");              // встроенный брокер тем
        config.setApplicationDestinationPrefixes("/app"); // куда клиент шлёт команды
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }
}
```

Сервис публикует в тему:

```java
@Service
public class NotificationService {
    @Autowired
    private SimpMessagingTemplate template;

    public void sendNotification(String message) {
        template.convertAndSend("/topic/notifications", message);
    }
}
```

Фронтенд (SockJS — обёртка с фолбэками; STOMP-клиент):

```javascript
var socket = new SockJS('http://localhost:8082/ws');
var stompClient = Stomp.over(socket);
stompClient.connect({}, function(frame) {
    stompClient.subscribe('/topic/notifications', function(notification) {
        // добавить элемент на страницу
    });
});
```

Семантика: клиент подписывается на `/topic/notifications`; сервер шлёт туда — все подписчики получают. **`/topic`** — префикс тем брокера, **`/app`** — префикс команд в приложение.

И связка с брокером сообщений (лекция «WS + Java»): приложение слушает очередь RabbitMQ и пересылает в тему:

```java
@RabbitListener(queues = "firstQueue")
public void listen(String message) {
    notificationService.sendNotification(message);   // RabbitMQ → WebSocket
}
```

💡 Выбор из лекции: «уведомления можно делать и на базовом WS, и на STOMP; для чатов можно рассмотреть прямую реализацию через RabbitMQ». SockJS+STOMP — «распространённый на Spring кейс» для чатов/уведомлений; чистый WS — там, где важен каждый байт и миллисекунда (игровые серверы, тикеры) и накладные расходы STOMP излишни.

---

## 5. Notification Service — архитектурный паттерн (СОП 11) ❗

### 5.1 Почему не «каждый сервис сам держит WebSocket»

В микросервисной архитектуре **не рекомендуется, чтобы каждый сервис открывал WS-соединение с браузером** — клиенту пришлось бы держать десятки открытых соединений. Вместо этого — **один Notification Service**:

1. **Вход:** асинхронный `@RabbitListener` — слушает события всех сервисов через шину сообщений;
2. **Выход:** держит (единственное) постоянное WS-соединение с браузером;
3. **Роль:** маршрутизирует сообщения из RabbitMQ в нужный WS-канал — **адаптер** между асинхронным миром и миром подключений.

```
microservices ──► RabbitMQ ──► @RabbitListener ──► NotificationService ──► WebSocket ──► браузер
```

### 5.2 Тонкое место: потоки ❗

**RabbitMQ работает в пуле потоков слушателей, а WS-сессии хранятся в памяти. Операция записи в сокет должна быть потокобезопасной** — два listener-потока могут одновременно писать в одну сессию. Отсюда CopyOnWriteArrayList + сериализация записи (декоратор) из раздела 3.2.

Эта же архитектура — у тебя в лекции 4 (DGS subscriptions тоже работают поверх WebSocket/SSE) и в лекции 6 (надёжность шины кормит уведомления).

---

## 🔗 В проекте smart-fridge

WS в проекте нет (события уходят только в audit-сервис по RabbitMQ). Если спросят «как бы ты сделал уведомления о просрочке продуктов» — по этой лекции: notification-service с `@RabbitListener` на `item.*` → `SimpMessagingTemplate.convertAndSend("/topic/expiry")` → фронт подписан через SockJS/STOMP.

## ⚠️ Возможные развилки экзамена

- «Почему WS начинается с HTTP и чем потом отличается» → 2.3 (101, заголовки, фреймы).
- «Long polling vs WebSocket: где заголовки и соединения» → 1.3 vs 2.2.
- «Почему нельзя каждому микросервису свой WS в браузер» → 5.1.
- «Почему CopyOnWriteArrayList и try/catch вокруг sendMessage» → 3.2 / 5.2.
- «Чем STOMP отличается от WebSocket» → 2.6 (подпротокол поверх WS).

## ✅ Самопроверка

1. Восстанови цепочку polling → long polling → streaming → SSE → WebSocket: у каждого — механизм и главный минус.
2. Три ограничения AJAX/Comet из лекции.
3. Нарисуй рукопожатие: запрос с заголовками, ответ со статусом и заголовком. Что будет, если ответ не 101?
4. ws vs wss: порты, шифрование.
5. Четыре события клиентского API и что делает `bufferedAmount`.
6. Почему список сессий — CopyOnWriteArrayList, а отправка — в try/catch?
7. Нарисуй архитектуру Notification Service и объясни роль адаптера.

---
*Назад: [Лекция 7](07-grpc-и-protobuf.md) · Дальше: [Лекция 9. Consul](09-consul-и-service-discovery.md)*
