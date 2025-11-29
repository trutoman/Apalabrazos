# Arquitectura Basada en Eventos

## Descripción General

Esta aplicación utiliza un **patrón de diseño basado en eventos (Event-Driven Architecture)** con un **EventBus central** para la comunicación entre componentes.

## Componentes Principales

### 1. **EventBus** (`backend/events/EventBus.java`)
- **Singleton** que actúa como mediador central de comunicación
- Permite **publicar** eventos y **registrar listeners**
- Los listeners implementan la interfaz `EventListener`
- Diseño simple y fácil de entender para principiantes

### 2. **EventListener** (`backend/events/EventListener.java`)
- **Interfaz** con un único método: `onEvent(GameEvent event)`
- Cualquier clase que quiera recibir eventos debe implementar esta interfaz
- El método `onEvent` recibe todos los eventos y debe filtrar los que le interesan

### 3. **Eventos** (`backend/events/`)
Todos los eventos heredan de `GameEvent` y son **objetos inmutables** que representan algo que ocurrió:

- `GameStartedEvent` - Se inicia un nuevo juego
- `AnswerSubmittedEvent` - El jugador envía una respuesta
- `AnswerValidatedEvent` - Se valida una respuesta
- `QuestionChangedEvent` - Cambia la pregunta actual
- `TimerTickEvent` - Actualización del temporizador
- `TurnEndedEvent` - Finaliza el turno de un jugador
- `GameFinishedEvent` - Termina el juego completo

### 4. **GameService** (`backend/service/GameService.java`)
- **Servicio backend** que contiene la lógica de negocio del juego
- Implementa `EventListener` para recibir eventos
- **Publica** eventos de estado (ej: `AnswerValidatedEvent`, `QuestionChangedEvent`)
- Gestiona el modelo de datos (`GameGlobal`, `GamePlayer`, etc.)

### 5. **Controladores JavaFX** (`frontend/controller/`)
- **MenuController** - Publica `GameStartedEvent` cuando el usuario inicia el juego
- **GameController** - Implementa `EventListener` y actualiza la UI
- **ResultsController** - Implementa `EventListener` para mostrar resultados

## Flujo de Comunicación

```
Usuario interactúa con UI
    ↓
Controlador publica evento
    ↓
EventBus notifica a todos los listeners
    ↓
GameService recibe evento en onEvent()
    ↓
GameService procesa lógica y actualiza modelo
    ↓
GameService publica evento de estado
    ↓
EventBus notifica a controladores
    ↓
Controladores reciben evento en onEvent()
    ↓
Controladores actualizan UI
```

## Ejemplo de Uso

### Publicar un evento:
```java
EventBus eventBus = EventBus.getInstance();
eventBus.publish(new GameStartedEvent("Jugador 1", "Jugador 2"));
```

### Recibir eventos (implementando EventListener):
```java
public class MiControlador implements EventListener {

    @Override
    public void onEvent(GameEvent event) {
        // Verificar el tipo de evento
        if (event instanceof GameStartedEvent) {
            GameStartedEvent gameEvent = (GameStartedEvent) event;
            System.out.println("Juego iniciado: " + gameEvent.getPlayerOneName());
        } else if (event instanceof QuestionChangedEvent) {
            QuestionChangedEvent questionEvent = (QuestionChangedEvent) event;
            System.out.println("Nueva pregunta: " + questionEvent.getLetter());
        }
    }
}
```

### Registrarse como listener:
```java
EventBus eventBus = EventBus.getInstance();
eventBus.addListener(this); // 'this' debe implementar EventListener
```

## Ventajas de esta Arquitectura

### ✅ **Desacoplamiento**
- Los componentes no se conocen directamente
- Fácil agregar nuevos suscriptores sin modificar publicadores

### ✅ **Escalabilidad**
- Múltiples componentes pueden escuchar el mismo evento
- Fácil agregar nuevos tipos de eventos

### ✅ **Testabilidad**
- Los componentes se pueden probar de forma aislada
- Fácil simular eventos para testing

### ✅ **Mantenibilidad**
- Separación clara de responsabilidades
- El flujo de datos es explícito y trazable

## Separación de Responsabilidades

### **Frontend (Controladores)**
- Capturan interacciones del usuario
- Publican eventos de usuario
- Se suscriben a eventos de estado
- Actualizan la interfaz visual

### **Backend (GameService)**
- Contiene toda la lógica de negocio
- Valida reglas del juego
- Gestiona el estado del modelo
- Publica eventos de cambios de estado

### **Modelos (backend/model/)**
- Representan datos puros (POJOs)
- No tienen lógica de negocio compleja
- No conocen eventos ni servicios

## Próximos Pasos

Para completar la implementación necesitas:

1. **Inicializar GameService** en tu `MainApp.java`:
   ```java
   GameService gameService = new GameService();
   ```

2. **Cargar preguntas** desde archivos JSON en `GameService.handleGameStarted()`

3. **Implementar timer** que publique `TimerTickEvent` periódicamente

4. **Conectar la UI** con los métodos del controlador (botones, campos de texto, etc.)

5. **Gestionar navegación** entre vistas usando el `ViewNavigator`

## Notas Importantes

- **Thread-safety**: `EventBus` es thread-safe, pero los manejadores de eventos en JavaFX deben usar `Platform.runLater()` para actualizar la UI
- **Limpieza**: Considera des-suscribirse de eventos cuando un controlador se destruye para evitar memory leaks
- **Errores**: El EventBus captura excepciones en handlers para que un error no afecte a otros suscriptores
