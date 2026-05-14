const THEME_URL = '/assets/audio/background_theme.mp3';
const DEFAULT_VOLUME = 0.42;
const DEFAULT_SFX_VOLUME = 0.28;

let themeAudio = null;
let unlockListenersBound = false;
let audioContext = null;
let sfxMasterGain = null;

function ensureAudioContext() {
    if (audioContext) {
        return audioContext;
    }

    const Ctx = window.AudioContext || window.webkitAudioContext;
    if (!Ctx) {
        return null;
    }

    audioContext = new Ctx();
    return audioContext;
}

function ensureSfxMasterGain() {
    const ctx = ensureAudioContext();
    if (!ctx) {
        return null;
    }

    if (sfxMasterGain) {
        return sfxMasterGain;
    }

    sfxMasterGain = ctx.createGain();
    sfxMasterGain.gain.value = DEFAULT_SFX_VOLUME;
    sfxMasterGain.connect(ctx.destination);
    return sfxMasterGain;
}

function playTone(freq, durationSeconds, offsetSeconds = 0, type = 'sine', peakGain = 0.95) {
    const ctx = ensureAudioContext();
    const master = ensureSfxMasterGain();
    if (!ctx || !master) {
        return;
    }

    if (ctx.state === 'suspended') {
        ctx.resume().catch(() => {});
    }

    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    const startAt = ctx.currentTime + Math.max(0, offsetSeconds);
    const endAt = startAt + Math.max(0.03, durationSeconds);

    osc.type = type;
    osc.frequency.setValueAtTime(freq, startAt);

    gain.gain.setValueAtTime(0.0001, startAt);
    gain.gain.exponentialRampToValueAtTime(peakGain, startAt + 0.015);
    gain.gain.exponentialRampToValueAtTime(0.0001, endAt);

    osc.connect(gain);
    gain.connect(master);

    osc.start(startAt);
    osc.stop(endAt + 0.01);
}

function playPitchDrop(startFreq, endFreq, durationSeconds, type = 'square', peakGain = 0.9) {
    const ctx = ensureAudioContext();
    const master = ensureSfxMasterGain();
    if (!ctx || !master) {
        return;
    }

    if (ctx.state === 'suspended') {
        ctx.resume().catch(() => {});
    }

    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    const now = ctx.currentTime;
    const endAt = now + Math.max(0.06, durationSeconds);

    osc.type = type;
    osc.frequency.setValueAtTime(Math.max(30, startFreq), now);
    osc.frequency.exponentialRampToValueAtTime(Math.max(30, endFreq), endAt);

    gain.gain.setValueAtTime(0.0001, now);
    gain.gain.exponentialRampToValueAtTime(peakGain, now + 0.01);
    gain.gain.exponentialRampToValueAtTime(0.0001, endAt);

    osc.connect(gain);
    gain.connect(master);

    osc.start(now);
    osc.stop(endAt + 0.01);
}

function playBrassBlast(freq, durationSeconds, offsetSeconds = 0, peakGain = 0.55) {
    const ctx = ensureAudioContext();
    const master = ensureSfxMasterGain();
    if (!ctx || !master) {
        return;
    }

    if (ctx.state === 'suspended') {
        ctx.resume().catch(() => {});
    }

    const startAt = ctx.currentTime + Math.max(0, offsetSeconds);
    const endAt = startAt + Math.max(0.05, durationSeconds);

    const osc1 = ctx.createOscillator();
    const osc2 = ctx.createOscillator();
    const gain = ctx.createGain();

    osc1.type = 'sawtooth';
    osc2.type = 'square';
    osc1.frequency.setValueAtTime(freq, startAt);
    osc2.frequency.setValueAtTime(freq * 2, startAt);

    gain.gain.setValueAtTime(0.0001, startAt);
    gain.gain.exponentialRampToValueAtTime(peakGain, startAt + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, endAt);

    osc1.connect(gain);
    osc2.connect(gain);
    gain.connect(master);

    osc1.start(startAt);
    osc2.start(startAt);
    osc1.stop(endAt + 0.01);
    osc2.stop(endAt + 0.01);
}

function ensureThemeAudio() {
    if (themeAudio) {
        return themeAudio;
    }

    themeAudio = new Audio(THEME_URL);
    themeAudio.loop = true;
    themeAudio.preload = 'auto';
    themeAudio.volume = DEFAULT_VOLUME;
    return themeAudio;
}

