export const UIManager = {
    switchView(viewId) {
        try {
            // Ocultar todas las vistas
            document.querySelectorAll('.game-view').forEach(view => {
                view.classList.add('hidden');
            });
            
            // Mostrar la que queremos
            const targetView = document.getElementById(viewId);
            if (!targetView) {
                console.error(`❌ Vista no encontrada: ${viewId}`);
                return;
            }
            targetView.classList.remove('hidden');
            console.log(`✅ Vista cambiada a: ${viewId}`);
        } catch (error) {
            console.error('Error al cambiar vista:', error);
        }
    }
};