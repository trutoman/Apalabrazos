import { Rosco } from '../ui/rosco.js';
import { Question } from '../ui/question.js';
import { Counter } from '../ui/counter.js';
import { InteractiveButton } from '../ui/interactiveButton.js';
import { Scoreboard } from '../ui/scoreboard.js';
import { Standings } from '../ui/standings.js';

export class MainScene extends Phaser.Scene {
    constructor() {
        super('MainScene');
        this.mainButton = null;
        this.rosco = null;
        this.question = null;
        this.counter = null;
    }

    preload() {
        this.load.spritesheet('buttons', 'assets/buttons_sprite.png',
            { frameWidth: 250, frameHeight: 250 }
        );
        this.load.image('background', 'assets/background_squares.png');
    }

    create() {
        const bg = this.add.image(this.scale.width / 2, this.scale.height / 2, 'background');
        const bgScale = Math.max(this.scale.width / bg.width, this.scale.height / bg.height);
        bg.setScale(bgScale);

        const roscoRadius = Math.min(210, Math.max(190, Math.round(this.scale.height * 0.22)));
        const layoutCenter = {
            x: this.scale.width / 2,
            y: this.scale.height * 0.40
        };

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
        const counterTopY = this.question.questionBox.y + (this.question.questionBox.height / 2) + 24;

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
            topY: Math.min(counterTopY, this.scale.height - counterHeight - 12),
            width: 240,
            height: counterHeight,
            timeValue: '180',
            correctValue: 0,
            wrongValue: 0
        });


    };

    update() {
    }

}
