export const UIManager = {
    switchView(viewId) {
        // Ocultar todas las vistas
        document.querySelectorAll('.game-view').forEach(view => {
            view.classList.add('hidden');
        });
        // Mostrar la que queremos
        document.getElementById(viewId).classList.remove('hidden');
    }
};