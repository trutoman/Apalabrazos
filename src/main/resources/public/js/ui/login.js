export const LoginUI = {
    init(onLoginAttempt) {
        const form = document.getElementById('auth-form');
        form.addEventListener('submit', (e) => {
            e.preventDefault();
            const data = {
                user: document.getElementById('username').value,
                pass: document.getElementById('password').value
            };
            // No hace el login aquí, avisa al orquestador
            onLoginAttempt(data);
        });
    },

    showError(msg) {
        alert(msg); // O un div rojo bonito en el HTML
    }
};