import { Rosco } from '../ui/rosco.js';
import { Question } from '../ui/question.js';
import { Counter } from '../ui/counter.js';
import { InteractiveButton } from '../ui/interactiveButton.js';
import { Scoreboard } from '../ui/scoreboard.js';
import { Standings } from '../ui/standings.js';
import { GameOverPopup } from '../ui/gameOverPopup.js';
import { ExtraTimePopup } from '../ui/extraTimePopup.js';
import { PhaserEventBus, getSticky } from '../phaserEventBus.js';
import { SocketClient } from '../../network/socket-client.js';
import { MatchAudio } from '../../audio/match-audio.js';

export class MainScene extends Phaser.Scene {
    constructor() {
        super('MainScene');
        this.bg        = null;
        this.rosco     = null;
        this.question  = null;
        this.counter   = null;
        this.scoreboard  = null;
        this.standings   = null;
        this.gameOverPopup = null;
        this.extraTimePopup = null;
        this.muteThemeBtn = null;
        this._resizeTimer = null;
        this._onNetQuestionChanged = this._handleQuestionChanged.bind(this);
        this._onNetAnswerValidated = this._handleAnswerValidated.bind(this);
        this._onNetStandings = this._handleStandings.bind(this);
        this._onNetGameFinished = this._handleGameFinished.bind(this);
        this._onNetExtraTimeScore = this._handleExtraTimeScore.bind(this);
        this.currentQuestionIndex = null;
        this.lastSubmittedQuestionIndex = null;

        // Estado guardado para preservar durante resize
        this._savedState = {
            letterStates: new Map(), // Map<letter, 'CORRECT'|'WRONG'|'PASSED'>
            correct: 0,
            wrong: 0,
            score: 0,
            questionData: null,
            themeMuted: false
        };
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
        PhaserEventBus.on('net:questionChanged', this._onNetQuestionChanged);
        PhaserEventBus.on('net:answerValidated', this._onNetAnswerValidated);
        PhaserEventBus.on('net:standings', this._onNetStandings);
        PhaserEventBus.on('net:gameFinished', this._onNetGameFinished);
        PhaserEventBus.on('net:extraTimeScore', this._onNetExtraTimeScore);

        const initialQuestionPayload = getSticky('net:questionChanged');
        if (initialQuestionPayload) {
            this._handleQuestionChanged(initialQuestionPayload);
        }

        const initialAnswerResult = getSticky('net:answerValidated');
        if (initialAnswerResult) {
            this._handleAnswerValidated(initialAnswerResult);
        }

        const initialStandings = getSticky('net:standings');
        if (initialStandings) {
            this._handleStandings(initialStandings);
        }
    }

    _handleQuestionChanged(payload = {}) {
        const questionData = payload?.nextQuestion;
        const questionIndex = Number.isInteger(payload?.questionIndex)
            ? payload.questionIndex
            : Number(payload?.questionIndex);

        if (Number.isFinite(questionIndex)) {
            this.currentQuestionIndex = questionIndex;
            this.lastSubmittedQuestionIndex = null;
        }

        this._syncCounter(payload?.totalCorrect, payload?.totalIncorrect);

        if (!questionData || !this.question) {
            console.warn('[GAME][SCENE] Ignorando QuestionChanged por falta de datos o UI no lista', {
                hasQuestionData: Boolean(questionData),
                hasQuestionUI: Boolean(this.question),
                questionIndex: this.currentQuestionIndex,
            });
            return;
        }

        const questionText = String(questionData?.questionText || '').trim();
        const responses = Array.isArray(questionData?.questionResponsesList)
            ? questionData.questionResponsesList
            : [];

        if (!questionText || responses.length < 4) {
            console.warn('[GAME][SCENE] QuestionChanged inválido para render', {
                questionIndex: this.currentQuestionIndex,
                questionText,
                responsesLength: responses.length,
                raw: questionData,
            });
            return;
        }

        console.log('[GAME][SCENE] Renderizando pregunta', {
            questionIndex: this.currentQuestionIndex,
            questionText,
            responsesLength: responses.length,
        });

        this.question.setContent(questionText, responses);
    }

