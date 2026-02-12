import { UIManager } from './ui-manager.js';
import { API_ENDPOINTS, buildApiUrl } from '../config.js';

export const LoginUI = {
    init(onLoginAttempt) {
        const form = document.getElementById('auth-form');
        form.addEventListener('submit', (e) => {
            e.preventDefault();
            const data = {
                user: document.getElementById('email').value,
                pass: document.getElementById('password').value
            };

            // Validate Password Complexity
            if (!this.validatePassword(data.pass, 'password')) {
                return;
            }

            // No hace el login aquí, avisa al orquestador
            onLoginAttempt(data);
        });

        // Navigation to Register
        const linkToRegister = document.getElementById('link-to-register');
        if (linkToRegister) {
            linkToRegister.addEventListener('click', (e) => {
                e.preventDefault();
                UIManager.switchView('view-register');
            });
        }

        // Navigation back to Login
        const linkToLogin = document.getElementById('link-to-login');
        if (linkToLogin) {
            linkToLogin.addEventListener('click', (e) => {
                e.preventDefault();
                UIManager.switchView('view-login');
            });
        }

        // Register form stub
        const registerForm = document.getElementById('register-form');
        if (registerForm) {
            registerForm.addEventListener('submit', (e) => {
                e.preventDefault();

                const email = document.getElementById('reg-email').value;
                const username = document.getElementById('reg-username').value;
                const password = document.getElementById('reg-password').value;
                const confirmPassword = document.getElementById('reg-confirm-password').value;

                // 1. Validate Email
                const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
                if (!emailRegex.test(email)) {
                    LoginUI.showError("Por favor, introduce un email válido.", "reg-email");
                    return;
                }

                // 2. Validate Password Complexity
                // Min 8 chars, at least one letter and one number
                if (!LoginUI.validatePassword(password, 'reg-password')) {
                    return;
                }

                // 3. Validate Password Match
                if (password !== confirmPassword) {
                    LoginUI.showError("Las contraseñas no coinciden.", ["reg-password", "reg-confirm-password"]);
                    return;
                }

                // 4. Send Registration Request
                const registerUrl = buildApiUrl(API_ENDPOINTS.register);

                fetch(registerUrl, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        username: username,
                        email: email,
                        password: password
                    })
                })
                    .then(response => {
                        if (response.ok) {
                            return response.json();
                        } else {
                            return response.json().then(data => {
                                throw new Error(data.message || "Error en el registro");
                            }).catch(e => {
                                // Fallback if response is not JSON or other error
                                throw new Error(e.message || "Error desconocido en el registro");
                            });
                        }
                    })
                    .then(data => {
                        alert("Registro exitoso! Por favor, inicia sesión.");
                        UIManager.switchView('view-login');
                    })
                    .catch(error => {
                        console.error("Registration error:", error);
                        LoginUI.showError(error.message || "No se pudo conectar con el servidor.");
                    });
            });
        }
    },

    showError(msg, targetIds = []) {
        alert(msg);
        const ids = Array.isArray(targetIds) ? targetIds : [targetIds];
        ids.forEach(id => {
            const el = document.getElementById(id);
            if (el) {
                el.classList.add('input-error');
                // Remove error class when user starts typing
                el.addEventListener('input', () => el.classList.remove('input-error'), { once: true });
            }
        });
    },

    validatePassword(password, inputIds = []) {
        const passwordRegex = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,}$/;
        if (!passwordRegex.test(password)) {
            this.showError("Minimum 8 characters, letters and numbers.", inputIds);
            return false;
        }
        return true;
    }
};