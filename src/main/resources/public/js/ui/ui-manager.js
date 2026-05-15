export const UIManager = {
    switchView(viewId) {
        try {
            // Hide all views
            document.querySelectorAll('.game-view').forEach(view => {
                view.classList.add('hidden');
            });

            // Show the target view
            const targetView = document.getElementById(viewId);
            if (!targetView) {
                console.error(`❌ View not found: ${viewId}`);
                return;
            }
            targetView.classList.remove('hidden');
            console.log(`View changed to: ${viewId}`);
        } catch (error) {
            console.error('Error changing view:', error);
        }
    }
};