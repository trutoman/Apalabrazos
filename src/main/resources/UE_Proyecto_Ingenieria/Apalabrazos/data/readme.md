** PREGUNTAS **
<!-- TODO: Implementar identificación de letras para que el rosco cargue correctamente -->
<!-- NOTA: Faltan referencias en el JSON. Algo que identifique a la letra para que el rosco lo cargue -->

** CAMBIOS REALIZADOS - GAME CONTROLLER **
<!-- He reorganizado completamente la interfaz del GameController para mejorar la experiencia de juego -->
<!-- Los botones de opciones ahora están ubicados a los lados del rosco en lugar de abajo, lo que facilita la lectura de las preguntas -->
<!-- Cambié leftButtonsArea y rightButtonsArea por leftOptionsArea y rightOptionsArea para que los nombres sean más descriptivos -->
<!-- Corregí el tipo de questionArea que antes era VBox y ahora es GridPane para coincidir con el diseño del FXML -->
<!-- Añadí referencias a los Labels de contadores (correctCountLabel e incorrectCountLabel) para actualizar las estadísticas en tiempo real -->
<!-- Agregué un panel lateral (liveScoresPanel) para mostrar las puntuaciones en partidas multijugador, aunque de momento no está conectado al backend -->
<!-- Simplifiqué el método initialize() eliminando código redundante y haciendo los event handlers más concisos -->
<!-- El rosco ahora se crea en el postInitialize() para asegurar que la configuración del jugador está lista antes de dibujar los botones -->
<!-- Cambié la lógica de handleOptionSelected() para enviar "Opción X" en lugar del texto completo, evitando problemas con respuestas largas -->
<!-- Ahora al pulsar una opción se busca automáticamente la letra actual del rosco (la que tiene el estilo rosco-letter-current) -->
<!-- El handleAnswerValidated() ahora limpia el estilo rosco-letter-current de todos los botones antes de actualizar el color, evitando que se acumulen -->
<!-- Cambié rosco-letter-wrong por rosco-letter-incorrect en los estilos para mantener consistencia con el backend -->
<!-- Corregí el bug donde no se convertía la letra a minúsculas al buscar el botón en el mapa, causando que no se actualizara el color -->
<!-- El evento QuestionChangedEvent ahora limpia el estilo current de todos los botones antes de aplicarlo al nuevo, evitando múltiples letras destacadas -->
<!-- Añadí maximización automática de la ventana tanto en el Lobby como al cargar el GameController -->

** CAMBIOS REALIZADOS - GAME.FXML **
<!-- Reestructuré completamente el layout para que sea más limpio y moderno -->
<!-- Cambié de un GridPane con sidebar lateral a un StackPane con GridPane interno que ocupa toda la pantalla -->
<!-- El diseño ahora tiene 4 filas: cabecera pequeña, área principal del rosco (55%), zona de pregunta (30%), y contadores abajo (fija) -->
<!-- Moví los botones de opciones de respuesta a los laterales del rosco en lugar de mostrarlos como texto debajo -->
<!-- Los botones ahora muestran el texto completo de la respuesta y son interactivos, no solo etiquetas -->
<!-- Eliminé los Labels optionALabel, optionBLabel, etc. que ya no se usan porque las respuestas están en los botones -->
<!-- El rosco ahora está centrado con un botón PASAR en el medio que sustituye al botón EMPEZAR cuando arranca el juego -->
<!-- Añadí un panel de puntuaciones en vivo (liveScoresPanel) en la esquina inferior izquierda para ver los resultados de otros jugadores -->
<!-- Los contadores de aciertos y fallos ahora están en la parte inferior central con íconos ✓ y ✗ para mejor visibilidad -->
<!-- El timer está ahora junto a la pregunta en la sección central, con un tamaño más grande (50px) y color dorado -->
<!-- Quité el canvas del rival (rivalCanvas) y los botones de estadísticas que estaban en el sidebar porque no se usaban -->
<!-- Simplifiqué el diseño eliminando capas innecesarias de VBox y HBox que complicaban el posicionamiento -->
<!-- Los botones de opciones tienen ahora 200x70px con texto envuelto (wrapText) para que las respuestas largas se vean bien -->
<!-- Agregué styleClass option-answer-button a los botones de respuesta para aplicarles los nuevos estilos -->

** CAMBIOS REALIZADOS - STYLES.CSS **
<!-- Creé una nueva clase option-answer-button con gradiente azul oscuro (#003d7a a #001a3d) para los botones de respuesta -->
<!-- Los botones tienen efecto hover que cambia el borde a dorado (#FFD700) y aumenta ligeramente su tamaño -->
<!-- Añadí efecto de profundidad con dropshadow más pronunciada al hacer hover para dar feedback visual -->
<!-- El efecto pressed hace que el botón se desplace 2px hacia abajo simulando que se hunde -->
<!-- Renombré la clase rosco-letter-wrong a rosco-letter-incorrect para mantener consistencia con el enum del backend -->
<!-- Añadí la clase rosco-letter-skipped para las letras que se pasan con el botón pasapalabra (azul claro) -->
<!-- Moví los estilos del background de .main-grid-pane a .root para que la imagen de fondo se aplique globalmente -->
<!-- Creé clases option-container y option-letter-label aunque de momento no se usan (preparación para futuras mejoras) -->
<!-- Marqué los estilos .letter-button antiguos como DEPRECATED porque ahora usamos .option-answer-button -->
<!-- Los estilos .option-button legacy siguen disponibles por si acaso se necesitan en otras vistas -->

