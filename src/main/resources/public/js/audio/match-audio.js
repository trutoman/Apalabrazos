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
};