function removeUnlockListeners() {
    if (!unlockListenersBound) {
        return;
    }

    unlockListenersBound = false;
    window.removeEventListener('pointerdown', unlockAudio, true);
    window.removeEventListener('keydown', unlockAudio, true);
    window.removeEventListener('touchstart', unlockAudio, true);
    window.removeEventListener('click', unlockAudio, true);
}

function unlockAudio() {
    const ctx = ensureAudioContext();
    if (ctx && ctx.state === 'suspended') {
        ctx.resume().catch(() => {});
    }

    const audio = ensureThemeAudio();
    audio.muted = true;

    const maybePromise = audio.play();
    if (!maybePromise || typeof maybePromise.then !== 'function') {
        audio.pause();
        audio.currentTime = 0;
        audio.muted = false;
        removeUnlockListeners();
        return;
    }

    maybePromise
        .then(() => {
            audio.pause();
            audio.currentTime = 0;
            audio.muted = false;
            removeUnlockListeners();
        })
        .catch(() => {
            // Keep listeners active until browser allows playback.
        });
}

function bindUnlockListeners() {
    if (unlockListenersBound) {
        return;
    }

    unlockListenersBound = true;
    window.addEventListener('pointerdown', unlockAudio, true);
    window.addEventListener('keydown', unlockAudio, true);
    window.addEventListener('touchstart', unlockAudio, true);
    window.addEventListener('click', unlockAudio, true);
}

export const MatchAudio = {
    prepare() {
        ensureThemeAudio();
        ensureSfxMasterGain();
        bindUnlockListeners();
    },

    playThemeLoop() {
        const audio = ensureThemeAudio();
        audio.muted = false;
        audio.loop = true;

        const playPromise = audio.play();
        if (playPromise && typeof playPromise.catch === 'function') {
            playPromise.catch((err) => {
                console.warn('[AUDIO] Theme playback blocked by browser policy:', err);
                bindUnlockListeners();
            });
        }
    },

    stopTheme() {
        if (!themeAudio) {
            return;
        }
        themeAudio.pause();
        themeAudio.currentTime = 0;
    },

    setThemeMuted(muted) {
        const audio = ensureThemeAudio();
        audio.muted = Boolean(muted);
    },

    setVolume(volume) {
        const audio = ensureThemeAudio();
        const normalized = Math.max(0, Math.min(1, Number(volume)));
        if (Number.isFinite(normalized)) {
            audio.volume = normalized;
        }
    },

    playCorrectSfx() {
        playTone(660, 0.11, 0, 'triangle', 0.75);
        playTone(988, 0.14, 0.1, 'triangle', 0.85);
    },

    playWrongSfx() {
        playTone(240, 0.12, 0, 'sawtooth', 0.9);
        playTone(180, 0.16, 0.1, 'square', 0.75);
    },

    playCountdownPumSfx() {
        // Heavy retro step: low thump + short click for arcade punch.
        playPitchDrop(210, 78, 0.22, 'square', 0.95);
        playTone(64, 0.16, 0, 'triangle', 0.55);
        playTone(920, 0.035, 0.006, 'square', 0.25);
    },

    playPassSfx() {
        // Short confirm click for pass action.
        playTone(520, 0.06, 0, 'triangle', 0.5);
        playTone(690, 0.09, 0.055, 'triangle', 0.55);
    },

    playGameOverCelebrationSfx() {
        // 2s celebratory brass fanfare.
        playBrassBlast(392, 0.32, 0.00, 0.46); // G4
        playBrassBlast(523.25, 0.30, 0.18, 0.48); // C5
        playBrassBlast(659.25, 0.30, 0.36, 0.50); // E5
        playBrassBlast(783.99, 0.34, 0.58, 0.54); // G5

        playBrassBlast(659.25, 0.28, 0.95, 0.47); // E5
        playBrassBlast(783.99, 0.28, 1.12, 0.49); // G5
        playBrassBlast(1046.5, 0.60, 1.32, 0.56); // C6 finale

        // Low support for a fuller trumpet-like body.
        playTone(196, 0.26, 0.02, 'triangle', 0.22);
        playTone(261.63, 0.26, 0.20, 'triangle', 0.24);
        playTone(329.63, 0.30, 0.40, 'triangle', 0.25);
        playTone(392, 0.62, 1.34, 'triangle', 0.3);
    },
};