    _handleAnswerValidated(answerResult = {}) {
        this._syncCounter(answerResult?.totalCorrect, answerResult?.totalIncorrect);
        this._syncTotalScore(answerResult?.totalScore);
        const status = String(answerResult?.status || '').trim().toUpperCase();
        const letter = String(answerResult?.questionLetter || '').trim().toUpperCase();

        if (this.rosco && letter) {
            if (status === 'RESPONDED_OK') {
                this.rosco.setLetterResult(letter, true);
                MatchAudio.playCorrectSfx();
            } else if (status === 'RESPONDED_FAIL') {
                this.rosco.setLetterResult(letter, false);
                MatchAudio.playWrongSfx();
            } else if (status === 'PASSED') {
                this.rosco.setLetterPassed(letter);
            }
        }

        console.log('[GAME][SCENE] Resultado de respuesta recibido', answerResult);
    }

    _handleGameFinished(gameFinishedPayload = {}) {
        console.log('[GAME][SCENE] Game finished received', gameFinishedPayload);
        MatchAudio.playGameOverCelebrationSfx();

        if (!this.gameOverPopup) {
            this.gameOverPopup = new GameOverPopup(this);
        }

        this.gameOverPopup.showGameOver({
            winnerName: gameFinishedPayload?.winnerName,
            winnerScore: gameFinishedPayload?.winnerScore,
        });
    }

    _handleStandings(standings = []) {
        if (!this.standings) {
            return;
        }
        this.standings.setEntries(Array.isArray(standings) ? standings : []);
    }

    _handleExtraTimeScore(payload = {}) {
        const extraTimeScoreRaw = Number(payload?.extraTimeScore);
        const extraTimeScore = Number.isFinite(extraTimeScoreRaw) && extraTimeScoreRaw >= 0
            ? Math.round(extraTimeScoreRaw)
            : 0;

        const backendTotalScoreRaw = Number(payload?.totalScore);
        const backendTotalScore = Number.isFinite(backendTotalScoreRaw) && backendTotalScoreRaw >= 0
            ? Math.round(backendTotalScoreRaw)
            : null;

        if (!this.extraTimePopup) {
            this.extraTimePopup = new ExtraTimePopup(this);
        }

        const currentScoreRaw = Number(this.scoreboard?.scoreValueText?.text);
        const currentScore = Number.isFinite(currentScoreRaw) && currentScoreRaw >= 0
            ? Math.round(currentScoreRaw)
            : 0;

        this.extraTimePopup.show({
            extraTimePoints: extraTimeScore,
            baseScore: currentScore,
            onProgress: (transferred) => {
                this._syncTotalScore(currentScore + transferred);
            },
            onComplete: () => {
                if (backendTotalScore !== null) {
                    this._syncTotalScore(backendTotalScore);
                } else {
                    this._syncTotalScore(currentScore + extraTimeScore);
                }
            },
        });
    }

    _syncCounter(totalCorrect, totalIncorrect) {
        if (!this.counter) {
            return;
        }

        const correct = Number(totalCorrect);
        if (Number.isFinite(correct) && correct >= 0) {
            this.counter.setCorrect(correct);
        }

        const wrong = Number(totalIncorrect);
        if (Number.isFinite(wrong) && wrong >= 0) {
            this.counter.setWrong(wrong);
        }
    }

    _syncTotalScore(totalScore) {
        if (!this.scoreboard) {
            return;
        }

        const score = Number(totalScore);
        if (Number.isFinite(score) && score >= 0) {
            this.scoreboard.setScore(score);
        }
    }

    _onResize(gameSize) {
        // Debounce: wait 80 ms after the last resize event before rebuilding
        if (this._resizeTimer) clearTimeout(this._resizeTimer);

        // Guardar estado antes de destruir
        this._saveGameState();

        this._resizeTimer = setTimeout(() => {
            this._resizeTimer = null;
            this._destroyLayout();
            this._buildLayout(gameSize.width, gameSize.height);
            // Restaurar estado después de reconstruir
            this._restoreGameState();
        }, 80);
    }

