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
                console.error(`View not found: ${viewId}`);
                return;
            }
            targetView.classList.remove('hidden');
            console.log(`View switched to: ${viewId}`);
        } catch (error) {
            console.error('Error switching view:', error);
        }
    }
};