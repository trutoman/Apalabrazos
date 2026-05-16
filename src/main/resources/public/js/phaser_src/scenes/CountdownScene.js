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
        this._waitingElement = null;
        this._countdownTween = null;
        this._waitingElementRotateTween = null;
        this._waitingElementTimer = null;
        this._isDone = false;
        this._countdownStarted = false;
        this._onQuestionChanged = this._handleQuestionChanged.bind(this);
        this._elementGroupIndex = 0;
        this._elementFrameGroups = [
            [0, 1, 2],
            [3, 4, 5],
            [6, 7, 8],
            [9, 10, 11],
            [12, 13, 14],
        ];
    }

    preload() {
        this.load.spritesheet('neobrutalism-elements', 'assets/neobrutalism_elements.png', {
            frameWidth: 1000,
            frameHeight: 1000,
        });
    }

    create() {
        const w = this.scale.width;
        const h = this.scale.height;

        const bgColor = getCssColor('--secondary', '#F0F0F0');
        const primaryColor = getCssColor('--primary', '#000000');

        this.cameras.main.setBackgroundColor(bgColor);

        this._waitingElement = this.add.sprite(w / 2, h / 2, 'neobrutalism-elements', 0)
            .setOrigin(0.5);
        this._applyWaitingElementLayout();
        this._startWaitingElementAnimation();

        this._countdownText = this.add.text(w / 2, h / 2, '3', {
            fontFamily: 'Archivo Black, sans-serif',
            fontSize: `${Math.round(Math.min(w, h) * 0.42)}px`,
            color: '#FF00F4',
            align: 'center',
            stroke: primaryColor,
            strokeThickness: Math.max(12, Math.round(Math.min(w, h) * 0.022)),
        }).setOrigin(0.5);
        this._applyCountdownTextStyle();
        this._countdownText.setVisible(false);
        console.log('[SEQ][COUNTDOWN] Waiting element visible, waiting for net:questionChanged...');

        PhaserEventBus.on('net:questionChanged', this._onQuestionChanged);

        if (getSticky('net:questionChanged')) {
            console.log('[SEQ][COUNTDOWN] Sticky questionChanged found. Skipping countdown, starting game now.');
            // this._beginNumericCountdown();
            this._finishCountdown();
        }
    }

    _startWaitingElementAnimation() {
        this._showRandomFrameFromCurrentGroup();

        this._waitingElementRotateTween = this.tweens.add({
            targets: this._waitingElement,
            rotation: Math.PI * 2,
            duration: 2000,
            ease: 'Linear',
            repeat: -1,
        });

        this._scheduleNextWaitingElementFrame();
    }

    _scheduleNextWaitingElementFrame() {
        this._waitingElementTimer = this.time.delayedCall(666, () => {
            if (this._countdownStarted || this._isDone) {
                return;
            }

            this._elementGroupIndex = (this._elementGroupIndex + 1) % this._elementFrameGroups.length;
            this._showRandomFrameFromCurrentGroup();
            this._scheduleNextWaitingElementFrame();
        });
    }

    _showRandomFrameFromCurrentGroup() {
        if (!this._waitingElement) {
            return;
        }

        const group = this._elementFrameGroups[this._elementGroupIndex];
        const frame = Phaser.Utils.Array.GetRandom(group);
        this._waitingElement.setFrame(frame);
    }

    _applyWaitingElementLayout() {
        if (!this._waitingElement) {
            return;
        }

        const size = Math.min(500, this.scale.width * 0.72, this.scale.height * 0.72);
        this._waitingElement.setDisplaySize(size, size);
    }

    _handleQuestionChanged() {
        if (this._isDone || this._countdownStarted) {
            return;
        }
        console.log('[SEQ][COUNTDOWN] net:questionChanged received. Skipping countdown, starting game directly.');
        // this._beginNumericCountdown();
        this._finishCountdown();
    }

    _beginNumericCountdown() {
        if (this._countdownStarted || this._isDone) {
            return;
        }

        this._countdownStarted = true;

        if (this._waitingElementRotateTween) {
            this._waitingElementRotateTween.stop();
            this._waitingElementRotateTween = null;
        }
        if (this._waitingElementTimer) {
            this._waitingElementTimer.remove();
            this._waitingElementTimer = null;
        }

        if (this._waitingElement) {
            this._waitingElement.setVisible(false);
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

    _applyCountdownTextStyle() {
        if (!this._countdownText) {
            return;
        }

        const minSide = Math.min(this.scale.width, this.scale.height);
        const stroke = Math.max(12, Math.round(minSide * 0.022));
        const shadowOffset = Math.max(8, Math.round(minSide * 0.016));

        this._countdownText.setStroke('#000000', stroke);
        // Neobrutalist look: hard offset shadow, no blur, separated from the black stroke.
        this._countdownText.setShadow(shadowOffset, shadowOffset, '#262626', 0, false, true);
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
        if (this._waitingElementRotateTween) {
            this._waitingElementRotateTween.stop();
            this._waitingElementRotateTween = null;
        }
        if (this._waitingElementTimer) {
            this._waitingElementTimer.remove();
            this._waitingElementTimer = null;
        }

        const onCountdownComplete = this.registry.get('onCountdownComplete');
        if (typeof onCountdownComplete === 'function') {
            onCountdownComplete();
        }

        this.scene.start('MainScene');
        console.log('[SEQ][COUNTDOWN] MainScene started.');
    }

    shutdown() {
        if (this._countdownTween) {
            this._countdownTween.stop();
            this._countdownTween = null;
        }
        if (this._waitingElementRotateTween) {
            this._waitingElementRotateTween.stop();
            this._waitingElementRotateTween = null;
        }
        if (this._waitingElementTimer) {
            this._waitingElementTimer.remove();
            this._waitingElementTimer = null;
        }

        PhaserEventBus.off('net:questionChanged', this._onQuestionChanged);
    }
}
