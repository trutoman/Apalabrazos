package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

public class GamePlayerConfig {

    private String playerName;
    private String imageResource; // Can be a local path or a URL
    private int timerSeconds;

    public GamePlayerConfig() {
        this.playerName = "Jugador1";
        this.imageResource = "resources/images/default-profile.png";
        this.timerSeconds = 180;
    }

    public GamePlayerConfig(String playerName, String imageResource, int timerSeconds) {
        this.playerName = playerName;
        this.imageResource = imageResource;
        this.timerSeconds = timerSeconds;
    }
    public String getPlayerName() {
        return playerName;
    }
    public String getImageResource() {
        return imageResource;
    }
    public int getTimerSeconds() {
        return timerSeconds;
    }
}