# 🎮 Flujo de Llamadas de Funciones al Iniciar el Juego

## 📍 Punto de Entrada Principal

### 1. **MainApp.start(Stage primaryStage)** ← PUNTO DE ENTRADA
```
├─ GameSessionManager gameSessionManager = new GameSessionManager()
│  └─ gameSessionManager.log.info("GameSessionManager initialized")
│
└─ ViewNavigator navigator = new ViewNavigator(primaryStage)
   └─ navigator.showMenu()
```

---

## 🎯 Fase 1: MENÚ PRINCIPAL

### 2. **ViewNavigator.showMenu()**
```
├─ FXMLLoader loader = new FXMLLoader(menu.fxml)
├─ Parent root = loader.load()
├─ MenuController controller = loader.getController()
├─ controller.setNavigator(this)
├─ Scene scene = new Scene(root, SCENE_WIDTH, SCENE_HEIGHT)
├─ stage.setScene(scene)
└─ stage.show()
```

### 3. **MenuController.initialize()** [FXML initialize automático]
```
├─ usernameLabel.setText(username)
├─ difficultyInput.getItems().addAll("EASY", "MEDIUM", "HARD")
├─ gameTypeInput.getItems().addAll("HIGHER_POINTS_WINS", "NUMBER_WINS")
├─ jugarButton.setOnAction(event → handleMultiplayer())
├─ scoresButton.setOnAction(event → handleViewScores())
├─ exitButton.setOnAction(event → handleExit())
└─ setupButtonHoverEffects(button, hoverColor)  [3x]
   ├─ button.setOnMouseEntered()
   └─ button.setOnMouseExited()
```

---

## 🚪 Fase 2: LOBBY (SALA DE ESPERA)

### 4. **MenuController.handleMultiplayer()** [Cuando usuario hace clic en "JUGAR"]
```
└─ navigator.showLobby()
```

### 5. **ViewNavigator.showLobby()**
```
├─ FXMLLoader loader = new FXMLLoader(lobby.fxml)
├─ Parent root = loader.load()
├─ LobbyController controller = loader.getController()
├─ controller.setNavigator(this)
├─ Scene scene = new Scene(root)
├─ stage.setWidth(Double.NaN)
├─ stage.setHeight(Double.NaN)
├─ stage.sizeToScene()
├─ stage.centerOnScreen()
└─ stage.show()
```

### 6. **LobbyController.initialize()** [FXML initialize automático]
```
├─ eventBus = GlobalEventBus.getInstance()
├─ eventBus.addListener(this)
├─ usernameLabel.setText("JugadorVacio")
├─ setupComboBoxes()
│  ├─ difficultyCombo.setItems(["TRIVIAL", "EASY", "DIFFICULT"])
│  ├─ difficultyCombo.setValue("EASY")
│  ├─ gameTypeCombo.setItems(["HIGHER_POINTS_WINS", "NUMBER_WINS"])
│  └─ gameTypeCombo.setValue("HIGHER_POINTS_WINS")
│
├─ setupGamesTable()
│  ├─ games = FXCollections.observableArrayList()
│  ├─ gamesTable.setItems(games)
│  ├─ gameRoomColumn.setCellValueFactory(...)
│  ├─ gameHostColumn.setCellValueFactory(...)
│  ├─ ... [6 columnas más]
│  ├─ joinButton.setDisable(true)
│  ├─ startGameButton.setDisable(true)
│  └─ gamesTable.getSelectionModel().selectedItemProperty().addListener()
│     └─ eventBus.publish(new GetGameSessionInfoEvent(...))
│
└─ setupEventHandlers()
   ├─ startButton.setOnAction(e → handleCreateGame())
   ├─ startGameButton.setOnAction(e → handleStartGame())
   ├─ joinButton.setOnAction(e → handleJoinGame())
   ├─ backButton.setOnAction(e → handleBack())
   └─ setupButtonHoverEffects() [4x]
```

### 7. **LobbyController.handleCreateGame()** [Cuando usuario hace clic en "Crear Partida"]
```
├─ Player player = new Player(name)
├─ this.loggedInsideLobbyPlayer = player
├─ this.usernameLabel.setText(player.getPlayerID())
├─ Parse questionCount, duration, players, difficulty
└─ ... [Se publica evento para crear la partida]
```

### 8. **LobbyController.handleStartGame()** [Cuando usuario inicia la partida]
```
└─ navigator.showGame(playerOneConfig, externalBus)
```

---

## 🎲 Fase 3: PANTALLA DEL JUEGO

### 9. **ViewNavigator.showGame(GamePlayerConfig playerOneConfig, EventBus externalBus)**
```
├─ System.out.println("[ViewNavigator] showGame llamado...")
├─ FXMLLoader loader = new FXMLLoader(game.fxml)
├─ System.out.println("[ViewNavigator] Cargando FXML...")
├─ Parent root = loader.load()
├─ System.out.println("[ViewNavigator] FXML cargado...")
├─ GameController controller = loader.getController()
├─ System.out.println("[ViewNavigator] Controller obtenido...")
├─ controller.setNavigator(this)
├─ System.out.println("[ViewNavigator] Llamando setNavigator...")
├─ controller.setPlayerConfig(playerOneConfig)
├─ System.out.println("[ViewNavigator] Llamando setPlayerConfig...")
├─ controller.setExternalBus(externalBus)
├─ System.out.println("[ViewNavigator] Llamando setExternalBus...")
├─ controller.postInitialize()
├─ System.out.println("[ViewNavigator] Llamando postInitialize...")
├─ Scene scene = new Scene(root, SCENE_WIDTH, SCENE_HEIGHT)
├─ stage.setScene(scene)
├─ stage.show()
└─ System.out.println("[ViewNavigator] showGame completado exitosamente")
```

