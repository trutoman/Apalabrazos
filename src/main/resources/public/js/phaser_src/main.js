import { MainScene } from './scenes/MainScene.js';

let gameInstance = null;

export function startGame(containerId) {
    if (gameInstance) {
        gameInstance.destroy(true);
        gameInstance = null;
    }

    const config = {
        type: Phaser.AUTO,
        parent: containerId,
        width: window.innerWidth,
        height: window.innerHeight,
        backgroundColor: '#F0F0F0',
        scale: {
            mode: Phaser.Scale.RESIZE,
            autoCenter: Phaser.Scale.CENTER_BOTH
        },
        scene: [MainScene]
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
