import { Rosco } from '../ui/rosco.js';
import { Question } from '../ui/question.js';
import { Counter } from '../ui/counter.js';
import { InteractiveButton } from '../ui/interactiveButton.js';
import { Scoreboard } from '../ui/scoreboard.js';
import { Standings } from '../ui/standings.js';
import { PhaserEventBus } from '../phaserEventBus.js';

export class MainScene extends Phaser.Scene {
    constructor() {
        super('MainScene');
        this.bg        = null;
        this.rosco     = null;
        this.question  = null;
        this.counter   = null;
        this.scoreboard  = null;
        this.standings   = null;
        this._resizeTimer = null;
    }

    preload() {
        this.load.spritesheet('buttons', 'assets/buttons_sprite.png',
            { frameWidth: 250, frameHeight: 250 }
        );
        this.load.image('background', 'assets/background_squares.png');
    }

    create() {
        this._buildLayout(this.scale.width, this.scale.height);
        this.scale.on('resize', this._onResize, this);
    }

    _onResize(gameSize) {
        // Debounce: wait 80 ms after the last resize event before rebuilding
        if (this._resizeTimer) clearTimeout(this._resizeTimer);
        this._resizeTimer = setTimeout(() => {
            this._resizeTimer = null;
            this._destroyLayout();
            this._buildLayout(gameSize.width, gameSize.height);
        }, 80);
    }

    _buildLayout(w, h) {
        this.bg = this.add.image(w / 2, h / 2, 'background');
        this.bg.setScale(Math.max(w / this.bg.width, h / this.bg.height));

        const roscoRadius  = Math.min(210, Math.max(190, Math.round(h * 0.22)));
        const layoutCenter = { x: w / 2, y: h * 0.40 };
        const roscoVerticalOffset = roscoRadius * 0.25;

        const roscoConfig = {
            letters: 'ABCDEFGHIJLMNÑOPQRSTUVXYZ',
            centerX: layoutCenter.x,
            centerY: layoutCenter.y - roscoVerticalOffset,
            roscoRadius,
            buttonRadius: 20,
            backgroundColor: '#F0F0F0'
        };
        this.rosco = new Rosco(this, roscoConfig);

        this.question = new Question(
            this,
            { text: 'Respuesta A', index: 1 },
            { text: 'Respuesta B', index: 2 },
            { text: 'Respuesta C', index: 3 },
            { text: 'Respuesta D', index: 4 },
            'Escribe aquí el enunciado de la pregunta?',
            {
                centerX: layoutCenter.x,
                centerY: layoutCenter.y,
                roscoRadius: roscoConfig.roscoRadius,
                roscoButtonRadius: roscoConfig.buttonRadius,
                questionBottomOffset: 45
            }
        );

        const counterHeight = 110;
        const counterTopY   = this.question.questionBox.y + (this.question.questionBox.height / 2) + 24;

        const leftAnswerEdgeX  = this.question.positionMap[1].x - this.question.answerRadius;
        const rightAnswerEdgeX = this.question.positionMap[2].x + this.question.answerRadius;

        this.scoreboard = new Scoreboard(this, {
            leftEdgeX: leftAnswerEdgeX,
            topY: 20,
            width: 310,
            height: 130
        });

        this.standings = new Standings(this, {
            rightEdgeX: rightAnswerEdgeX,
            topY: 20,
            width: 310,
            height: 130
        });

        this.counter = new Counter(this, {
            centerX: layoutCenter.x,
            topY: Math.min(counterTopY, h - counterHeight - 12),
            width: 240,
            height: counterHeight,
            timeValue: '180',
            correctValue: 0,
            wrongValue: 0
        });
    }

    _destroyLayout() {
        if (this.counter)    { this.counter.destroy();    this.counter    = null; }
        if (this.rosco)      { this.rosco.destroy();      this.rosco      = null; }
        if (this.question)   { this.question.destroy();   this.question   = null; }
        if (this.scoreboard) { this.scoreboard.destroy(); this.scoreboard = null; }
        if (this.standings)  { this.standings.destroy();  this.standings  = null; }
        if (this.bg)         { this.bg.destroy();         this.bg         = null; }
    }

    update() {
    }

    shutdown() {
        this.scale.off('resize', this._onResize, this);
        if (this._resizeTimer) { clearTimeout(this._resizeTimer); this._resizeTimer = null; }
        this._destroyLayout();
    }

}
