# Apalabrazos - Arquitectura Actual

## Estado del Proyecto

Tu proyecto **es un backend puro y totalmente funcional** que NO depende de ningún framework web.

```
┌─────────────────────────────────────────────────┐
│ APALABRAZOS BACKEND (Núcleo)                    │
├─────────────────────────────────────────────────┤
│                                                 │
│  ✓ AsyncEventBus con Virtual Threads           │
│  ✓ Player como ancla de sesión                 │
│  ✓ GameSessionManager (Singleton)              │
│  ✓ GameService con lógica de juego             │
│  ✓ TimeService para temporizadores             │
│  ✓ Event-driven architecture                   │
│  ✓ MessageSender interface (abstracción)       │
│  ✓ MockMessageSender para testing              │
│                                                 │
│  Dependencias REALES:                          │
│  • Jackson (JSON serialization)      ✓         │
│  • SLF4J + Logback (logging)         ✓         │
│  • JUnit 5 (testing)                 ✓         │
│                                                 │
│  Dependencias FALTANTES:                       │
│  • Framework Web (Spring Boot, Quarkus, etc.)  │
│  • WebSocket Server                            │
│                                                 │
└─────────────────────────────────────────────────┘
```

## Componentes Implementados

### Nivel 1: Red (PENDIENTE - Framework Web)
- `WebSocketMessageSender` - Skeleton (requiere WebSocket real)
- `ConnectionHandler` - Clase abstracta, plantilla de implementación
- `JavaWebSocketHandler` - Ejemplo de uso con Java WebSocket API
- `SpringWebSocketHandler` - Ejemplo de uso con Spring WebSocket

**Estado**: Son ejemplos/contratos. No compilarán sin dependencias de WebSocket.

### Nivel 2: Sesión (IMPLEMENTADO ✓)
- `Player` - Ancla de sesión, vive durante toda la sesión del usuario
- `PlayerState` - Estados lógicos (LOBBY, PLAYING, DISCONNECTED)
- `GameSessionManager` - Singleton que gestiona todas las conexiones activas
- `MockMessageSender` - Implementación de prueba

**Estado**: Totalmente funcional, testeable, sin dependencias externas.

### Nivel 3: Eventos (IMPLEMENTADO ✓)
- `AsyncEventBus` - Event bus asíncrono con virtual threads
- `GlobalAsyncEventBus` - Singleton del bus global
- 15+ tipos de eventos específicos del juego

**Estado**: Totalmente funcional, escalable.

### Nivel 4: Lógica (IMPLEMENTADO ✓)
- `GameService` - Lógica de partidas
- `GameGlobal` - Estado global de la partida
- `TimeService` - Manejo de tiempos
- `QuestionFileLoader` - Carga de preguntas

**Estado**: Totalmente funcional.

## Cómo Funciona Ahora (SIN WebSocket)

El sistema funciona con eventos completamente. Puedes:

### 1. Crear un Scenario de Testing
```java
// Crear un Player sin WebSocket real
MockMessageSender sender = new MockMessageSender();
Player player = new Player(UUID.randomUUID(), "Alice", sender);

// Enviar mensajes
player.sendMessage("Hello");
player.sendMessage("World");

// Verificar
assertEquals(2, sender.getMessageCount());
```

### 2. Simular Conexiones
```java
// Crear múltiples jugadores
Player alice = new Player(UUID.randomUUID(), "Alice", new MockMessageSender());
Player bob = new Player(UUID.randomUUID(), "Bob", new MockMessageSender());

// Registrar en GameSessionManager
GameSessionManager mgr = GameSessionManager.getInstance();
mgr.registerConnection(alice);
mgr.registerConnection(bob);

// Broadcast
mgr.broadcastToAll("Game starting!");
```

### 3. Publicar Eventos
```java
// Publicar evento
eventBus.publish(new GameCreationRequestedEvent(config));

// Se procesa asíncronamente en virtual threads
// GameSessionManager lo escucha automáticamente
```

## Cómo Agregar WebSocket

