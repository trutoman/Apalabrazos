package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

/**
 * Empty event indicating the creator's start request has been validated.
 * GameController should start the game upon receiving this.
 */
public class CreatorInitGameEvent extends GameEvent {
    public CreatorInitGameEvent() {
        super();
    }
}
