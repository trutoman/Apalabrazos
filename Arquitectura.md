# Arquitectura y Funcionamiento del Programa Apalabrazos

## Descripción General

Apalabrazos es una aplicación de juego multijugador en línea desarrollada en Java, utilizando un backend basado en Javalin y un frontend web con HTML, CSS y JavaScript. El juego permite a los usuarios registrarse, iniciar sesión, crear o unirse a salas de juego (lobbies) y participar en partidas de preguntas y respuestas en tiempo real.

## Arquitectura del Sistema

### Arquitectura General

La aplicación sigue una arquitectura cliente-servidor con comunicación en tiempo real mediante WebSockets. Se divide en tres capas principales:

1. **Capa de Presentación (Frontend)**
2. **Capa de Lógica de Negocio (Backend)**
3. **Capa de Datos**

### 1. Capa de Presentación (Frontend)

**Tecnologías:**
- HTML5
- CSS3
- JavaScript (ES6 Modules)

**Estructura:**
- `index.html`: Página principal que contiene todas las vistas (login, registro, lobby, juego)
- `css/`: Hojas de estilo para diferentes vistas (style.css, login.css, register.css, lobby.css)
- `js/`: Lógica del cliente dividida en módulos:
  - `main.js`: Punto de entrada principal
  - `config.js`: Configuraciones de API y WebSocket
  - `ui/`: Módulos de interfaz de usuario (login.js, lobby.js, ui-manager.js)
  - `network/`: Cliente WebSocket (socket-client.js)
  - `validation/`: Validaciones del lado cliente (game-validation.js)

**Funcionalidad:**
- Interfaz de usuario para login y registro
- Gestión de lobbies y creación de partidas
- Comunicación en tiempo real con el servidor vía WebSocket
- Validación de formularios y datos del juego

### 2. Capa de Lógica de Negocio (Backend)

**Tecnologías:**
- Java 21 (LTS)
- Maven para gestión de dependencias
- Javalin: Framework web ligero con soporte nativo para WebSocket
- Azure Cosmos DB para persistencia de datos
- JWT para autenticación
- SLF4J + Logback para logging
- JUnit 5 para pruebas

**Componentes Principales:**

#### Servidor WebSocket
- `EmbeddedWebSocketServer`: Servidor embebido que maneja conexiones WebSocket en el puerto 8080
- `JavalinConnectionHandler`: Maneja las conexiones WebSocket individuales

#### Sistema de Eventos
- `EventBus`: Bus de eventos síncrono para comunicación entre componentes
- `AsyncEventBus`: Bus de eventos asíncrono para operaciones no bloqueantes
- Eventos específicos: `AnswerSubmittedEvent`, `GameControllerReady`, `CreatorInitGameEvent`, etc.

#### Servicios
- `MatchesManager`: Singleton que gestiona todas las sesiones de juego activas
- `GameService`: Lógica específica de cada partida
- `UserRepository`: Interfaz con Azure Cosmos DB para gestión de usuarios

#### Configuración
- `CosmosDBConfig`: Configuración de conexión a Cosmos DB
- `JwtConfig`: Configuración de tokens JWT

#### Modelos y DTOs
- `User`, `Player`: Modelos de datos para usuarios y jugadores
- `LoginRequest`, `RegisterRequest`: Objetos de transferencia de datos

### 3. Capa de Datos

**Tecnologías:**
- Azure Cosmos DB: Base de datos NoSQL en la nube
- JSON: Archivos locales para preguntas del juego (`questions.json`, `questions2.json`)

**Funcionalidad:**
- Persistencia de usuarios y datos de sesión
- Almacenamiento de preguntas y configuraciones del juego

## Funcionamiento del Programa

### Flujo de Ejecución

1. **Inicio de la Aplicación:**
   - Se ejecuta `MainApp.main()` que inicializa el `MatchesManager` singleton
   - Se crea y arranca el `EmbeddedWebSocketServer` en el puerto 8080
   - El servidor sirve archivos estáticos del frontend y maneja conexiones WebSocket

2. **Registro/Login de Usuario:**
   - El usuario accede a la interfaz web
   - Envía credenciales vía WebSocket
   - El backend valida contra Cosmos DB y genera token JWT
   - Se establece sesión autenticada

3. **Creación/Unión a Partida:**
   - Usuario crea una nueva partida o se une a una existente
   - `MatchesManager` registra la conexión y crea instancia de `GameService`
   - Se inicializa el bus de eventos para la partida

4. **Desarrollo del Juego:**
   - Las preguntas se cargan desde archivos JSON
   - Los jugadores responden en tiempo real
   - Eventos se propagan a través del `AsyncEventBus`
   - El servidor actualiza el estado del juego y notifica a todos los participantes

5. **Finalización:**
   - Al terminar la partida, se limpian recursos
   - Conexiones se cierran apropiadamente

### Comunicación en Tiempo Real

- **WebSocket**: Protocolo principal para comunicación bidireccional
- **Eventos**: Sistema interno para desacoplar componentes
- **JSON**: Formato de mensajes entre cliente y servidor

### Seguridad

- Autenticación basada en JWT
- Hashing de contraseñas
- Validación de entrada en cliente y servidor

### Escalabilidad

- Arquitectura basada en eventos permite distribución horizontal
- Singleton `MatchesManager` centraliza gestión de sesiones
- WebSocket nativo en Javalin optimiza rendimiento

## Dependencias Principales

- **Javalin 6.1.3**: Framework web y WebSocket
- **Azure Cosmos DB 4.55.0**: Base de datos
- **Jackson 2.15.2**: Serialización JSON
- **JWT**: Autenticación
- **SLF4J + Logback**: Logging
- **JUnit 5.9.2**: Pruebas unitarias

## Configuración y Despliegue

- **Puerto**: 8080 (configurable)
- **Base de datos**: Azure Cosmos DB (requiere configuración de conexión)
- **Recursos estáticos**: Servidos desde `src/main/resources/public/`
- **Compilación**: Maven con Java 21

Esta arquitectura permite un juego fluido y responsivo, con separación clara de responsabilidades y facilidad de mantenimiento.