import { InteractiveButton } from './interactiveButton.js';

export class GameOverPopup {
    constructor(scene, options = {}) {
        this.scene = scene;
        this.centerX = options.centerX ?? (this.scene.scale.width / 2);
        this.centerY = options.centerY ?? (this.scene.scale.height / 2);
        this.isVisible = false;
        this.container = null;
        this.gameOverText = null;
        this.winnerText = null;
        this.showWinnerTimer = null;
        this.hideTimer = null;
    }

    /**
     * Show the "Game Over" popup with large text
     */
    showGameOver() {
        if (this.isVisible) return;
        this.isVisible = true;

        // Destroy any existing containers
        if (this.container) {
            this.container.destroy();
        }

        // Create background (semi-transparent black rectangle)
        const bg = this.scene.add.rectangle(
            this.centerX,
            this.centerY,
            this.scene.scale.width,
            this.scene.scale.height,
            0x000000,
            0.5
        );
        bg.setDepth(1000);

        // Create main popup container
        this.container = this.scene.add.container(this.centerX, this.centerY);
        this.container.setDepth(1001);

        // Create irregular popup background (like standings/scores)
        const popupWidth = 600;
        const popupHeight = 300;
        const w = popupWidth / 2;
        const h = popupHeight / 2;
        const s = Math.round(Math.min(w, h) * 0.12);
        const r = () => (Math.random() * 2 - 1) * s;

        const pts = [
            { x: -w + Math.abs(r()), y: -h - Math.abs(r()) },
            { x: w + Math.abs(r()), y: -h + r() },
            { x: w - Math.abs(r()), y: h + Math.abs(r()) },
            { x: -w - Math.abs(r()), y: h + r() },
        ];

        // Draw popup background
        const popup = this.scene.add.graphics();
        popup.fillStyle(0xfadf09, 1); // Same yellow as buttons
        popup.fillPoints(pts, true, true);
        popup.lineStyle(3, 0x000000, 1);
        popup.strokePoints(pts, true, true);

        this.container.add(popup);

        // "GAME OVER" text
        this.gameOverText = this.scene.add.text(0, -50, 'GAME OVER', {
            fontSize: '72px',
            fontFamily: 'Archivo Black',
            color: '#000000',
            align: 'center'
        }).setOrigin(0.5);
        this.gameOverText.setStroke('#ff0000', 3); // Red outline
        this.container.add(this.gameOverText);

        this.container.add(bg);

        // Set timer to show winner after 5 seconds
        this.showWinnerTimer = this.scene.time.delayedCall(5000, () => {
            this.showWinner();
        });
    }

    /**
     * Show the winner after game over
     * @param {string} winnerName - Name of the winner
     */
    showWinner() {
        if (!this.isVisible || !this.container) return;

        // Fade out "GAME OVER" text
        this.scene.tweens.add({
            targets: this.gameOverText,
            alpha: 0,
            duration: 500,
            ease: 'Power2.Out'
        });

        // Create winner text (after GAME OVER fades)
        this.scene.time.delayedCall(600, () => {
            this.winnerText = this.scene.add.text(0, 0, 'GANADOR', {
                fontSize: '48px',
                fontFamily: 'Archivo Black',
                color: '#000000',
                align: 'center'
            }).setOrigin(0.5);

            this.container.add(this.winnerText);

            // Fade in winner text
            this.winnerText.setAlpha(0);
            this.scene.tweens.add({
                targets: this.winnerText,
                alpha: 1,
                duration: 500,
                ease: 'Power2.Out'
            });
        });

        // Auto-hide after 5 more seconds
        this.hideTimer = this.scene.time.delayedCall(5000, () => {
            this.hide();
        });
    }

    /**
     * Hide the popup
     */
    hide() {
        if (!this.isVisible) return;
        this.isVisible = false;

        // Clear timers
        if (this.showWinnerTimer) {
            this.showWinnerTimer.remove();
            this.showWinnerTimer = null;
        }
        if (this.hideTimer) {
            this.hideTimer.remove();
            this.hideTimer = null;
        }

        // Fade out and destroy container
        if (this.container) {
            this.scene.tweens.add({
                targets: this.container,
                alpha: 0,
                duration: 500,
                ease: 'Power2.In',
                onComplete: () => {
                    if (this.container) {
                        this.container.destroy();
                        this.container = null;
                    }
                }
            });
        }
    }

    /**
     * Destroy the popup
     */
    destroy() {
        if (this.showWinnerTimer) {
            this.showWinnerTimer.remove();
            this.showWinnerTimer = null;
        }
        if (this.hideTimer) {
            this.hideTimer.remove();
            this.hideTimer = null;
        }
        if (this.container) {
            this.container.destroy();
            this.container = null;
        }
    }
}
