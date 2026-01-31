package UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller;

import UE_Proyecto_Ingenieria.Apalabrazos.frontend.ViewNavigator;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.*;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.*;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.service.GameService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class LobbyController implements EventListener {

    private static final Logger log = LoggerFactory.getLogger(LobbyController.class);

    @FXML private ImageView profileImage;
    @FXML private Label usernameLabel;
    @FXML private TextField playerNameInput;
    @FXML private ComboBox<String> difficultyCombo;
    @FXML private ComboBox<String> gameTypeCombo;
    @FXML private TextField questionCountInput;
    @FXML private TextField durationInput;
    @FXML private TextField playersInput;
    @FXML private TextField roomCodeInput;
    @FXML private Label statusLabel;
    @FXML private TableView<GameLobbyEntry> gamesTable;
    @FXML private TableColumn<GameLobbyEntry, String> gameRoomColumn;
    @FXML private TableColumn<GameLobbyEntry, String> gameHostColumn;
    @FXML private TableColumn<GameLobbyEntry, String> gameTypeColumn;
    @FXML private TableColumn<GameLobbyEntry, String> gameDifficultyColumn;
    @FXML private TableColumn<GameLobbyEntry, String> gameQuestionCountColumn;
    @FXML private TableColumn<GameLobbyEntry, String> gameDurationColumn;
    @FXML private TableColumn<GameLobbyEntry, String> gamePlayersColumn;
    @FXML private TableColumn<GameLobbyEntry, String> gamePlayersListColumn;
    @FXML private Button startButton;
    @FXML private Button startGameButton;
    @FXML private Button joinButton;
    @FXML private Button backButton;

    private ViewNavigator navigator;
    private EventBus eventBus;
    private ObservableList<GameLobbyEntry> games;
    private Player loggedInsideLobbyPlayer;
    private final Map<String, GameLobbyEntry> pendingGames = new HashMap<>();
    private final Map<String, String> pendingHostsByTempCode = new HashMap<>();
    private final Map<String, String> pendingHostPlayerIdByTempCode = new HashMap<>();
    private final Map<String, GameService> sessionServices = new HashMap<>();
    private boolean suppressInfoRequest = false;

    public void setNavigator(ViewNavigator navigator) { this.navigator = navigator; }

    @FXML
    public void initialize() {
        eventBus = GlobalEventBus.getInstance();
        eventBus.addListener(this);
        usernameLabel.setText("JugadorVacio");
        setupComboBoxes();
        setupGamesTable();
        setupEventHandlers();
    }

    private void setupComboBoxes() {
        difficultyCombo.setItems(FXCollections.observableArrayList("TRIVIAL", "EASY", "DIFFICULT"));
        difficultyCombo.setValue("EASY");
        gameTypeCombo.setItems(FXCollections.observableArrayList("HIGHER_POINTS_WINS", "NUMBER_WINS"));
        gameTypeCombo.setValue("HIGHER_POINTS_WINS");
    }

    private void setupGamesTable() {
        games = FXCollections.observableArrayList();
        gamesTable.setItems(games);

        gameRoomColumn.setCellValueFactory(cd -> javafx.beans.binding.Bindings.createStringBinding(cd.getValue()::getRoomCode));
        gameHostColumn.setCellValueFactory(cd -> javafx.beans.binding.Bindings.createStringBinding(cd.getValue()::getHostName));
        gameTypeColumn.setCellValueFactory(cd -> javafx.beans.binding.Bindings.createStringBinding(cd.getValue()::getGameType));
        gameDifficultyColumn.setCellValueFactory(cd -> javafx.beans.binding.Bindings.createStringBinding(cd.getValue()::getDifficulty));
        gameQuestionCountColumn.setCellValueFactory(cd -> javafx.beans.binding.Bindings.createStringBinding(cd.getValue()::getQuestionCount));
        gameDurationColumn.setCellValueFactory(cd -> javafx.beans.binding.Bindings.createStringBinding(cd.getValue()::getDurationSeconds));
        gamePlayersColumn.setCellValueFactory(cd -> javafx.beans.binding.Bindings.createStringBinding(cd.getValue()::getPlayersSummary));
        gamePlayersListColumn.setCellValueFactory(cd -> javafx.beans.binding.Bindings.createStringBinding(cd.getValue()::getPlayersList));

        // Deshabilitar los botones Unirse y Empezar Partida por defecto
        joinButton.setDisable(true);
        startGameButton.setDisable(true);

        gamesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            // Habilitar los botones solo cuando hay una selección
            joinButton.setDisable(newSel == null);
            startGameButton.setDisable(newSel == null);

            if (newSel == null) {
                statusLabel.setText("Selecciona una partida");
                statusLabel.setStyle("-fx-text-fill: #f39c12;");
                roomCodeInput.clear();
            } else {
                statusLabel.setText("Partida " + newSel.getRoomCode() + " seleccionada");
                statusLabel.setStyle("-fx-text-fill: #3498db;");
                roomCodeInput.setText(newSel.getRoomCode());
            }

            // Publicar evento para obtener información de la sesión seleccionada
            if (newSel != null && !suppressInfoRequest) {
                eventBus.publish(new GetGameSessionInfoEvent(newSel.getRoomCode()));
                log.info("Published GetGameSessionInfoEvent for room: {}", newSel.getRoomCode());
            }
        });
        gamesTable.setPlaceholder(new Label("No hay partidas creadas"));
    }

    private void setupEventHandlers() {
        startButton.setOnAction(e -> handleCreateGame());
        startGameButton.setOnAction(e -> handleStartGame());
        joinButton.setOnAction(e -> handleJoinGame());
        backButton.setOnAction(e -> handleBack());
        setupButtonHoverEffects(startButton, "#27ae60");
        setupButtonHoverEffects(startGameButton, "#27ae60");
        setupButtonHoverEffects(joinButton, "#2980b9");
        setupButtonHoverEffects(backButton, "#7f8c8d");
    }

    private void setupButtonHoverEffects(Button b, String hoverColor) {
        String original = b.getStyle();
        b.setOnMouseEntered(e -> b.setStyle(original + "; -fx-background-color: " + hoverColor + ";"));
        b.setOnMouseExited(e -> b.setStyle(original));
    }

    private void handleCreateGame() {
        // Creamos el usuario lo que en un futuro se hará con base de datos
        String name = playerNameInput.getText() == null ? "" : playerNameInput.getText().trim();
        Player player = new Player(name);
        // Forzamos ahora a que este lobby pertenezca al jugador que acaba de ser creado
        this.loggedInsideLobbyPlayer = player;
        this.usernameLabel.setText(player.getPlayerID());

        String questionsStr = questionCountInput.getText() == null ? "" : questionCountInput.getText().trim();
        String durationStr = durationInput.getText() == null ? "" : durationInput.getText().trim();
        String playersStr = playersInput.getText() == null ? "" : playersInput.getText().trim();
        String difficultyStr = difficultyCombo.getValue();
        String gameTypeStr = gameTypeCombo.getValue();

        boolean error = false;
        if (this.loggedInsideLobbyPlayer.getPlayerID().isEmpty()) { markError(playerNameInput); error = true; }
        int questionCount = parsePositiveInt(questionsStr, questionCountInput); if (questionCount == -1) error = true;
        int durationSeconds = parsePositiveInt(durationStr, durationInput); if (durationSeconds == -1) error = true;
        int maxPlayers = parsePositiveInt(playersStr, playersInput); if (maxPlayers == -1) error = true;
        if (difficultyStr == null || difficultyStr.isEmpty()) { markErrorCombo(difficultyCombo); error = true; }
        if (gameTypeStr == null || gameTypeStr.isEmpty()) { markErrorCombo(gameTypeCombo); error = true; }
        if (error) { showAlert("Error de validación", "Por favor, verifica que todos los campos sean válidos."); return; }

        String tempRoomCode = roomCodeInput.getText();

        // No añadimos la partida a la tabla aún: esperar al evento GameSessionCreatedEvent
        pendingHostsByTempCode.put(tempRoomCode, this.loggedInsideLobbyPlayer.getPlayerID());

        // Guardar el playerId del host para unirlo automáticamente cuando se cree la sesión
        pendingHostPlayerIdByTempCode.put(tempRoomCode, this.loggedInsideLobbyPlayer.getPlayerID());

        GamePlayerConfig config = new GamePlayerConfig();
        config.setPlayer(this.loggedInsideLobbyPlayer);
        config.setQuestionNumber(questionCount);
        config.setTimerSeconds(durationSeconds);
        config.setGameType(GameType.valueOf(gameTypeStr));
        config.setDifficultyLevel(QuestionLevel.valueOf(difficultyStr));
        config.setMaxPlayers(maxPlayers);

        // Publish game creation event to central bus
        eventBus.publish(new GameCreationRequestedEvent(config, tempRoomCode));
        //eventBus.publish(new PlayerJoinedEvent(player));

        statusLabel.setText("Partida creada - esperando jugadores");
        statusLabel.setStyle("-fx-text-fill: #27ae60;");
    }

    private void handleStartGame() {
        String roomCode = roomCodeInput.getText();
        if (roomCode == null || roomCode.trim().isEmpty()) {
            showAlert("Sin selección", "Por favor, selecciona una partida de la tabla.");
            return;
        }

        // Obtener el GameService de la sesión
        GameService gameService = sessionServices.get(roomCode);
        if (gameService == null) {
            showAlert("Error", "No se encontró la sesión de juego para el código: " + roomCode);
            return;
        }
        // Obtener la configuración del juego desde el GameGlobal
        var gameGlobal = gameService.getGameInstance();

        // Crear configuración del jugador usando los datos del GameGlobal
        GamePlayerConfig playerConfig = new GamePlayerConfig();
        playerConfig.setPlayer(this.loggedInsideLobbyPlayer);
        playerConfig.setRoomId(roomCode);
        playerConfig.setQuestionNumber(gameGlobal.getNumberOfQuestions());
        playerConfig.setTimerSeconds(gameGlobal.getGameDuration());
        playerConfig.setGameType(gameGlobal.getGameType());
        playerConfig.setDifficultyLevel(gameGlobal.getDifficulty());
        playerConfig.setMaxPlayers(gameGlobal.getMaxPlayers());

        statusLabel.setText("Iniciando partida " + roomCode);
        statusLabel.setStyle("-fx-text-fill: #27ae60;");

        // Publicar evento para solicitar iniciar el juego (roomId y username del que inicia)
        // GameSessionManager validará que sea el creador
        eventBus.publish(new GameStartedRequestEvent(roomCode, this.loggedInsideLobbyPlayer.getPlayerID()));

        // Navegar al juego usando el ViewNavigator
        if (navigator != null) {
            EventBus externalBus = gameService.getExternalBus();
            log.info("Navegando al juego con externalBus: {}", externalBus);
            navigator.showGame(playerConfig, externalBus);
        }

        log.info("Starting game with Room ID: {}", roomCode);
    }

    private void handleJoinGame() {
        GameLobbyEntry selectedGame = gamesTable.getSelectionModel().getSelectedItem();
        if (selectedGame == null) {
            showAlert("Sin selección", "Por favor, selecciona una partida de la tabla.");
            return;
        }

        String name = playerNameInput.getText() == null ? "" : playerNameInput.getText().trim();
        if (name.isEmpty()) {
            markError(playerNameInput);
            showAlert("Error de validación", "Por favor, ingresa tu nombre.");
            return;
        }

        // Create player and join event
        Player player = new Player(name);
        eventBus.publish(new PlayerJoinedEvent(player.getPlayerID(), selectedGame.getRoomCode()));

        statusLabel.setText("Uniéndose a partida " + selectedGame.getRoomCode());
        statusLabel.setStyle("-fx-text-fill: #3498db;");
    }

    private void handleBack() {
        if (navigator != null)
            navigator.showMenu();
    }

    @Override
    public void onEvent(GameEvent event) {
        if (event instanceof GameSessionCreatedEvent) {
            handleGameSessionCreated((GameSessionCreatedEvent) event);
        }
    }

    /**
     * Process game session creation event from GameSessionManager
     */
    private void handleGameSessionCreated(GameSessionCreatedEvent event) {
        String sessionId = event.getSessionId();
        String tempRoomCode = event.getTempRoomCode();
        log.info("Session created with ID: {} for temp room: {}", sessionId, tempRoomCode);

        // UI updates must run on JavaFX Application Thread
        Platform.runLater(() -> {
            // If the session already exists (info refresh), just select it to avoid duplicates
            GameLobbyEntry existingEntry = findGameEntry(sessionId);
            if (existingEntry != null) {
                suppressInfoRequest = true;
                gamesTable.getSelectionModel().select(existingEntry);
                suppressInfoRequest = false;
                log.info("Session {} already listed. Skipping duplicate add.", sessionId);
                return;
            }

            // Actualizar room id
            roomCodeInput.setText(sessionId);

            // Construir la entrada usando datos del servicio
            String hostName = pendingHostsByTempCode.getOrDefault(tempRoomCode, "Anfitrión");
            String difficulty = "";
            String gameType = "";
            int maxPlayersFromService = 0;
            int numberOfQuestions = 0;
            int gameDuration = 0;
            try {
                var service = event.getGameService();
                // Guardar la referencia al GameService para uso posterior
                if (service != null) {
                    sessionServices.put(sessionId, service);
                }
                var global = service != null ? service.getGameInstance() : null;
                if (global != null) {
                    difficulty = global.getDifficulty() != null ? global.getDifficulty().name() : "";
                    gameType = global.getGameType() != null ? global.getGameType().name() : "";
                    maxPlayersFromService = global.getMaxPlayers();
                    numberOfQuestions = global.getNumberOfQuestions();
                    gameDuration = global.getGameDuration();
                }
            } catch (Exception ignored) {}

            GameLobbyEntry newEntry = new GameLobbyEntry(sessionId, hostName, maxPlayersFromService, difficulty, gameType, numberOfQuestions, gameDuration);
            games.add(newEntry);
            // Evitar ciclo: la selección dispara GetGameSessionInfoEvent -> GameSessionCreatedEvent
            suppressInfoRequest = true;
            gamesTable.getSelectionModel().select(newEntry);
            suppressInfoRequest = false;

            // Unir automáticamente al host a la partida que acaba de crear
            String hostPlayerId = pendingHostPlayerIdByTempCode.get(tempRoomCode);
            if (hostPlayerId != null) {
                eventBus.publish(new PlayerJoinedEvent(hostPlayerId, sessionId));
                log.info("Host {} ({}) joined session {}", hostName, hostPlayerId, sessionId);
                pendingHostPlayerIdByTempCode.remove(tempRoomCode);
            }

            pendingHostsByTempCode.remove(tempRoomCode);
            log.info("Added new game entry for session {} (from temp {})", sessionId, tempRoomCode);
        });
    }

    private GameLobbyEntry findGameEntry(String roomCode) {
        if (roomCode == null) return null;
        return games.stream()
                .filter(entry -> roomCode.equals(entry.getRoomCode()))
                .findFirst()
                .orElse(null);
    }

    private void markError(TextField field) { field.setStyle(field.getStyle() + "; -fx-border-color: #e74c3c; -fx-border-width: 2px;"); }
    private void markErrorCombo(ComboBox<?> combo) { combo.setStyle(combo.getStyle() + "; -fx-border-color: #e74c3c; -fx-border-width: 2px;"); }

    private int parsePositiveInt(String value, TextField field) {
        try {
            int n = Integer.parseInt(value);
            if (n <= 0) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException ex) {
            markError(field);
            return -1;
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static class GameLobbyEntry {
        private String roomCode;
        private final String hostName;
        private final int maxPlayers;
        private final String difficulty;
        private final String gameType;
        private final int questionCount;
        private final int durationSeconds;
        private final ObservableList<PlayerLobbyEntry> players;

        public GameLobbyEntry(String roomCode, String hostName, int maxPlayers, String difficulty, String gameType, int questionCount, int durationSeconds) {
            this.roomCode = roomCode;
            this.hostName = hostName;
            this.maxPlayers = maxPlayers;
            this.difficulty = difficulty;
            this.gameType = gameType;
            this.questionCount = questionCount;
            this.durationSeconds = durationSeconds;
            this.players = FXCollections.observableArrayList();
            this.players.add(new PlayerLobbyEntry(hostName, "Anfitrión", 0, "00:00"));
        }

        public String getRoomCode() { return roomCode; }
        public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
        public String getHostName() { return hostName; }
        public ObservableList<PlayerLobbyEntry> getPlayers() { return players; }
        public String getDifficulty() { return difficulty; }
        public String getGameType() { return gameType; }
        public String getQuestionCount() { return questionCount > 0 ? String.valueOf(questionCount) : "-"; }
        public String getDurationSeconds() { return durationSeconds > 0 ? String.valueOf(durationSeconds) : "-"; }
        public String getPlayersSummary() { return maxPlayers > 0 ? players.size() + "/" + maxPlayers : players.size() + "/?"; }
        public String getPlayersList() {
            return players.isEmpty()
                    ? "Sin jugadores"
                    : players.stream()
                        .map(p -> p.getName() + " (" + p.getStatus() + ")")
                        .collect(Collectors.joining(", "));
        }
    }

    public static class PlayerLobbyEntry {
        private String name;
        private String status;
        private int score;
        private String time;

        public PlayerLobbyEntry(String name, String status, int score, String time) {
            this.name = name; this.status = status; this.score = score; this.time = time;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
    }
}
