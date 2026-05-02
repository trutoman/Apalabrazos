import { InteractiveButton } from './interactiveButton.js';

export class Scoreboard {
    /**
     * @param {Phaser.Scene} scene
     * @param {object} options
     * @param {number} options.leftEdgeX  - x of the left edge (aligns with left answer buttons)
     * @param {number} options.topY       - y of the top edge
     * @param {number} [options.width]
     * @param {number} [options.height]
     */
    constructor(scene, options = {}) {
        this.scene = scene;

        this.width  = options.width  ?? 150;
        this.height = options.height ?? 200;
        this.leftEdgeX = options.leftEdgeX ?? 20;
        this.topY      = options.topY      ?? 20;

        const centerX = this.leftEdgeX + this.width / 2;
        const centerY = this.topY + this.height / 2;

        this.panel = new InteractiveButton(
            this.scene, 'scoreboard_panel',
            centerX, centerY,
            this.width, this.height,
            '', null,
            {
                type: 'irregular',
                circleColor: 0xF0F0F0,
                strokeColor: 0x000000,
                strokeWidth: 3,
                shadowColor: 0x000000,
                shadowAlpha: 1,
                shadowDepth: 6,
                useHandCursor: false
            }
        );

        // "Puntos" label at the top, centered inside the panel
        const labelY = -this.height / 2 + 20;
        this.titleText = scene.add.text(0, labelY, 'PUNTOS', {
            fontSize: '26px',
            fontFamily: 'Archivo Black',
            color: '#000000'
        }).setOrigin(0.5, 0);
        this.panel.add(this.titleText);
    }
}
