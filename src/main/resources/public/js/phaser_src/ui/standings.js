import { InteractiveButton } from './interactiveButton.js';

export class Standings {
    /**
     * @param {Phaser.Scene} scene
     * @param {object} options
     * @param {number} options.rightEdgeX - x of the right edge (aligns with right answer buttons)
     * @param {number} options.topY       - y of the top edge
     * @param {number} [options.width]
     * @param {number} [options.height]
     */
    constructor(scene, options = {}) {
        this.scene = scene;

        this.width      = options.width      ?? 150;
        this.height     = options.height     ?? 200;
        this.rightEdgeX = options.rightEdgeX ?? (scene.scale.width - 20);
        this.topY       = options.topY       ?? 20;

        const centerX = this.rightEdgeX - this.width / 2;
        const centerY = this.topY + this.height / 2;

        this.panel = new InteractiveButton(
            this.scene, 'posiciones_panel',
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
                useHandCursor: false,
                reactive: false
            }
        );

        // "POSICIONES" label at the top, centered inside the panel
        const labelY = -this.height / 2 + 20;
        this.titleText = scene.add.text(0, labelY, 'POSICIONES', {
            fontSize: '26px',
            fontFamily: 'Archivo Black',
            color: '#000000'
        }).setOrigin(0.5, 0);
        this.panel.add(this.titleText);

        this.entriesText = scene.add.text(0, -this.height / 2 + 56, '', {
            fontSize: '16px',
            fontFamily: 'Archivo Black',
            color: '#000000',
            align: 'center',
            lineSpacing: 8
        }).setOrigin(0.5, 0);
        this.panel.add(this.entriesText);

        this.setEntries([]);
    }

    setEntries(entries = []) {
        const normalized = Array.isArray(entries) ? entries.slice(0, 3) : [];
        if (normalized.length === 0) {
            this.entriesText.setText('Sin datos');
            return;
        }

        const lines = [];
        normalized.forEach((entry, idx) => {
            const name = String(entry?.playerName || entry?.playerId || `Jugador ${idx + 1}`).trim();
            const score = Number(entry?.score);
            const safeScore = Number.isFinite(score) ? score : 0;
            lines.push(`${idx + 1}. ${name} - ${safeScore}`);
        });

        this.entriesText.setText(lines.join('\n'));
    }

    destroy() {
        if (this.entriesText) {
            this.entriesText.destroy();
            this.entriesText = null;
        }

        if (this.panel) {
            this.panel.destroy();
            this.panel = null;
        }
    }
}
