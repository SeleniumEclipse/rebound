// Original, deterministic 85 ms wooden/plucked hit. No sampled third-party audio.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const sampleRate = 44100;
const duration = .085;
const count = Math.round(sampleRate * duration);
const wav = Buffer.alloc(44 + count * 2);
wav.write('RIFF', 0); wav.writeUInt32LE(wav.length - 8, 4); wav.write('WAVEfmt ', 8);
wav.writeUInt32LE(16, 16); wav.writeUInt16LE(1, 20); wav.writeUInt16LE(1, 22);
wav.writeUInt32LE(sampleRate, 24); wav.writeUInt32LE(sampleRate * 2, 28);
wav.writeUInt16LE(2, 32); wav.writeUInt16LE(16, 34); wav.write('data', 36);
wav.writeUInt32LE(count * 2, 40);
let seed = 8173, energy = 0, peak = 0;
for (let i = 0; i < count; i++) {
  const t = i / sampleRate;
  seed = (Math.imul(seed, 1664525) + 1013904223) | 0;
  const noise = (seed >>> 0) / 2147483648 - 1;
  const attack = Math.min(1, t / .0015);
  const tail = Math.min(1, (duration - t) / .012);
  const tonal = Math.sin(2 * Math.PI * 780 * t) * .68 + Math.sin(2 * Math.PI * 1563 * t) * .2;
  const value = attack * tail * (tonal * Math.exp(-t * 45) + noise * .14 * Math.exp(-t * 200));
  peak = Math.max(peak, Math.abs(value)); energy += value * value;
  wav.writeInt16LE(Math.round(value * 30000), 44 + i * 2);
}
const rms = Math.sqrt(energy / count);
assert.ok(peak < 1 && peak > .5 && rms > .1, `Unexpected signal: peak=${peak}, rms=${rms}`);
const file = path.resolve(__dirname, '../app/src/main/res/raw/block_hit.wav');
fs.writeFileSync(file, wav);
console.log(`Original block-hit WAV: ${count} samples, ${wav.length} bytes, peak=${peak.toFixed(3)}, RMS=${rms.toFixed(3)}`);