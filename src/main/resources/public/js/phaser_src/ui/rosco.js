import { InteractiveButton } from './interactiveButton.js';

export class Rosco {
    constructor(scene, options = {}) {
        this.scene = scene;
        this.letters = (options.letters || "ABCDEFGHIJLMNÑOPQRSTUVXYZ").split("");
        this.centerX = options.centerX ?? (this.scene.scale.width / 2);
        this.centerY = options.centerY ?? (this.scene.scale.height / 2);
        this.roscoRadius = options.roscoRadius || 220;
        this.buttonRadius = options.buttonRadius || 22;
        this.backgroundColor = options.backgroundColor || '#F0F0F0';

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

            const button = new InteractiveButton(
                this.scene,
                buttonName,
                buttonX,
                buttonY,
                this.buttonRadius * 2,
                this.buttonRadius * 2,
                char
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

        this.centerButton = new InteractiveButton(
            this.scene,
            'pass_button',
            this.centerX,
            this.centerY,
            centerRadius * 2,
            centerRadius * 2,
            'PASAR',
            null,
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