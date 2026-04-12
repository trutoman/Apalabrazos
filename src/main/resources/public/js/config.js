// js/config.js

// Auto-detect protocol and host for both API and WebSocket
const isHttps = window.location.protocol === 'https:';
const host = window.location.hostname;
const port = window.location.port || (isHttps ? 443 : 80);

export const API_BASE_URL = ""; // Relative URLs work better
export const WS_BASE_URL = `${isHttps ? 'wss' : 'ws'}://${host}${port && port !== (isHttps ? 443 : 80) ? ':' + port : ''}`;

export const API_ENDPOINTS = {
    login: "/api/login",
    register: "/api/register"
};

export const WS_ENDPOINTS = {
    game: "/ws/game"
};

export function buildApiUrl(path) {
    return `${API_BASE_URL}${path}`;
}

export function buildWsUrl(path) {
    return `${WS_BASE_URL}${path}`;
}
