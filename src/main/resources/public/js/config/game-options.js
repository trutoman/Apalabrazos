// js/config/game-options.js
// Single source of truth for all create-game configuration options.

export const GAME_OPTIONS = {

    players: [
        { value: '2', label: '2' },
        { value: '3', label: '3' },
        { value: '4', label: '4' },
        { value: '5', label: '5' },
        { value: '6', label: '6' },
        { value: '7', label: '7' },
        { value: '8', label: '8' },
    ],

    gameTypes: [
        { value: 'CLASSIC', label: 'Classic' },
        { value: 'DOMINIO', label: 'Dominio' },
        { value: 'META', label: 'Meta' },
    ],

    difficulties: [
        { value: 'EASY', label: 'Easy' },
        { value: 'MEDIUM', label: 'Medium', default: true },
        { value: 'HARD', label: 'Hard' },
    ],

    // Time in minutes. Value is stored as a number; label shows friendly text.
    times: [
        { value: '0.5', label: '30 s' },
        { value: '1', label: '1 min' },
        { value: '2', label: '2 min' },
        { value: '3', label: '3 min' },
        { value: '5', label: '5 min', default: true },
        { value: '7', label: '7 min' },
        { value: '10', label: '10 min' },
    ],
};