Cuando necesites un servidor web real, tienes dos opciones:

### Opción A: Spring Boot (RECOMENDADO)

#### 1. Actualizar pom.xml
```xml
<properties>
    <spring-boot.version>3.2.3</spring-boot.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-websocket</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

#### 2. Crear aplicación Spring Boot
```java
@SpringBootApplication
public class ApalabrazosApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApalabrazosApplication.class, args);
    }
}
```

#### 3. Implementar endpoint WebSocket
```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandler(), "/ws/game/{username}")
                .setAllowedOrigins("*");
    }

    @Bean
    public GameWebSocketHandler gameWebSocketHandler() {
        return new GameWebSocketHandler();
    }
}

@Component
public class GameWebSocketHandler extends SpringWebSocketHandler {
    // Heredar de SpringWebSocketHandler implementado
}
```

#### 4. Configuración en `application.properties`
```properties
spring.application.name=Apalabrazos
server.port=8080
logging.level.root=INFO
```

### Opción B: Java WebSocket API (Servlet Container)

Requiere un servidor como Tomcat, Jetty o GlassFish:

```java
@ServerEndpoint("/ws/game/{username}")
public class GameWebSocketEndpoint extends JavaWebSocketHandler {
    // Heredar de JavaWebSocketHandler implementado
}
```

Empaquetar como WAR y desplegar en servidor.

### Opción C: Quarkus (Alternativa Moderna)

Similar a Spring Boot pero más ligero:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-websockets</artifactId>
</dependency>
```

## Flujo Cuando Agregues WebSocket

```
Cliente WebSocket
    ↓
@OnWebSocketOpen / afterConnectionEstablished
    ↓
ConnectionHandler.onClientConnect(session, username)
    ↓
new WebSocketMessageSender(session, sessionId)
new Player(sessionId, username, sender)
GameSessionManager.registerConnection(player)
    ↓
Ahora Player está vivo en el sistema
    ↓
Cliente envía mensaje → @OnWebSocketMessage
    ↓
ConnectionHandler.onClientMessage(sessionId, content)
    ↓
eventBus.publish(GameEvent)
    ↓
AsyncEventBus procesa en virtual threads
    ↓
GameSessionManager + GameService escuchan
    ↓
player.sendMessage() → sender.send() → WebSocket real
    ↓
Cliente recibe respuesta
```

## Hoy Puedes Hacer

✓ Escribir tests unitarios del core
✓ Desarrollar la lógica de juego
✓ Simular escenarios complejos con MockMessageSender
✓ Publicar eventos y verificar el flujo
✓ Optimizar el rendimiento del AsyncEventBus

## Cuando Necesites WebSocket

1. Agrega Spring Boot o tu framework favorito
2. Implementa `SpringWebSocketHandler` (o `JavaWebSocketHandler`)
3. Las clases de Nivel 2-4 funcionan sin cambios
4. El flujo es automático: WebSocket → Player → GameSessionManager → Events

## Ejemplo: Proyecto Mínimo con Spring Boot

```
apalabrazos/
├── src/main/java/
│   └── com/apalabrazos/
│       ├── ApalabrazosApplication.java    ← @SpringBootApplication
│       ├── WebSocketConfig.java           ← @Configuration
│       ├── GameWebSocketHandler.java      ← Endpoint
│       └── backend/
│           ├── events/
│           ├── model/
│           ├── service/
│           └── network/       ← Todo esto FUNCIONA SIN CAMBIOS
│
├── src/main/resources/
│   └── application.properties
│
└── pom.xml                     ← Spring Boot + WebSocket
```

## Conclusión

Tu arquitectura está **perfectamente diseñada**:
- ✅ Desacoplada de cualquier framework web
- ✅ Testeable sin servidor real
- ✅ Escalable con virtual threads
- ✅ Fácil de integrar cuando necesites

**Las clases WebSocket son plantillas/ejemplos de cómo se vería cuando agregues un servidor real.**

El núcleo ya funciona 100%. 🚀