    _saveGameState() {
        // Guardar estados de las letras
        if (this.rosco && this.rosco.letterButtons) {
            this._savedState.letterStates.clear();
            this.rosco.letterButtons.forEach((button, letter) => {
                // Detectar estado por el color del botón (accediendo a _circleColor)
                if (button && button._circleColor !== undefined) {
                    const color = button._circleColor;
                    if (color === 0xa2ff00) { // CORRECT
                        this._savedState.letterStates.set(letter, 'CORRECT');
                    } else if (color === 0xff4911) { // WRONG
                        this._savedState.letterStates.set(letter, 'WRONG');
                    } else if (color === 0x00f0ff) { // PASSED
                        this._savedState.letterStates.set(letter, 'PASSED');
                    }
                }
            });
        }

        // Guardar counter
        if (this.counter) {
            this._savedState.correct = this.counter.correctValue || 0;
            this._savedState.wrong = this.counter.wrongValue || 0;
        }

        // Guardar score (accediendo a través del texto)
        if (this.scoreboard && this.scoreboard.scoreValueText) {
            const scoreText = this.scoreboard.scoreValueText.text;
            this._savedState.score = Number(scoreText) || 0;
        }

        // Guardar estado mute del tema
        // (this._savedState.themeMuted se actualiza en el callback del botón, no hay que hacer nada)
    }

    _restoreGameState() {
        // Restaurar estados de las letras
        if (this.rosco) {
            this._savedState.letterStates.forEach((state, letter) => {
                if (state === 'CORRECT') {
                    this.rosco.setLetterResult(letter, true);
                } else if (state === 'WRONG') {
                    this.rosco.setLetterResult(letter, false);
                } else if (state === 'PASSED') {
                    this.rosco.setLetterPassed(letter);
                }
            });
        }

        // Restaurar counter
        if (this.counter) {
            if (this._savedState.correct > 0) {
                this.counter.setCorrect(this._savedState.correct);
            }
            if (this._savedState.wrong > 0) {
                this.counter.setWrong(this._savedState.wrong);
            }
        }

        // Restaurar score
        if (this.scoreboard && this._savedState.score > 0) {
            this.scoreboard.setScore(this._savedState.score);
        }
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
        this.rosco = new Rosco(this, { ...roscoConfig, onPassPressed: () => this._submitPass() });

        this.question = new Question(
            this,
            { text: 'Respuesta A', index: 0 },
            { text: 'Respuesta B', index: 1 },
            { text: 'Respuesta C', index: 2 },
            { text: 'Respuesta D', index: 3 },
            'Escribe aquí el enunciado de la pregunta?',
            {
                centerX: layoutCenter.x,
                centerY: layoutCenter.y,
                roscoRadius: roscoConfig.roscoRadius,
                roscoButtonRadius: roscoConfig.buttonRadius,
                questionBottomOffset: 45,
                onAnswerSelected: (optionIndex) => this._submitAnswer(optionIndex)
            }
        );

        const counterHeight = 110;
        const counterTopY   = this.question.questionBox.y + (this.question.questionBox.height / 2) + 24;

        const leftAnswerEdgeX  = this.question.positionMap[0].x - this.question.answerRadius;
        const rightAnswerEdgeX = this.question.positionMap[1].x + this.question.answerRadius;
        const panelWidth = 310;

        // Posiciones (izquierda): su arista izquierda coincide con respuestas 1 y 3.
        const standingsRightEdgeX = leftAnswerEdgeX + panelWidth;
        // Puntos (derecha): su arista derecha coincide con respuestas 2 y 4.
        const scoreboardLeftEdgeX = rightAnswerEdgeX - panelWidth;

        this.scoreboard = new Scoreboard(this, {
            leftEdgeX: scoreboardLeftEdgeX,
            topY: 20,
            width: panelWidth,
            height: 130
        });

        this.standings = new Standings(this, {
            rightEdgeX: standingsRightEdgeX,
            topY: 20,
            width: panelWidth,
            height: 130
        });

        const actualCounterTopY = Math.min(counterTopY, h - counterHeight - 12);
        this.counter = new Counter(this, {
            centerX: layoutCenter.x,
            topY: actualCounterTopY,
            width: 240,
            height: counterHeight,
            timeValue: '180',
            correctValue: 0,
            wrongValue: 0
        });

        // ── Mute theme toggle (neobrutalism checkbox) ──────────────────
        const _cGap = 8;
        const _cTopH = Math.round((counterHeight - _cGap) * 0.6);
        const _cBotH = (counterHeight - _cGap) - _cTopH;
        const muteBtnSize = 40;
        const muteIconLeftOffset = (muteBtnSize / 2) + 12;
        const muteIconBoxWidth = 28;
        const muteControlRightOffset = muteIconLeftOffset + muteIconBoxWidth;
        const muteBtnX = rightAnswerEdgeX - muteControlRightOffset;
        const muteBtnY = actualCounterTopY + _cTopH + _cGap + _cBotH / 2;
        const isThemeMuted = this._savedState.themeMuted;
        this.muteThemeBtn = new InteractiveButton(
            this, 'mute-theme-btn',
            muteBtnX, muteBtnY,
            muteBtnSize, muteBtnSize,
            '',
            () => {
                const muted = !this._savedState.themeMuted;
                this._savedState.themeMuted = muted;
                MatchAudio.setThemeMuted(muted);
                this.muteThemeBtn._muteXOverlay.setVisible(muted);
            },
            {
                type: 'square',
                circleColor: 0xffffff,
                strokeColor: 0x000000,
                strokeWidth: 3,
                textColor: '#000000',
                fontSize: '1px',
                shadowDepth: 4,
            }
        );
        // Check clásico: caja vacía + cruz roja al activar, y nota fija a la derecha
        const _muteXText = this.add.text(0, 0, '\u2715', {
            fontSize: '28px',
            fontFamily: 'Archivo Black',
            color: '#ff0000',
            stroke: '#b10000',
            strokeThickness: 4,
        }).setOrigin(0.5).setVisible(isThemeMuted);
        this.muteThemeBtn.add(_muteXText);
        this.muteThemeBtn._muteXOverlay = _muteXText;

        const _musicIcon = this.add.text(muteIconLeftOffset, 0, '\u266B', {
            fontSize: '24px',
            fontFamily: 'Archivo Black',
            color: '#000000',
            fixedWidth: muteIconBoxWidth,
            align: 'center',
        }).setOrigin(0, 0.5);
        this.muteThemeBtn.add(_musicIcon);
    }

