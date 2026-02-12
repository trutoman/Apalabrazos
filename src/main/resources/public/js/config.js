// js/config.js

export const API_BASE_URL = ""; // Set to full backend URL when needed, e.g. "https://api.example.com"
export const WS_BASE_URL = "ws://localhost:8080"; // Set to full WS base URL when needed, e.g. "wss://api.example.com"

export const API_ENDPOINTS = {
    login: "/api/login",
    register: "/api/register"
};

export const WS_ENDPOINTS = {
    game: "/game"
};

export function buildApiUrl(path) {
    return `${API_BASE_URL}${path}`;
}

export function buildWsUrl(path) {
    return `${WS_BASE_URL}${path}`;
}
