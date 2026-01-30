# ❓ ¿Cómo se invoca handleStartGame() del GameController?

## 📍 Hay **2 maneras** de invocar `handleStartGame()`:

---

## 1️⃣ **VÍA USUARIO** - Click en el botón "EMPEZAR"

### Secuencia:

```
[Usuario hace clic en el botón EMPEZAR]
         ↓
[FXML dispara el evento OnAction del startButton]
         ↓
GameController.initialize() [línea 161]
    startButton.setOnAction(event -> handleStartGame())
         ↓
[El listener ejecuta la lambda]
         ↓
GameController.handleStartGame() [línea 312]
```

### Código en GameController.initialize():
```java
@FXML
public void initialize() {
    log.debug("initialize()");
    // Configurar el botón de inicio
    if (startButton != null) {
        // Mientras se valida el inicio desde el lobby, mostrar "Esperando..."
        startButton.setText("Esperando...");
        startButton.setDisable(true);
        startButton.setOnAction(event -> handleStartGame());  // ← LÍNEA 161
    }
    // ... resto del código
}
```

### FXML (game.fxml, línea ~77):
```xml
<!-- Botón de inicio (antes de empezar) - CENTRO DE REFERENCIA -->
<Button fx:id="startButton" text="EMPEZAR" prefWidth="150" prefHeight="150"
        style="-fx-font-size: 20px; -fx-font-weight: bold; -fx-background-color: #27ae60; -fx-text-fill: white; -fx-border-radius: 75; -fx-background-radius: 75; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 10, 0, 0, 4);" />
```

---

## 2️⃣ **VÍA EVENTO** - CreatorInitGameEvent

### Secuencia:

```
[CreatorInitGameEvent se publica en el EventBus]
         ↓
GameController.onEvent(GameEvent event) [implementa EventListener]
         ↓
[Se verifica: if (event instanceof CreatorInitGameEvent)] [línea ~527]
         ↓
Platform.runLater(() -> {
    if (startButton != null) {
        startButton.setText("Empezar");
        startButton.setDisable(false);
    }
    handleStartGame();  // ← LÍNEA 532
})
         ↓
GameController.handleStartGame() [línea 312]
```

### Código en GameController.onEvent() (línea 520-532):
```java
// Verificar el tipo de evento y llamar al método apropiado
if (event instanceof TimerTickEvent) {
    int remaining = ((TimerTickEvent) event).getElapsedSeconds();
    Platform.runLater(() -> timerLabel.setText(String.valueOf(remaining)));
} else if (event instanceof CreatorInitGameEvent) {
    // Validación correcta del inicio del juego por el creador
    Platform.runLater(() -> {
        if (startButton != null) {
            startButton.setText("Empezar");
            startButton.setDisable(false);
        }
        // Iniciar como si se hubiera pulsado el botón
        handleStartGame();  // ← LÍNEA 532 - Se llama aquí automáticamente
    });
} else if (event instanceof QuestionChangedEvent) {
    // ... más código
}
```

---

## 📊 Tabla Comparativa

| Vía | Disparador | Línea | Sincronía |
|-----|-----------|-------|----------|
| **1. Usuario** | Click en botón | 161 | Síncrono |
| **2. Evento** | CreatorInitGameEvent | 532 | Asíncrono (Platform.runLater) |

---

## 🔄 ¿Cómo se habilita el botón?

### Estado inicial (en initialize):
```
startButton.setText("Esperando...")
startButton.setDisable(true)  ← DESHABILITADO
```

### Se habilita cuando llega CreatorInitGameEvent:
```
startButton.setText("Empezar")
startButton.setDisable(false)  ← HABILITADO
```

**O** puede ser habilitado directamente desde otro controlador.

---

## 🎯 ¿Quién publica CreatorInitGameEvent?

Busca en tu codebase por `new CreatorInitGameEvent(...)`:

```
CreatorInitGameEvent se publica probablemente desde:
├─ GameService.java
├─ GameSessionManager.java
├─ LobbyController.java
└─ Otros servicios del backend
```

---

## 📌 Notas Importantes

1. **El botón comienza DESHABILITADO** ("Esperando...") cuando se carga el GameController
2. **Se habilita cuando llega CreatorInitGameEvent** desde el backend
3. **Hay 2 puntos de entrada** para ejecutar el método:
   - Usuario hace click
   - Evento automático desde el backend

---

## 🚀 En resumen:

```
handleStartGame() se invoca CUANDO:

✓ Usuario hace clic en el botón "EMPEZAR"
  (listener registrado en GameController.initialize(), línea 161)

O

✓ Llega CreatorInitGameEvent al GameController
  (manejado en GameController.onEvent(), línea 532)
```
