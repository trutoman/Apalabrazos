export class ExtraTimePopup {
    constructor(scene, options = {}) {
        this.scene = scene;
        this.centerX = options.centerX ?? (this.scene.scale.width / 2);
        this.centerY = options.centerY ?? (this.scene.scale.height / 2);
        this.isVisible = false;
        this.overlay = null;
        this.container = null;
        this.valueText = null;
        this.helperText = null;
        this.holdTimer = null;
        this.hideTimer = null;
        this.transferTween = null;
    }

    show(options = {}) {
        const extraTimePointsRaw = Number(options?.extraTimePoints);
        const extraTimePoints = Number.isFinite(extraTimePointsRaw) && extraTimePointsRaw > 0
            ? Math.round(extraTimePointsRaw)
            : 0;
        const baseScoreRaw = Number(options?.baseScore);
        const baseScore = Number.isFinite(baseScoreRaw) && baseScoreRaw >= 0
            ? Math.round(baseScoreRaw)
            : 0;

        if (extraTimePoints <= 0) {
            if (typeof options?.onProgress === 'function') {
                options.onProgress(0);
            }
            if (typeof options?.onComplete === 'function') {
                options.onComplete(baseScore);
            }
            return;
        }

        this._clear();
        this.isVisible = true;

        const onProgress = typeof options?.onProgress === 'function' ? options.onProgress : null;
        const onComplete = typeof options?.onComplete === 'function' ? options.onComplete : null;

        this.overlay = this.scene.add.rectangle(
            this.centerX,
            this.centerY,
            this.scene.scale.width,
            this.scene.scale.height,
            0x000000,
            0.45
        );
        this.overlay.setDepth(1002);

        this.container = this.scene.add.container(this.centerX, this.centerY);
        this.container.setDepth(1003);

        const popupWidth = 620;
        const popupHeight = 320;
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

        const popup = this.scene.add.graphics();
        popup.fillStyle(0xfadf09, 1);
        popup.fillPoints(pts, true, true);
        popup.lineStyle(3, 0x000000, 1);
        popup.strokePoints(pts, true, true);
        this.container.add(popup);

        const title = this.scene.add.text(0, -92, 'EXTRA TIME POINTS', {
            fontSize: '46px',
            fontFamily: 'Archivo Black',
            color: '#000000',
            align: 'center'
        }).setOrigin(0.5);
        this.container.add(title);

        this.valueText = this.scene.add.text(0, -10, String(extraTimePoints), {
            fontSize: '86px',
            fontFamily: 'Archivo Black',
            color: '#000000',
            align: 'center'
        }).setOrigin(0.5);
        this.container.add(this.valueText);

        this.helperText = this.scene.add.text(0, 88, 'Se suma al marcador...', {
            fontSize: '28px',
            fontFamily: 'Archivo Black',
            color: '#000000',
            align: 'center'
        }).setOrigin(0.5);
        this.helperText.setAlpha(0.8);
        this.container.add(this.helperText);

        this.container.setAlpha(0);
        this.container.setScale(0.92);
        this.scene.tweens.add({
            targets: this.container,
            alpha: 1,
            scale: 1,
            duration: 320,
            ease: 'Back.Out'
        });

        const holdMs = 1500;
        const transferMs = 2000;

        this.holdTimer = this.scene.time.delayedCall(holdMs, () => {
            const counter = { value: 0 };
            this.transferTween = this.scene.tweens.add({
                targets: counter,
                value: extraTimePoints,
                duration: transferMs,
                ease: 'Sine.InOut',
                onUpdate: () => {
                    const transferred = Math.round(counter.value);
                    const pending = Math.max(0, extraTimePoints - transferred);
                    this.valueText.setText(String(pending));
                    if (onProgress) {
                        onProgress(transferred);
                    }
                },
                onComplete: () => {
                    if (onProgress) {
                        onProgress(extraTimePoints);
                    }
                    if (onComplete) {
                        onComplete(baseScore + extraTimePoints);
                    }

                    this.hideTimer = this.scene.time.delayedCall(360, () => {
                        this.hide();
                    });
                }
            });
        });
    }

    hide() {
        if (!this.isVisible) {
            return;
        }

        this.isVisible = false;

        if (this.container) {
            this.scene.tweens.add({
                targets: this.container,
                alpha: 0,
                y: this.container.y - 20,
                duration: 280,
                ease: 'Power2.In',
                onComplete: () => {
                    if (this.container) {
                        this.container.destroy();
                        this.container = null;
                    }
                }
            });
        }

        if (this.overlay) {
            this.scene.tweens.add({
                targets: this.overlay,
                alpha: 0,
                duration: 280,
                ease: 'Power2.In',
                onComplete: () => {
                    if (this.overlay) {
                        this.overlay.destroy();
                        this.overlay = null;
                    }
                }
            });
        }
    }

    _clear() {
        if (this.holdTimer) {
            this.holdTimer.remove();
            this.holdTimer = null;
        }
        if (this.hideTimer) {
            this.hideTimer.remove();
            this.hideTimer = null;
        }
        if (this.transferTween) {
            this.transferTween.stop();
            this.transferTween = null;
        }
        if (this.container) {
            this.container.destroy();
            this.container = null;
        }
        if (this.overlay) {
            this.overlay.destroy();
            this.overlay = null;
        }
        this.valueText = null;
        this.helperText = null;
        this.isVisible = false;
    }

    destroy() {
        this._clear();
    }
}
