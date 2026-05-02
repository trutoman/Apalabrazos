import { InteractiveButton } from './interactiveButton.js';

export class Counter {
    constructor(scene, options = {}) {
        this.scene = scene;

        this.centerX = options.centerX ?? (this.scene.scale.width / 2);
        this.topY = options.topY ?? 24;
        this.width = options.width ?? 240;
        this.height = options.height ?? 150;

        this.timeValue = options.timeValue ?? '100';
        this.correctValue = options.correctValue ?? 0;
        this.wrongValue = options.wrongValue ?? 0;

        this._draw();
    }

    _draw() {
        const gap = 8;
        const topHeight = Math.round((this.height - gap) * 0.6);
        const bottomHeight = (this.height - gap) - topHeight;
        const bottomBlockWidth = Math.floor((this.width - gap) / 2);
        const shadowDepth = 5;

        const timeCenterY   = this.topY + topHeight / 2;
        const bottomCenterY = this.topY + topHeight + gap + bottomHeight / 2;
        const leftCenterX   = this.centerX - bottomBlockWidth / 2 - gap / 2;
        const rightCenterX  = this.centerX + bottomBlockWidth / 2 + gap / 2;

        // ── Block 1: time ───────────────────────────────────────────────
        this.timeButton = new InteractiveButton(
            this.scene, 'counter_time',
            this.centerX, timeCenterY,
            this.width, topHeight,
            this.timeValue, null,
            {
                type: 'irregular',
                circleColor: 0xF0F0F0,
                strokeColor: 0x000000,
                strokeWidth: 2,
                textColor: '#000000',
                fontSize: '34px',
                shadowDepth,
                useHandCursor: false
            }
        );

        // ── Block 2: correct ────────────────────────────────────────────
        this.correctButton = new InteractiveButton(
            this.scene, 'counter_correct',
            leftCenterX, bottomCenterY,
            bottomBlockWidth, bottomHeight,
            `${this.correctValue}`, null,
            {
                type: 'irregular',
                circleColor: 0xa2ff00,
                strokeColor: 0x000000,
                strokeWidth: 2,
                shadowDepth,
                useHandCursor: false
            }
        );
        this._correctCount = this.correctButton.text;


        // ── Block 3: wrong ──────────────────────────────────────────────
        this.wrongButton = new InteractiveButton(
            this.scene, 'counter_wrong',
            rightCenterX, bottomCenterY,
            bottomBlockWidth, bottomHeight,
            `${this.wrongValue}`, null,
            {
                type: 'irregular',
                circleColor: 0xff4911,
                strokeColor: 0x000000,
                strokeWidth: 2,
                shadowDepth,
                useHandCursor: false
            }
        );
        this._wrongCount = this.wrongButton.text;
    }

    setTime(value) {
        this.timeValue = value;
        this.timeButton.text.setText(value);
    }

    setCorrect(value) {
        this.correctValue = value;
        this._correctCount.setText(`${value}`);
    }

    setWrong(value) {
        this.wrongValue = value;
        this._wrongCount.setText(`${value}`);
    }

    incrementCorrect() {
        this.setCorrect(this.correctValue + 1);
    }

    incrementWrong() {
        this.setWrong(this.wrongValue + 1);
    }
}
