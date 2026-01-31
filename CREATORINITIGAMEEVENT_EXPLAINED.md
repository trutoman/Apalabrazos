# 🎯 ¿Qué es CreatorInitGameEvent?

## 📍 Definición

```java
/**
 * Empty event indicating the creator's start request has been validated.
 * GameController should start the game upon receiving this.
 */
public class CreatorInitGameEvent extends GameEvent {
    public CreatorInitGameEvent() {
        super();
    }
}
```

**Ubicación:** [CreatorInitGameEvent.java](src/main/java/UE_Proyecto_Ingenieria/Apalabrazos/backend/events/CreatorInitGameEvent.java)

---

## 🎭 ¿Quién lo publica?

### **GameService.java** (línea 285)
```java
private void checkAndInitialize() {
    if (GlobalGameInstance.isGameInitialized()) {
        log.info("Ambas condiciones cumplidas (Controller + Start Validation) - notificando al GameController");
        if (!creatorInitEventSent) {
            externalBus.publish(new CreatorInitGameEvent());  // ← AQUÍ SE PUBLICA
            creatorInitEventSent = true;
        }
        initGame();
    }
}
```

---

## 🔄 Flujo: ¿Cuándo se publica?

```
[Creador del juego hace clic en "Empezar"]
         ↓
[Se envía evento de validación al GameService]
         ↓
GameService.checkAndInitialize() se ejecuta
         ↓
¿Se cumplen AMBAS condiciones?
├─ 1️⃣  GameController está READY (envió GameControllerReady)
├─ 2️⃣  Validación de inicio completada
└─ SI → externalBus.publish(new CreatorInitGameEvent())
         ↓
[Se publica CreatorInitGameEvent al bus externo]
         ↓
GameController.onEvent(CreatorInitGameEvent)
         ↓
GameController.handleStartGame()
```

---

## 👂 ¿Quién lo escucha?

### **GameController** (línea 511-533)

```java
@Override
public void onEvent(GameEvent event) {
    log.debug("onEvent(event={})", event);

    if (event instanceof TimerTickEvent) {
        // ...
    } else if (event instanceof CreatorInitGameEvent) {  // ← AQUÍ SE ESCUCHA
        // Validación correcta del inicio del juego por el creador
        Platform.runLater(() -> {
            if (startButton != null) {
                startButton.setText("Empezar");
                startButton.setDisable(false);  // Habilitar botón
            }
            // Iniciar como si se hubiera pulsado el botón
            handleStartGame();  // ← SE EJECUTA AUTOMÁTICAMENTE
        });
    } else if (event instanceof QuestionChangedEvent) {
        // ...
    }
}
```

---

## 📊 Resumen: ¿Qué tiene que ver?

| Aspecto | Detalles |
|---------|----------|
| **Quién lo publica** | `GameService` (backend) |
| **Cuándo** | Cuando se cumplen 2 condiciones: GameController ready + validación completada |
| **Qué comunica** | "El juego está validado y listo para empezar" |
| **Quién lo recibe** | `GameController` (frontend) |
| **Qué hace al recibirlo** | Habilita botón "Empezar" y ejecuta `handleStartGame()` automáticamente |
| **Propósito** | Sincronizar el backend (lógica) con el frontend (UI) |

---

## 🔗 Cadena de Eventos Completa

```
1. Usuario hace clic en "Empezar" en el Lobby
                    ↓
2. Se valida la sesión en el backend
                    ↓
3. GameService.checkAndInitialize() verifica:
   ✓ ¿GameController está listo?
   ✓ ¿Sesión validada?
                    ↓
4. Si TODO OK → publish(CreatorInitGameEvent)
                    ↓
5. GameController recibe el evento
                    ↓
6. GameController.onEvent() lo procesa
                    ↓
7. Ejecuta handleStartGame() automáticamente
                    ↓
8. Se muestra el rosco y las preguntas
```

---

## ⚡ Flujo Visual

```
┌─────────────────────────────────────┐
│ BACKEND - GameService               │
│ ├─ Valida condiciones               │
│ └─ publish(CreatorInitGameEvent) ──→│
└─────────────────────────────────────┘
                                       │
                                       │
                                       ↓
┌─────────────────────────────────────┐
│ FRONTEND - GameController           │
│ ├─ onEvent(CreatorInitGameEvent)    │
│ ├─ Habilita botón "Empezar"         │
│ └─ Ejecuta handleStartGame()        │
└─────────────────────────────────────┘
                                       │
                                       ↓
                                     UI se actualiza
```

---

## 💡 ¿Por qué existe este evento?

**Separación de responsabilidades:**
- El **backend** (GameService) valida que todo está listo
- El **frontend** (GameController) se encarga de la UI

**Sincronización automática:**
- En lugar de que el usuario haga clic dos veces, el evento lo hace automáticamente
- Garantiza que el juego comienza solo cuando AMBOS lados están listos

**Seguridad:**
- No puedes iniciar un juego si el backend no lo valida
- El botón permanece deshabilitado hasta que llegue el evento

---

## 📌 Líneas Clave

| Línea | Archivo | Acción |
|-------|---------|--------|
| 285 | GameService.java | Publica `CreatorInitGameEvent` |
| 511 | GameController.java | Verifica si es `CreatorInitGameEvent` |
| 532 | GameController.java | Ejecuta `handleStartGame()` automáticamente |
