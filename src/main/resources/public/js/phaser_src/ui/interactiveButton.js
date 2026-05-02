export class InteractiveButton extends Phaser.GameObjects.Container {
    constructor(scene, buttonName, posX, posY, displayWidth, displayHeight, label = '', onPointerDown = null, options = {}) {
        super(scene, posX, posY);

        const radius = Math.min(displayWidth, displayHeight) / 2;
        const {
            type = 'circle',
            circleColor = 0xfadf09,
            strokeColor = 0x000000,
            strokeWidth = 1,
            textColor = '#000000',
            fontSize = '24px',
            fontFamily = 'Archivo Black',
            shadowColor = 0x000000,
            shadowAlpha = 1,
            useHandCursor = true,
            shadowDepth = 4
        } = options;

        this.buttonName = buttonName;
        this.label = label;
        this.onPointerDown = onPointerDown;
        this.baseOffset = 0;
        this.hoverOffset = -shadowDepth;
        this.shadowOffset = shadowDepth;
        this._cx = 0;
        this._cy = 0;

        if (type === 'irregular') {
            const w = displayWidth / 2;
            const h = displayHeight / 2;
            const s = Math.round(Math.min(w, h) * 0.12);
            const r = () => (Math.random() * 2 - 1) * s;   // random in [-s, +s]

            // Irregular quadrilateral: each corner skewed randomly
            const pts = [
                { x: -w + Math.abs(r()), y: -h - Math.abs(r()) },   // top-left
                { x:  w + Math.abs(r()), y: -h + r() },              // top-right
                { x:  w - Math.abs(r()), y:  h + Math.abs(r()) },   // bottom-right
                { x: -w - Math.abs(r()), y:  h + r() },              // bottom-left
            ];

            this.shadow = scene.add.graphics();
            this.shadow.fillStyle(shadowColor, shadowAlpha);
            this.shadow.fillPoints(pts, true, true);
            this.shadow.setPosition(this.shadowOffset, this.shadowOffset);

            this.circle = scene.add.graphics();
            this.circle.fillStyle(circleColor, 1);
            this.circle.fillPoints(pts, true, true);
            this.circle.lineStyle(strokeWidth, strokeColor, 1);
            this.circle.strokePoints(pts, true, true);

            const polygon = new Phaser.Geom.Polygon(pts.flatMap(p => [p.x, p.y]));
            this.circle.setInteractive(polygon, Phaser.Geom.Polygon.Contains);
            if (useHandCursor) this.circle.input.cursor = 'pointer';

        } else if (type === 'square') {
            this.shadow = scene.add.rectangle(this.shadowOffset, this.shadowOffset, displayWidth, displayHeight, shadowColor, shadowAlpha);
            this.circle = scene.add.rectangle(0, 0, displayWidth, displayHeight, circleColor);
            this.circle.setStrokeStyle(strokeWidth, strokeColor);
            this.circle.setInteractive({ useHandCursor });

        } else {
            // default: circle
            this.shadow = scene.add.circle(this.shadowOffset, this.shadowOffset, radius, shadowColor, shadowAlpha);
            this.circle = scene.add.circle(0, 0, radius, circleColor);
            this.circle.setStrokeStyle(strokeWidth, strokeColor);
            this.circle.setInteractive({ useHandCursor });
        }

        this.text = scene.add.text(0, 0, label, {
            fontSize,
            fontFamily,
            color: textColor
        }).setOrigin(0.5);

        this.add([this.shadow, this.circle, this.text]);
        this.setSize(displayWidth, displayHeight);
        this.setName(buttonName);

        this.circle.on('pointerover', this.handlePointerOver, this);
        this.circle.on('pointerout', this.handlePointerOut, this);
        this.circle.on('pointerdown', this.handlePointerDown, this);

        scene.add.existing(this);
    }

    _moveContent(x, y) {
        const dx = x - this._cx;
        const dy = y - this._cy;
        this._cx = x;
        this._cy = y;
        this.each(child => {
            if (child !== this.shadow) {
                child.x += dx;
                child.y += dy;
            }
        });
    }

    handlePointerOver() {
        this._moveContent(this.hoverOffset, this.hoverOffset);
    }

    handlePointerOut() {
        this._moveContent(this.baseOffset, this.baseOffset);
    }

    handlePointerDown() {
        this._moveContent(this.shadowOffset, this.shadowOffset);
        console.log(`pointerdown on button: ${this.buttonName}`);

        if (typeof this.onPointerDown === 'function') {
            this.onPointerDown(this.label, {
                circle: this.circle,
                text: this.text,
                shadow: this.shadow,
                buttonName: this.buttonName,
                button: this
            });
        }
    }
}
