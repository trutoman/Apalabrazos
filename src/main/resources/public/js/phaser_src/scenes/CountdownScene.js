function getCssColor(variableName, fallback) {
    const value = getComputedStyle(document.documentElement)
        .getPropertyValue(variableName)
        .trim();
    return value || fallback;
}

export class CountdownScene extends Phaser.Scene {
    constructor() {
        super('CountdownScene');
        this._countdownText = null;
        this._currentTween = null;
        this._isDone = false;
        this._onResize = this._handleResize.bind(this);
    }

    create() {
        const w = this.scale.width;
        const h = this.scale.height;

        const bgColor = getCssColor('--secondary', '#F0F0F0');
        const primaryColor = getCssColor('--primary', '#000000');

        this.cameras.main.setBackgroundColor(bgColor);

        this._countdownText = this.add.text(w / 2, h / 2, '3', {
            fontFamily: 'Archivo Black, sans-serif',
            fontSize: `${Math.round(Math.min(w, h) * 0.42)}px`,
            color: '#FF00F4',
            align: 'center',
            stroke: primaryColor,
            strokeThickness: Math.max(12, Math.round(Math.min(w, h) * 0.022)),
        }).setOrigin(0.5);

        this._countdownText.setShadow(0, 0, primaryColor, 26, false, true);

        this.scale.on('resize', this._onResize);
        this._playStep(3);
    }

    _getNumberColor(value) {
        if (value === 3) return '#FF00F4';
        if (value === 2) return '#FADF09';
        if (value === 1) return '#00F0FF';
        return '#FF00F4';
    }

    _playStep(value) {
        if (this._isDone) {
            return;
        }

        if (value <= 0) {
            this._finishCountdown();
            return;
        }

        this._countdownText
            .setText(String(value))
            .setColor(this._getNumberColor(value));
        this._countdownText.setScale(0.25);
        this._countdownText.setAlpha(0);

        this._currentTween = this.tweens.add({
            targets: this._countdownText,
            alpha: 1,
            scale: 1.0,
            duration: 220,
            ease: 'Back.Out',
            onComplete: () => {
                this._currentTween = this.tweens.add({
                    targets: this._countdownText,
                    scale: 1.32,
                    duration: 260,
                    ease: 'Sine.Out',
                    onComplete: () => {
                        this._currentTween = this.tweens.add({
                            targets: this._countdownText,
                            alpha: 0,
                            scale: 1.55,
                            duration: 220,
                            ease: 'Sine.In',
                            onComplete: () => this._playStep(value - 1),
                        });
                    },
                });
            },
        });
    }

    _finishCountdown() {
        if (this._isDone) {
            return;
        }
        this._isDone = true;

        const onCountdownComplete = this.registry.get('onCountdownComplete');
        if (typeof onCountdownComplete === 'function') {
            onCountdownComplete();
        }

        this.scene.start('MainScene');
    }

    _handleResize(gameSize) {
        if (!this._countdownText) {
            return;
        }

        const w = gameSize.width;
        const h = gameSize.height;
        this._countdownText
            .setPosition(w / 2, h / 2)
            .setFontSize(Math.round(Math.min(w, h) * 0.42));
    }

    shutdown() {
        if (this._currentTween) {
            this._currentTween.stop();
            this._currentTween = null;
        }
        this.scale.off('resize', this._onResize);
    }
}
