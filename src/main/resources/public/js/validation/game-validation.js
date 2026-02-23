// js/validation/game-validation.js

import { GAME_OPTIONS } from '../config/game-options.js';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Returns the set of valid values for a GAME_OPTIONS array. */
function validValues(optionsList) {
    return new Set(optionsList.map(o => o.value));
}

// ---------------------------------------------------------------------------
// is_game_name_valid
//
// Rules:
//   1. Trim whitespace first (normalisation).
//   2. Length: 3–20 characters.
//   3. Allowed chars: letters (a-z A-Z), digits (0-9), single spaces, _ and -.
//   4. Must not start or end with a space.
//   5. No consecutive spaces.
//   6. No emojis / special symbols.
//
// Returns { valid: bool, error: string|null }
// ---------------------------------------------------------------------------
export function is_game_name_valid(rawName) {
    const name = (rawName ?? '').trim();

    if (name.length < 3) {
        return { valid: false, error: 'El nombre debe tener al menos 3 caracteres.' };
    }
    if (name.length > 20) {
        return { valid: false, error: 'El nombre no puede superar los 20 caracteres.' };
    }
    // Only letters, digits, spaces, dash and underscore
    if (!/^[a-zA-Z0-9 _-]+$/.test(name)) {
        return { valid: false, error: 'Solo se permiten letras, números, espacios, guion (-) y guion bajo (_).' };
    }
    // No consecutive spaces
    if (/  /.test(name)) {
        return { valid: false, error: 'No puede haber espacios consecutivos.' };
    }

    return { valid: true, error: null };
}

// ---------------------------------------------------------------------------
// validate_game_creation
//
// Reads the four <select> values + the game name and checks:
//   • Name passes is_game_name_valid.
//   • Every option value belongs to the allowed set from GAME_OPTIONS.
//
// Returns { valid: bool, errors: string[] }
// ---------------------------------------------------------------------------
export function validate_game_creation() {
    const errors = [];

    // --- Name ---
    const nameInput = document.getElementById('cfg-game-name');
    const nameResult = is_game_name_valid(nameInput?.value ?? '');
    if (!nameResult.valid) {
        errors.push(nameResult.error);
    }

    // --- Players ---
    const playersVal = document.getElementById('cfg-players')?.value;
    if (!validValues(GAME_OPTIONS.players).has(playersVal)) {
        errors.push(`Número de jugadores no válido: "${playersVal}".`);
    }

    // --- Game type ---
    const typeVal = document.getElementById('cfg-game-type')?.value;
    if (!validValues(GAME_OPTIONS.gameTypes).has(typeVal)) {
        errors.push(`Tipo de juego no válido: "${typeVal}".`);
    }

    // --- Time ---
    const timeVal = document.getElementById('cfg-time')?.value;
    if (!validValues(GAME_OPTIONS.times).has(timeVal)) {
        errors.push(`Tiempo no válido: "${timeVal}".`);
    }

    // --- Difficulty ---
    const diffVal = document.getElementById('cfg-difficulty')?.value;
    if (!validValues(GAME_OPTIONS.difficulties).has(diffVal)) {
        errors.push(`Dificultad no válida: "${diffVal}".`);
    }

    return { valid: errors.length === 0, errors };
}
