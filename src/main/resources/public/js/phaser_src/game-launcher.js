import { MainScene } from './scenes/MainScene.js';
import { CountdownScene } from './scenes/CountdownScene.js';

let gameInstance = null;

export function startGame(containerId, options = {}) {
    if (gameInstance) {
        gameInstance.destroy(true);
        gameInstance = null;
    }

    const onCountdownComplete = typeof options?.onCountdownComplete === 'function'
        ? options.onCountdownComplete
        : null;

    const config = {
        type: Phaser.AUTO,
        parent: containerId,
        width: 1280,
        height: 720,
        backgroundColor: '#F0F0F0',
        scale: {
            mode: Phaser.Scale.FIT,
            autoCenter: Phaser.Scale.CENTER_BOTH
        },
        scene: [CountdownScene, MainScene],
        callbacks: {
            postBoot(game) {
                game.registry.set('onCountdownComplete', onCountdownComplete);
            }
        }
    };

    gameInstance = new Phaser.Game(config);
    return gameInstance;
}

export function destroyGame() {
    if (gameInstance) {
        gameInstance.destroy(true);
        gameInstance = null;
    }
}
