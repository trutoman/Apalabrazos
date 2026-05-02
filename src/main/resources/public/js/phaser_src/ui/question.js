import { InteractiveButton } from './interactiveButton.js';

export class Question {
    // answer1..4 = { text: string, index: 1|2|3|4 }
    // question   = string
    constructor(scene, answer1, answer2, answer3, answer4, question, options = {}) {
        this.scene = scene;
        this.answers = [answer1, answer2, answer3, answer4];
        this.questionText = question;

        this.centerX = options.centerX ?? (this.scene.scale.width / 2);
        this.centerY = options.centerY ?? (this.scene.scale.height / 2);
        this.roscoRadius = options.roscoRadius || 220;
        this.roscoButtonRadius = options.roscoButtonRadius || 22;
        this.questionBottomOffset = options.questionBottomOffset || 140;
        this.answerRadius = options.answerRadius || 50;
        this.answerTextMaxWidth = options.answerTextMaxWidth || 200;
        this.labelMap = { 1: '1', 2: '2', 3: '3', 4: '4' };
        this.answerButtons = new Map();

        this.positionMap = this._buildSidePositionMap(options);

        this._drawAll();
    }

    _buildSidePositionMap(options = {}) {
        const horizontalGapFromRosco = options.horizontalGapFromRosco || 130;
        const verticalOffset = Math.round(this.roscoRadius * 0.40);
        const sidePadding    = 20;
        const roscoSafeGap   = 24;
        const halfButton     = this.answerRadius;           // half-side of the square button
        const textGap        = halfButton + 14;             // from button center to text edge

        const answersVerticalOffset = 30;

        const roscoEdgeLeft  = this.centerX - (this.roscoRadius + this.roscoButtonRadius);
        const roscoEdgeRight = this.centerX + (this.roscoRadius + this.roscoButtonRadius);
        const preferred      = this.roscoRadius + this.roscoButtonRadius + halfButton + horizontalGapFromRosco;

        // Left button: text drawn to the right — keep button left enough so text clears rosco & viewport
        const maxLeftX = Math.min(
            roscoEdgeLeft  - roscoSafeGap - this.answerTextMaxWidth - textGap,
            this.scene.scale.width - sidePadding - this.answerTextMaxWidth - textGap
        );
        const leftX = Math.max(sidePadding + halfButton, Math.min(this.centerX - preferred, maxLeftX));

        // Right button: text drawn to the left — keep button right enough so text clears rosco & viewport
        const minRightX = Math.max(
            roscoEdgeRight + roscoSafeGap + this.answerTextMaxWidth + textGap,
            sidePadding + this.answerTextMaxWidth + textGap
        );
        const rightX = Math.min(
            this.scene.scale.width - sidePadding - halfButton,
            Math.max(this.centerX + preferred, minRightX)
        );

        return {
            1: { x: leftX,  y: this.centerY - verticalOffset + answersVerticalOffset, textSide: 'right' },
            2: { x: rightX, y: this.centerY - verticalOffset + answersVerticalOffset, textSide: 'left'  },
            3: { x: leftX,  y: this.centerY + verticalOffset + answersVerticalOffset, textSide: 'right' },
            4: { x: rightX, y: this.centerY + verticalOffset + answersVerticalOffset, textSide: 'left'  },
        };
    }

    _drawAll() {
        this.answers.forEach(answer => this._drawAnswer(answer));
        this._drawQuestion();
    }

    _drawAnswer(answer) {
        const pos   = this.positionMap[answer.index];
        const label = this.labelMap[answer.index];
        const r     = this.answerRadius;

        const button = new InteractiveButton(
            this.scene,
            `answer_${answer.index}_button`,
            pos.x,
            pos.y,
            r * 2,
            r * 2,
            label,
            null,
            {
                type: 'irregular',
                circleColor: 0xff00f4,
                strokeColor: 0x5b2c6f,
                strokeWidth: 3,
                textColor: '#ffffff',
                fontSize: '32px',
                shadowAlpha: 1,
                shadowDepth: 8
            }
        );
        this.answerButtons.set(answer.index, button);

        // Answer text beside the circle
        const gap      = r + 14;
        const textX    = pos.textSide === 'right' ? pos.x + gap : pos.x - gap;
        const originX  = pos.textSide === 'right' ? 0 : 1;

        this.scene.add.text(textX, pos.y, answer.text, {
            fontSize: '20px',
            fontFamily: 'Archivo Black',
            color: '#222222',
            wordWrap: { width: this.answerTextMaxWidth },
        }).setOrigin(originX, 0.5);
    }

    _drawQuestion() {
        const cx = this.centerX;
        const yFromRosco = this.centerY + this.roscoRadius + this.questionBottomOffset;
        const barY = Math.min(this.scene.scale.height - 52, yFromRosco);
        const barWidth = Math.min(900, this.scene.scale.width - 40);
        const barHeight = 60;

        this.questionBox = new InteractiveButton(
            this.scene,
            'question_box',
            cx,
            barY,
            barWidth,
            barHeight,
            this.questionText,
            null,
            {
                type: 'irregular',
                circleColor: 0xfadf09,
                strokeColor: 0x000000,
                strokeWidth: 2,
                textColor: '#000000',
                fontSize: '20px',
                shadowColor: 0x000000,
                shadowAlpha: 1,
                shadowDepth: 9,
                useHandCursor: false,
            }
        );

        this.questionBox.text.setWordWrapWidth(barWidth - 40, true);
        this.questionBox.text.setAlign('center');
        this.questionBox.text.setOrigin(0.5);
    }
}
