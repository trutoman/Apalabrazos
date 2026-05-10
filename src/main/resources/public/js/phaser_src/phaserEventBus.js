// js/phaser_src/phaserEventBus.js
//
// Central internal event bus for the Phaser game.
// Decouples network/backend messages from scene and UI logic.
//
// Usage — emit from the network bridge:
//   PhaserEventBus.emit('net:timerTick', { remaining: 42 });
//
// Usage — listen inside any Phaser scene or UI component:
//   PhaserEventBus.on('net:timerTick', ({ remaining }) => { ... });
//
// Convention for event name prefixes:
//   net:*   — events bridged in from the WebSocket / backend
//   ui:*    — events triggered by user interaction inside Phaser
//   game:*  — internal game-state transitions

export const PhaserEventBus = new Phaser.Events.EventEmitter();

const stickyEvents = new Map();

export function emitSticky(eventName, payload) {
	stickyEvents.set(eventName, payload);
	PhaserEventBus.emit(eventName, payload);
}

export function getSticky(eventName) {
	return stickyEvents.get(eventName);
}

export function clearSticky(eventName) {
	stickyEvents.delete(eventName);
}
