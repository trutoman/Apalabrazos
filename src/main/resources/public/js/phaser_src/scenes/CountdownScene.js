function getCssColor(variableName, fallback) {
    const value = getComputedStyle(document.documentElement)
        .getPropertyValue(variableName)
        .trim();
    return value || fallback;
}

import { MatchAudio } from '../../audio/match-audio.js';
import { PhaserEventBus, getSticky } from '../phaserEventBus.js';

export class CountdownScene extends Phaser.Scene {
    constructor() {
        super('CountdownScene');
        this._countdownText = null;
        this._spinnerRing = null;
        this._spinnerContainer = null;
        this._countdownTween = null;
        this._spinnerRotateTween = null;
        this._spinnerColorTimer = null;
        this._isDone = false;
        this._countdownStarted = false;
        this._onResize = this._handleResize.bind(this);
        this._onQuestionChanged = this._handleQuestionChanged.bind(this);

        this._spinnerColors = ['#FF00F4', '#FADF09', '#00F0FF', '#A2FF00'];
        this._currentColorIndex = 0;
    }

    create() {
        const w = this.scale.width;
        const h = this.scale.height;

        const bgColor = getCssColor('--secondary', '#F0F0F0');
        const primaryColor = getCssColor('--primary', '#000000');

        this.cameras.main.setBackgroundColor(bgColor);

        this._spinnerContainer = this.add.container(w / 2, h / 2);
        this._spinnerRing = this.add.graphics();
        this._spinnerContainer.add(this._spinnerRing);
        this._redrawSpinner();
        this._startSpinnerAnimation();

        this._countdownText = this.add.text(w / 2, h / 2, '3', {
            fontFamily: 'Archivo Black, sans-serif',
            fontSize: `${Math.round(Math.min(w, h) * 0.42)}px`,
            color: '#FF00F4',
            align: 'center',
            stroke: primaryColor,
            strokeThickness: Math.max(12, Math.round(Math.min(w, h) * 0.022)),
        }).setOrigin(0.5);
        this._countdownText.setShadow(0, 0, primaryColor, 26, false, true);
        this._countdownText.setVisible(false);
        console.log('[SEQ][COUNTDOWN] Spinner visible, waiting for net:questionChanged...');

        PhaserEventBus.on('net:questionChanged', this._onQuestionChanged);
        this.scale.on('resize', this._onResize);

        if (getSticky('net:questionChanged')) {
            console.log('[SEQ][COUNTDOWN] Sticky questionChanged found. Starting numeric countdown now.');
            this._beginNumericCountdown();
        }
    }

    _drawSpinner(graphics, radius, thickness) {
        graphics.clear();
        const color = Phaser.Display.Color.HexStringToColor(
            this._spinnerColors[this._currentColorIndex]
        ).color;

        graphics.lineStyle(thickness, color, 1);
        for (let i = 0; i < 4; i++) {
            const startAngle = Phaser.Math.DegToRad(i * 90 + 10);
            const endAngle = Phaser.Math.DegToRad((i + 1) * 90 - 18);
            graphics.beginPath();
            graphics.arc(0, 0, radius, startAngle, endAngle, false);
            graphics.strokePath();
        }
    }

    _startSpinnerAnimation() {
        this._spinnerRotateTween = this.tweens.add({
            targets: this._spinnerContainer,
            rotation: Math.PI * 2,
            duration: 2000,
            ease: 'Linear',
            repeat: -1,
        });

        this._spinnerColorLoop();
    }

    _spinnerColorLoop() {
        this._spinnerColorTimer = this.time.delayedCall(500, () => {
            if (this._countdownStarted || this._isDone) {
                return;
            }
            this._currentColorIndex = (this._currentColorIndex + 1) % this._spinnerColors.length;
            this._redrawSpinner();
            this._spinnerColorLoop();
        });
    }

    _redrawSpinner() {
        if (!this._spinnerRing) {
            return;
        }

        const w = this.scale.width;
        const h = this.scale.height;
        const radius = Math.round(Math.min(w, h) * 0.24);
        const thickness = Math.max(18, Math.round(Math.min(w, h) * 0.04));

        this._drawSpinner(this._spinnerRing, radius, thickness);
    }

    _handleQuestionChanged() {
        if (this._isDone || this._countdownStarted) {
            return;
        }
        console.log('[SEQ][COUNTDOWN] net:questionChanged received. Switching spinner -> numeric countdown.');
        this._beginNumericCountdown();
    }

    _beginNumericCountdown() {
        if (this._countdownStarted || this._isDone) {
            return;
        }

        this._countdownStarted = true;

        if (this._spinnerRotateTween) {
            this._spinnerRotateTween.stop();
            this._spinnerRotateTween = null;
        }
        if (this._spinnerColorTimer) {
            this._spinnerColorTimer.remove();
            this._spinnerColorTimer = null;
        }

        if (this._spinnerContainer) {
            this._spinnerContainer.setVisible(false);
        }
        if (this._countdownText) {
            this._countdownText.setVisible(true);
        }

        console.log('[SEQ][COUNTDOWN] Numeric countdown started (3,2,1).');

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

        console.log('[SEQ][COUNTDOWN] Step', value);

        this._countdownText
            .setText(String(value))
            .setColor(this._getNumberColor(value));
        this._countdownText.setScale(0.25);
        this._countdownText.setAlpha(0);

        MatchAudio.playCountdownPumSfx();

        this._countdownTween = this.tweens.add({
            targets: this._countdownText,
            alpha: 1,
            scale: 1.0,
            duration: 220,
            ease: 'Back.Out',
            onComplete: () => {
                this._countdownTween = this.tweens.add({
                    targets: this._countdownText,
                    scale: 1.32,
                    duration: 260,
                    ease: 'Sine.Out',
                    onComplete: () => {
                        this._countdownTween = this.tweens.add({
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

        if (this._countdownTween) {
            this._countdownTween.stop();
            this._countdownTween = null;
        }
        if (this._spinnerRotateTween) {
            this._spinnerRotateTween.stop();
            this._spinnerRotateTween = null;
        }
        if (this._spinnerColorTimer) {
            this._spinnerColorTimer.remove();
            this._spinnerColorTimer = null;
        }

        const onCountdownComplete = this.registry.get('onCountdownComplete');
        if (typeof onCountdownComplete === 'function') {
            onCountdownComplete();
        }

        this.scene.start('MainScene');
        console.log('[SEQ][COUNTDOWN] MainScene started.');
    }

    _handleResize(gameSize) {
        const w = gameSize.width;
        const h = gameSize.height;

        if (this._spinnerContainer) {
            this._spinnerContainer.setPosition(w / 2, h / 2);
        }
        if (this._countdownText) {
            this._countdownText
                .setPosition(w / 2, h / 2)
                .setFontSize(Math.round(Math.min(w, h) * 0.42));
        }

        this._redrawSpinner();
    }

    shutdown() {
        if (this._countdownTween) {
            this._countdownTween.stop();
            this._countdownTween = null;
        }
        if (this._spinnerRotateTween) {
            this._spinnerRotateTween.stop();
            this._spinnerRotateTween = null;
        }
        if (this._spinnerColorTimer) {
            this._spinnerColorTimer.remove();
            this._spinnerColorTimer = null;
        }

        PhaserEventBus.off('net:questionChanged', this._onQuestionChanged);
        this.scale.off('resize', this._onResize);
    }
}
