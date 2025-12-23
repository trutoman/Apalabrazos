package UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller;

import UE_Proyecto_Ingenieria.Apalabrazos.frontend.ViewNavigator;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.EventBus;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.EventListener;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.GameEvent;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.GameCreationRequestedEvent;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.GameSessionCreatedEvent;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.PlayerJoinedEvent;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.EventBusRegistry;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.GamePlayerConfig;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Player;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.GameType;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.QuestionLevel;
import javafx.fxml.FXML;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.application.Platform;

import java.util.Random;
import java.util.Map;
import java.util.HashMap;

public class LobbyController implements EventListener {

    @FXML private ImageView profileImage;
    @FXML private Label usernameLabel;
    @FXML private TextField playerNameInput;
    @FXML private ComboBox<String> difficultyCombo;
    @FXML private ComboBox<String> gameTypeCombo;
    @FXML private TextField questionCountInput;
    @FXML private TextField durationInput;
    @FXML private TextField roomCodeInput;
    @FXML private Label statusLabel;
    @FXML private TableView<GameLobbyEntry> gamesTable;
    @FXML private TableColumn<GameLobbyEntry, String> gameRoomColumn;
    @FXML private TableColumn<GameLobbyEntry, String> gameHostColumn;
    @FXML private TableColumn<GameLobbyEntry, String> gamePlayersColumn;
    @FXML private TableView<PlayerLobbyEntry> selectedPlayersTable;
    @FXML private TableColumn<PlayerLobbyEntry, String> selectedPlayerNameColumn;
    @FXML private TableColumn<PlayerLobbyEntry, String> selectedPlayerStatusColumn;
    @FXML private Button startButton;
    @FXML private Button backButton;

    private ViewNavigator navigator;
    private EventBus eventBus;
    private String username = "Jugador1";
    private final int maxPlayers = 4;
    private ObservableList<GameLobbyEntry> games;
    private final Map<String, GameLobbyEntry> pendingGames = new HashMap<>();
    private final Map<String, Player> pendingHostPlayers = new HashMap<>();

    public void setNavigator(ViewNavigator navigator) { this.navigator = navigator; }

