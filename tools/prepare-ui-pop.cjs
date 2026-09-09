// Decode an existing CC-BY UI sound, not a synthesized substitute.
// Requires Playwright's Chromium for the standard Web Audio MP3 decoder.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'C:/Users/nic/GitHub/hearth/ui/node_modules/playwright');
const root = path.resolve(__dirname, '..');
const sources = {
  previous: 'https://cdn.freesound.org/previews/253/253956_1196472-hq.mp3',
  ui: 'https://cdn.freesound.org/previews/708/708605_13515726-hq.mp3',
};
const sourceHashes = {
  previous: '09a72dba775c2407cd2227fb5fceb385d5dd18979fc1715f4d07c63d5f444a83',
  ui: '76e68aee31611cc35ee5c12c7e1f90766d1c149765ad3acc444747e20f4d1677',
};
const rate = 44100;
function wav(samples) {
  const out = Buffer.alloc(44 + samples.length * 2);
  out.write('RIFF'); out.writeUInt32LE(out.length - 8, 4); out.write('WAVEfmt ', 8);
  out.writeUInt32LE(16, 16); out.writeUInt16LE(1, 20); out.writeUInt16LE(1, 22);
  out.writeUInt32LE(rate, 24); out.writeUInt32LE(rate * 2, 28);
  out.writeUInt16LE(2, 32); out.writeUInt16LE(16, 34); out.write('data', 36);
  out.writeUInt32LE(samples.length * 2, 40);
  samples.forEach((s, i) => out.writeInt16LE(Math.round(Math.max(-1, Math.min(1, s)) * 32767), 44 + i * 2));
  return out;
}
function stats(samples) {
  let peak = 0, energy = 0, onset = -1, peakAt = 0;
  samples.forEach((value, i) => {
    if (Math.abs(value) > peak) { peak = Math.abs(value); peakAt = i; }
    if (onset < 0 && Math.abs(value) > .02) onset = i;
    energy += value * value;
  });
  return { durationMs: samples.length / rate * 1000, onsetMs: onset / rate * 1000, peakMs: peakAt / rate * 1000, peak, rms: Math.sqrt(energy / samples.length) };
}
(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  const evidence = {};
  try {
    for (const [name, url] of Object.entries(sources)) {
      const response = await fetch(url);
      if (!response.ok) throw new Error(`Download failed: ${response.status}`);
      const source = Buffer.from(await response.arrayBuffer());
      if (crypto.createHash('sha256').update(source).digest('hex') !== sourceHashes[name]) throw new Error(`Review changed source before replacing ${name}`);
      const samples = await page.evaluate(async ({ base64, rate }) => {
        const context = new OfflineAudioContext(1, rate, rate);
        const bytes = Uint8Array.from(atob(base64), c => c.charCodeAt(0));
        const decoded = await context.decodeAudioData(bytes.buffer);
        const channel = decoded.getChannelData(0);
        return Array.from(channel, (_, i) => {
          let sum = 0;
          for (let c = 0; c < decoded.numberOfChannels; c++) sum += decoded.getChannelData(c)[i];
          return sum / decoded.numberOfChannels;
        });
      }, { base64: source.toString('base64'), rate });
      evidence[name] = { url, sourceSha256: crypto.createHash('sha256').update(source).digest('hex'), ...stats(samples) };
      if (name === 'ui') {
        // Remove silence before the first quiet audible sample, retain a 1ms lead-in,
        // and apply only a 1ms edge fade; leave pitch and tone intact.
        const onset = samples.findIndex(s => Math.abs(s) > .003);
        const end = samples.findLastIndex(s => Math.abs(s) > .001);
        const trimmed = samples.slice(Math.max(0, onset - 44), Math.min(samples.length, end + 221));
        const peak = Math.max(...trimmed.map(Math.abs));
        const gain = Math.min(1, .65 / peak);
        const prepared = trimmed.map((s, i) => s * gain * Math.min(1, i / 44, (trimmed.length - 1 - i) / 44));
        const output = wav(prepared);
        fs.writeFileSync(path.join(root, 'app/src/main/res/raw/ui_pop.wav'), output);
        evidence.prepared = { ...stats(prepared), bytes: output.length, sha256: crypto.createHash('sha256').update(output).digest('hex'), gain };
      }
    }
  } finally { await browser.close(); }
  fs.mkdirSync(path.join(root, 'build/audio-audit'), { recursive: true });
  fs.writeFileSync(path.join(root, 'build/audio-audit/source-analysis.json'), JSON.stringify(evidence, null, 2) + '\n');
  console.log(JSON.stringify(evidence, null, 2));
})().catch(error => { console.error(error); process.exitCode = 1; });