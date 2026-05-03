import { InteractiveButton } from './interactiveButton.js';

// Color de cada estado posible de una letra en el rosco
const LETTER_COLORS = {
    pending:   0xFADF09,  // amarillo — todavía sin responder
    active:    0x00b4ff,  // azul     — es la letra en juego ahora mismo
    correct:   0x6dd44a,  // verde    — respondida correctamente
    incorrect: 0xe05050,  // rojo     — respondida de forma incorrecta
    passed:    0xffa94d   // naranja  — el jugador decidió pasar esta pregunta
};

export class Rosco {
    constructor(scene, options = {}) {
        this.scene = scene;
        this.letters = (options.letters || "ABCDEFGHIJLMNÑOPQRSTUVXYZ").split("");
        this.centerX = options.centerX ?? (this.scene.scale.width / 2);
        this.centerY = options.centerY ?? (this.scene.scale.height / 2);
        this.roscoRadius = options.roscoRadius || 220;
        this.buttonRadius = options.buttonRadius || 22;
        this.backgroundColor = options.backgroundColor || '#F0F0F0';

        // Callback que se ejecuta cuando el jugador pulsa el botón PASAR
        this._onPassSelected = options.onPassSelected || null;

        this.letterButtons = new Map();
        this.buttonsByName = new Map();
        this.buttonsGroup = this.scene.add.group();
        this.centerButton = null;

        this.create();
    }

    create() {
        this.createCenterButton();

        this.letters.forEach((char, i) => {
            const angle = -Math.PI / 2 + (i / this.letters.length) * Math.PI * 2;
            const buttonX = this.centerX + Math.cos(angle) * this.roscoRadius;
            const buttonY = this.centerY + Math.sin(angle) * this.roscoRadius;
            const buttonName = `${char}_button`;

            // Las letras del rosco son solo indicadores visuales, no se pueden pulsar
            const button = new InteractiveButton(
                this.scene,
                buttonName,
                buttonX,
                buttonY,
                this.buttonRadius * 2,
                this.buttonRadius * 2,
                char,
                null,
                { interactive: false }
            );

            this.buttonsGroup.add(button);
            this.letterButtons.set(char, button);
            this.buttonsByName.set(buttonName, button);
        });
    }

    createCenterButton() {
        const marginToLetters = this.buttonRadius * 2;
        const maxCenterRadius = this.roscoRadius - this.buttonRadius - marginToLetters;
        const centerRadius = Math.max(60, maxCenterRadius);

        // Al pulsar PASAR se notifica a la escena para enviar selectedOption -1 al servidor
        this.centerButton = new InteractiveButton(
            this.scene,
            'pass_button',
            this.centerX,
            this.centerY,
            centerRadius * 2,
            centerRadius * 2,
            'PASAR',
            () => { if (this._onPassSelected) this._onPassSelected(); },
            {
                circleColor: 0x00f0ff,
                strokeColor: 0x000000,
                strokeWidth: 2,
                textColor: '#000000',
                fontSize: '38px',
                shadowDepth: 8
            }
        );
    }

    // Cambia el color de una letra del rosco según su estado en la partida
    setLetterState(letter, state) {
        const btn = this.letterButtons.get(letter.toUpperCase());
        if (!btn) return;
        btn.circle.setFillStyle(LETTER_COLORS[state] ?? LETTER_COLORS.pending);
    }

    destroy() {
        this.letterButtons.forEach(btn => btn.destroy());
        this.letterButtons.clear();
        this.buttonsByName.clear();
        if (this.centerButton) {
            this.centerButton.destroy();
            this.centerButton = null;
        }
        if (this.buttonsGroup) {
            this.buttonsGroup.destroy(true);
            this.buttonsGroup = null;
        }
    }

    getButtonByLetter(letter) {
        return this.letterButtons.get(letter) || null;
    }

    getButtonByName(buttonName) {
        return this.buttonsByName.get(buttonName) || null;
    }

    getButtonsByLetters(letters) {
        return letters
            .map((letter) => this.getButtonByLetter(letter))
            .filter((button) => button !== null);
    }

    getAllButtons() {
        return this.buttonsGroup ? this.buttonsGroup.getChildren() : [];
    }
}