### 10. **GameController.setNavigator(ViewNavigator navigator)**
```
└─ this.navigator = navigator
└─ log.debug("setNavigator(navigator={})", navigator)
```

### 11. **GameController.setPlayerConfig(GamePlayerConfig playerConfig)**
```
├─ this.playerConfig = playerConfig
├─ if (playerConfig.getPlayer() != null) {
│  └─ this.localPlayerId = playerConfig.getPlayer().getPlayerID()
│  }
└─ this.roomId = playerConfig.getRoomId()
```

### 12. **GameController.setExternalBus(EventBus externalBus)**
```
├─ log.debug("setExternalBus(externalBus={})", externalBus)
├─ this.externalBus = externalBus
└─ if (!listenerRegistered && externalBus != null) {
   ├─ externalBus.addListener(this)
   └─ listenerRegistered = true
   }
```

### 13. **GameController.initialize()** [FXML initialize automático]
```
├─ log.debug("initialize()")
├─ if (startButton != null) {
│  ├─ startButton.setText("Esperando...")
│  ├─ startButton.setDisable(true)
│  └─ startButton.setOnAction(event → handleStartGame())
│  }
├─ if (roscoPane != null) {
│  ├─ roscoPane.widthProperty().addListener(() → recreateRosco())
│  └─ roscoPane.heightProperty().addListener(() → recreateRosco())
│  }
├─ initializeLetterStates()
├─ setupOptionButtons()
├─ if (playerOneCanvas != null) {
│  └─ setupCanvasCircle(playerOneCanvas)
│  }
└─ if (rivalCanvas != null) {
   └─ setupCanvasCircle(rivalCanvas)
   }
```

### 14. **GameController.postInitialize()**
```
├─ log.debug("postInitialize()")
├─ if (!listenerRegistered && externalBus != null) {
│  ├─ externalBus.addListener(this)
│  └─ listenerRegistered = true
│  }
└─ if (externalBus != null && localPlayerId != null && roomId != null) {
   ├─ externalBus.publish(new GameControllerReady(localPlayerId, roomId))
   └─ log.info("postInitialize() completed - publishing GameControllerReady...")
   }
```

---

## 🎮 Fase 4: INICIO DEL JUEGO (En el Lobby)

### 15. **GameController.handleStartGame()** [Cuando usuario hace clic en "Empezar"]
```
├─ log.debug("handleStartGame()")
├─ if (startButton != null) {
│  ├─ startButton.setVisible(false)
│  └─ startButton.setManaged(false)
│  }
├─ if (roscoPane != null) {
│  ├─ roscoPane.setVisible(true)
│  └─ roscoPane.setManaged(true)
│  }
├─ if (leftOptionsArea != null) {
│  ├─ leftOptionsArea.setVisible(true)
│  └─ leftOptionsArea.setManaged(true)
│  }
├─ if (rightOptionsArea != null) {
│  ├─ rightOptionsArea.setVisible(true)
│  └─ rightOptionsArea.setManaged(true)
│  }
├─ if (questionArea != null) {
│  ├─ questionArea.setVisible(true)
│  └─ questionArea.setManaged(true)
│  }
└─ if (skipButton != null) {
   ├─ skipButton.setVisible(true)
   └─ skipButton.setManaged(true)
   }
```

---

## 📊 Resumen del Flujo

```
MainApp.start()
    ↓
ViewNavigator.showMenu()
    ↓
MenuController.initialize()
    ↓
[Usuario hace clic en "JUGAR"]
    ↓
MenuController.handleMultiplayer()
    ↓
ViewNavigator.showLobby()
    ↓
LobbyController.initialize()
    ↓
[Usuario crea/inicia partida]
    ↓
LobbyController.handleStartGame()
    ↓
ViewNavigator.showGame()
    ↓
GameController.setNavigator()
    ↓
GameController.setPlayerConfig()
    ↓
GameController.setExternalBus()
    ↓
GameController.initialize() [FXML]
    ↓
GameController.postInitialize()
    ↓
[GameController está listo para jugar]
    ↓
GameController.handleStartGame() [Usuario inicia juego]
    ↓
[Mostrar UI del juego y rosco]
```

---

## 🔔 Eventos Publicados
- `GameCreationRequestedEvent` - Cuando se crea una partida
- `GetGameSessionInfoEvent` - Cuando se selecciona una partida
- `GameControllerReady` - Cuando el GameController está listo

---

## 🎯 Clases Involucradas
| Clase | Archivo | Función Principal |
|-------|---------|-------------------|
| **MainApp** | MainApp.java | Punto de entrada de la aplicación |
| **ViewNavigator** | ViewNavigator.java | Orquesta el cambio entre vistas |
| **MenuController** | MenuController.java | Controla el menú principal |
| **LobbyController** | LobbyController.java | Controla la sala de espera |
| **GameController** | GameController.java | Controla la pantalla del juego |
| **GlobalEventBus** | GlobalEventBus.java | Bus de eventos global |
| **GameSessionManager** | GameSessionManager.java | Gestiona las sesiones de juego |
