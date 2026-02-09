# Arquitectura Player - Documentación

## Resumen
Se ha implementado la clase **Player** como el "ancla" de la arquitectura del sistema, junto con las estructuras de soporte necesarias para gestionar conexiones y sesiones de jugadores.

## Componentes Implementados

### 1. **PlayerState** (Enum)
**Ubicación:** `src/main/java/UE_Proyecto_Ingenieria/Apalabrazos/backend/model/PlayerState.java`

Estados lógicos del jugador:
- `LOBBY` - En el lobby, sin partida
- `MATCHMAKING` - Buscando partida
- `PLAYING` - En partida activa
- `DISCONNECTED` - Desconectado (puede reconectar)
- `FINISHED` - Partida completada, viendo resultados

### 2. **MessageSender** (Interface)
**Ubicación:** `src/main/java/UE_Proyecto_Ingenieria/Apalabrazos/backend/network/MessageSender.java`

Abstracción del canal de comunicación con el cliente. Permite:
- Desacoplar la lógica de juego del medio de transporte (WebSocket, HTTP, etc.)
- Facilitar testing mediante implementaciones mock
- Mantener el código testeable y limpio

**Métodos:**
```java
void send(Object message);      // Enviar mensaje al cliente
boolean isConnected();          // Verificar si está conectado
void close();                   // Cerrar conexión
```

### 3. **MockMessageSender** (Implementación Mock)
**Ubicación:** `src/main/java/UE_Proyecto_Ingenieria/Apalabrazos/backend/network/MockMessageSender.java`

Implementación para testing que almacena mensajes en una lista para verificación.

**Métodos adicionales:**
```java
List<Object> getSentMessages();  // Obtener todos los mensajes
Object getLastMessage();         // Último mensaje enviado
int getMessageCount();           // Cantidad de mensajes
void clearMessages();            // Limpiar mensajes
void reconnect();                // Simular reconexión
```

### 4. **Player** (Clase Mejorada)
**Ubicación:** `src/main/java/UE_Proyecto_Ingenieria/Apalabrazos/backend/model/Player.java`

El **ancla de la arquitectura**. Vive durante toda la sesión del usuario.

#### Tres Facetas:

**A) Identidad**
```java
UUID sessionId;         // ID de sesión único
String name;           // Nombre del jugador
String playerID;       // ID legible: nombre-xxxx
String imageResource;  // Avatar/imagen
```

**B) Estado Lógico**
```java
PlayerState state;     // Estado actual
UUID currentMatchId;   // Partida actual (null si no está en una)
```

**C) Canal de Comunicación**
```java
MessageSender sender;  // Abstracción del canal de red
```

#### Métodos Clave:

**Gestión de Sesión:**
```java
UUID getSessionId()
PlayerState getState()
void setState(PlayerState state)
UUID getCurrentMatchId()
void setCurrentMatchId(UUID matchId)
boolean isInMatch()
```

**Comunicación:**
```java
void sendMessage(Object message)  // Enviar mensaje al cliente
boolean isConnected()              // Verificar conexión
void disconnect()                  // Desconectar jugador
```

#### Constructores:

```java
// Para código legacy/testing (sin red)
Player()
Player(String name)
Player(String name, String imageResource)

// Constructor principal para servidor
Player(UUID sessionId, String name, MessageSender sender)
```

### 5. **GameSessionManager** (Actualizado)
**Ubicación:** `src/main/java/UE_Proyecto_Ingenieria/Apalabrazos/backend/service/GameSessionManager.java`

Ahora es **Singleton** y maneja conexiones activas.

#### Nuevas Estructuras:
```java
Map<UUID, Player> activeConnections;  // SessionID → Player
```

#### Nuevos Métodos de Conexión:

**Gestión de Conexiones:**
```java
boolean registerConnection(Player player)
Player unregisterConnection(UUID sessionId)
Player getPlayerBySessionId(UUID sessionId)
List<Player> getAllConnectedPlayers()
int getActiveConnectionCount()
boolean isSessionActive(UUID sessionId)
```

**Mensajería:**
```java
void broadcastToAll(Object message)
boolean sendToPlayer(UUID sessionId, Object message)
```

## Flujo de Trabajo

### 1. Nueva Conexión (Nivel 1 - Red)
```java
// En el ConnectionHandler (WebSocket)
WebSocketSession session = ...;
MessageSender sender = new WebSocketMessageSender(session);
```

### 2. Crear Player (Nivel 2 - Sesión)
```java
UUID sessionId = UUID.randomUUID();
Player player = new Player(sessionId, username, sender);
GameSessionManager.getInstance().registerConnection(player);
```

### 3. Transiciones de Estado
```java
player.setState(PlayerState.MATCHMAKING);  // Buscando partida
player.setState(PlayerState.PLAYING);      // En partida
player.setCurrentMatchId(matchId);
```

### 4. Comunicación (Nivel 4 - Match)
```java
// El Match envía eventos al jugador
player.sendMessage("Tu turno - Pregunta #1");
player.sendMessage("¡Respuesta correcta! +10 puntos");
```

### 5. Desconexión
```java
GameSessionManager.getInstance().unregisterConnection(sessionId);
```

## Ejemplo de Uso

Ver el ejemplo completo ejecutable en:
- `ejemplos-modernizacion/SimplePlayerExample.java`

Ejecutar con:
```bash
mvn compile
javac -cp target/classes ejemplos-modernizacion/SimplePlayerExample.java -d target/classes
java -cp target/classes UE_Proyecto_Ingenieria.Apalabrazos.ejemplos.SimplePlayerExample
```

## Testing

Ejemplo de test unitario:
```java
@Test
public void testPlayerMessaging() {
    MockMessageSender sender = new MockMessageSender();
    Player player = new Player(UUID.randomUUID(), "TestPlayer", sender);

    player.sendMessage("Hello");
    player.sendMessage("World");

    assertEquals(2, sender.getMessageCount());
    assertEquals("Hello", sender.getSentMessages().get(0));
    assertEquals("World", sender.getLastMessage());
}
```

## Beneficios de esta Arquitectura

1. **Separación de Responsabilidades**: Red, sesión y lógica están desacopladas
2. **Testeable**: Mock fácil del canal de comunicación
3. **Extensible**: Fácil añadir nuevos medios de transporte (HTTP, gRPC, etc.)
4. **Thread-Safe**: Maps concurrentes en GameSessionManager
5. **Ciclo de Vida Claro**: Player vive durante toda la sesión
6. **Desacoplamiento**: La lógica de juego no sabe nada de WebSockets

## Próximos Pasos

1. Implementar `WebSocketMessageSender` que implemente `MessageSender`
2. Crear el `ConnectionHandler` que gestione los WebSockets
3. Integrar con el sistema de eventos asíncrono existente
4. Añadir reconexión automática con timeout
5. Implementar heartbeat/ping para detectar desconexiones

## Compatibilidad

✅ **Retrocompatible**: Los constructores antiguos de Player siguen funcionando
✅ **Sin cambios breaking**: El código existente no requiere modificaciones
✅ **Compilación exitosa**: Todo el proyecto compila sin errores