    @FXML
    public void initialize() {
        eventBus = EventBus.getInstance();
        eventBus.addListener(this);
        usernameLabel.setText(username);
        setupComboBoxes();
        setupGamesTable();
        setupSelectedPlayersTable();
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
        gamePlayersColumn.setCellValueFactory(cd -> javafx.beans.binding.Bindings.createStringBinding(cd.getValue()::getPlayersSummary));

        gamesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> showSelectedGamePlayers(newSel));
        gamesTable.setPlaceholder(new Label("No hay partidas creadas"));
    }

    private void setupSelectedPlayersTable() {
        selectedPlayersTable.setItems(FXCollections.observableArrayList());
        selectedPlayerNameColumn.setCellValueFactory(cd -> javafx.beans.binding.Bindings.createStringBinding(cd.getValue()::getName));
        selectedPlayerStatusColumn.setCellValueFactory(cd -> javafx.beans.binding.Bindings.createStringBinding(cd.getValue()::getStatus));
        selectedPlayersTable.setPlaceholder(new Label("Selecciona una partida"));
    }

    private void setupEventHandlers() {
        startButton.setOnAction(e -> handleCreateGame());
        backButton.setOnAction(e -> handleBack());
        setupButtonHoverEffects(startButton, "#27ae60");
        setupButtonHoverEffects(backButton, "#7f8c8d");
    }

    private void setupButtonHoverEffects(Button b, String hoverColor) {
        String original = b.getStyle();
        b.setOnMouseEntered(e -> b.setStyle(original + "; -fx-background-color: " + hoverColor + ";"));
        b.setOnMouseExited(e -> b.setStyle(original));
    }

    private void generateRoomCode() {
        String code = "ROOM-" + String.format("%04d", new Random().nextInt(10000));
        roomCodeInput.setText(code);
    }

    private void showSelectedGamePlayers(GameLobbyEntry entry) {
        if (entry == null) {
            selectedPlayersTable.setItems(FXCollections.observableArrayList());
            selectedPlayersTable.setPlaceholder(new Label("Selecciona una partida"));
            statusLabel.setText("Selecciona una partida");
            statusLabel.setStyle("-fx-text-fill: #f39c12;");
            return;
        }

        selectedPlayersTable.setItems(entry.getPlayers());
        selectedPlayersTable.setPlaceholder(new Label("Sin jugadores todavía"));
        statusLabel.setText("Partida " + entry.getRoomCode() + " seleccionada");
        statusLabel.setStyle("-fx-text-fill: #3498db;");
    }

    private void handleCreateGame() {
        String name = playerNameInput.getText() == null ? "" : playerNameInput.getText().trim();
        String questionsStr = questionCountInput.getText() == null ? "" : questionCountInput.getText().trim();
        String durationStr = durationInput.getText() == null ? "" : durationInput.getText().trim();
        String difficultyStr = difficultyCombo.getValue();
        String gameTypeStr = gameTypeCombo.getValue();

        boolean error = false;
        if (name.isEmpty()) { markError(playerNameInput); error = true; }
        int questionCount = parsePositiveInt(questionsStr, questionCountInput); if (questionCount == -1) error = true;
        int durationSeconds = parsePositiveInt(durationStr, durationInput); if (durationSeconds == -1) error = true;
        if (difficultyStr == null || difficultyStr.isEmpty()) { markErrorCombo(difficultyCombo); error = true; }
        if (gameTypeStr == null || gameTypeStr.isEmpty()) { markErrorCombo(gameTypeCombo); error = true; }
        if (error) { showAlert("Error de validación", "Por favor, verifica que todos los campos sean válidos."); return; }

        String tempRoomCode = roomCodeInput.getText();
        GameLobbyEntry newGame = new GameLobbyEntry(tempRoomCode, name, maxPlayers);
        games.add(newGame);
        gamesTable.getSelectionModel().select(newGame);
        pendingGames.put(tempRoomCode, newGame);

        // Create config and publish event to MatchMakerService
        Player player = new Player(name);
        GamePlayerConfig config = new GamePlayerConfig();
        config.setPlayer(player);
        config.setQuestionNumber(questionCount);
        config.setTimerSeconds(durationSeconds);
        config.setGameType(GameType.valueOf(gameTypeStr));
        config.setDifficultyLevel(QuestionLevel.valueOf(difficultyStr));

        // Publish game creation event to central bus
        eventBus.publish(new GameCreationRequestedEvent(config, tempRoomCode));
        // Defer PlayerJoined until session bus exists; store host for later publish
        pendingHostPlayers.put(tempRoomCode, player);

        statusLabel.setText("Partida creada - esperando jugadores");
        statusLabel.setStyle("-fx-text-fill: #27ae60;");
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
        System.out.println("[LobbyController] Session created with ID: " + sessionId + " for temp room " + tempRoomCode);

        // UI updates must run on JavaFX Application Thread
        Platform.runLater(() -> {
            // Update room code input with the new session ID
            roomCodeInput.setText(sessionId);
            GameLobbyEntry entry = pendingGames.remove(tempRoomCode);
            if (entry != null) {
                entry.setRoomCode(sessionId);
                gamesTable.refresh();
                System.out.println("[LobbyController] Updated table entry from " + tempRoomCode + " to " + sessionId);
                // Publish host joined event to the per-session bus
                Player host = pendingHostPlayers.remove(tempRoomCode);
                EventBus sessionBus = EventBusRegistry.getInstance().getSessionBus(sessionId);
                if (host != null && sessionBus != null) {
                    sessionBus.publish(new PlayerJoinedEvent(host));
                }
            } else {
                System.out.println("[LobbyController] WARNING: No pending game found for temp code " + tempRoomCode);
            }
        });
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
        private final ObservableList<PlayerLobbyEntry> players;

        public GameLobbyEntry(String roomCode, String hostName, int maxPlayers) {
            this.roomCode = roomCode;
            this.hostName = hostName;
            this.maxPlayers = maxPlayers;
            this.players = FXCollections.observableArrayList();
            this.players.add(new PlayerLobbyEntry(hostName, "Anfitrión", 0, "00:00"));
        }

        public String getRoomCode() { return roomCode; }
        public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
        public String getHostName() { return hostName; }
        public ObservableList<PlayerLobbyEntry> getPlayers() { return players; }
        public String getPlayersSummary() { return players.size() + "/" + maxPlayers; }
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
