# Plan de sincronización del lobby

## Objetivo
Hacer que cualquier cliente web pueda ver las partidas activas de la instancia del backend:

1. **al entrar al lobby** mediante un snapshot inicial,
2. **mientras permanece en el lobby** mediante broadcasts en tiempo real.

---

## Punto 1 — Snapshot inicial al conectar al lobby

### Objetivo
Cuando un usuario se autentica y entra al lobby, el backend le envía automáticamente la lista actual de partidas activas.

### Pasos
1. Definir el mensaje `LobbyMatchesSnapshot` con:
   - `roomId`
   - `name`
   - `players`
   - `maxPlayers`
   - `gameType`
   - `time`
   - `difficulty`

2. Construir el snapshot en `MatchesManager` usando `getActiveMatches()`.

3. Enviar ese snapshot desde `ConnectionHandler.onClientConnect()` justo después de registrar al jugador y añadirlo a `LobbyRoom`.

4. En `main.js`, procesar `LobbyMatchesSnapshot` para repintar `#games-list` con `addOnlineGameCard()`.

5. Verificar con dos clientes:
   - Cliente A crea una partida.
   - Cliente B entra después.
   - Cliente B debe verla sin refrescar.

---

## Punto 2 — Broadcast en tiempo real

### Objetivo
Cuando se crea una nueva partida, todos los clientes que ya están en el lobby la ven aparecer al momento.

### Pasos
1. Reutilizar el mismo payload del snapshot y definir `LobbyMatchCreated`.
2. Añadir un helper de broadcast en `LobbyRoom`.
3. Lanzar ese broadcast desde `MatchesManager.handleGameCreationRequested()` al registrar la nueva partida.
4. En `main.js`, hacer un upsert por `roomId` para no duplicar tarjetas.
5. Verificar con dos clientes ya dentro del lobby.

---

## Archivos clave
- `src/main/java/Apalabrazos/backend/service/MatchesManager.java`
- `src/main/java/Apalabrazos/backend/network/ConnectionHandler.java`
- `src/main/java/Apalabrazos/backend/lobby/LobbyRoom.java`
- `src/main/resources/public/js/main.js`

---

## Estado
- ✅ Plan documentado
- ✅ Punto 1 implementado
- ✅ Punto 2 implementado (broadcast de nuevas partidas en tiempo real)