    _submitPass() {
        MatchAudio.playPassSfx();

        const questionIndex = Number(this.currentQuestionIndex);
        if (!Number.isFinite(questionIndex) || questionIndex < 0) {
            console.warn('[GAME] Ignorando pasar: no hay pregunta activa');
            return;
        }

        SocketClient.send('AnswerSubmitted', {
            questionIndex,
            selectedOption: -1,
            submittedAt: Math.floor(Date.now() / 1000),
        });
    }

    _submitAnswer(optionIndex) {
        const selectedOption = Number(optionIndex);
        if (!Number.isFinite(selectedOption)) {
            return;
        }

        if (selectedOption < 0 || selectedOption > 3) {
            console.warn('[GAME] Ignorando respuesta: opción fuera de rango', selectedOption);
            return;
        }

        const questionIndex = Number(this.currentQuestionIndex);
        if (!Number.isFinite(questionIndex) || questionIndex < 0) {
            console.warn('[GAME] Ignorando respuesta: no hay pregunta activa');
            return;
        }

        if (this.lastSubmittedQuestionIndex === questionIndex) {
            return;
        }

        this.lastSubmittedQuestionIndex = questionIndex;
        SocketClient.send('AnswerSubmitted', {
            questionIndex,
            selectedOption,
            submittedAt: Math.floor(Date.now() / 1000),
        });
    }

    _destroyLayout() {
        if (this.counter)    { this.counter.destroy();    this.counter    = null; }
        if (this.rosco)      { this.rosco.destroy();      this.rosco      = null; }
        if (this.question)   { this.question.destroy();   this.question   = null; }
        if (this.scoreboard) { this.scoreboard.destroy(); this.scoreboard = null; }
        if (this.standings)  { this.standings.destroy();  this.standings  = null; }
        if (this.gameOverPopup) { this.gameOverPopup.destroy(); this.gameOverPopup = null; }
        if (this.extraTimePopup) { this.extraTimePopup.destroy(); this.extraTimePopup = null; }
        if (this.muteThemeBtn) { this.muteThemeBtn.destroy(); this.muteThemeBtn = null; }
        if (this.bg)         { this.bg.destroy();         this.bg         = null; }
    }

    update() {
    }

    shutdown() {
        PhaserEventBus.off('net:questionChanged', this._onNetQuestionChanged);
        PhaserEventBus.off('net:answerValidated', this._onNetAnswerValidated);
        PhaserEventBus.off('net:standings', this._onNetStandings);
        PhaserEventBus.off('net:gameFinished', this._onNetGameFinished);
        PhaserEventBus.off('net:extraTimeScore', this._onNetExtraTimeScore);
        this.scale.off('resize', this._onResize, this);
        if (this._resizeTimer) { clearTimeout(this._resizeTimer); this._resizeTimer = null; }
        this._destroyLayout();
    }

}
