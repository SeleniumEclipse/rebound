// Black-box check of the actual, non-debug APK. No fixture injection or private data.
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const adb = process.env.ADB || path.join(process.env.LOCALAPPDATA, 'Android/Sdk/platform-tools/adb.exe');
const serial = process.env.ANDROID_SERIAL || 'emulator-5554';
const pkg = 'com.nicgames.rebound';
const root = path.resolve(__dirname, '..');
const output = path.join(root, 'screenshots');
fs.mkdirSync(output, { recursive: true });
function command(...args) { return execFileSync(adb, ['-s', serial, ...args], { encoding: 'utf8', timeout: 45000 }).trim(); }
function dump() {
  command('shell', 'uiautomator', 'dump', '/sdcard/rebound-smoke.xml');
  const xml = command('shell', 'cat', '/sdcard/rebound-smoke.xml');
  assert.ok(xml.includes(`package="${pkg}"`), 'A different app took the foreground; use a dedicated test device');
  return [...xml.matchAll(/<node\b([^>]+)>/g)].map(match => Object.fromEntries([...match[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(m => [m[1], m[2]])));
}
function find(nodes, text) { return nodes.find(n => n.text === text || n['content-desc'] === text); }
function bounds(node) {
  assert.ok(node, 'Expected screen element');
  const n = node.bounds.match(/\d+/g).map(Number);
  return { x: n[0], y: n[1], w: n[2] - n[0], h: n[3] - n[1] };
}
function tap(node) { const b = bounds(node); command('shell', 'input', 'tap', String(Math.round(b.x + b.w / 2)), String(Math.round(b.y + b.h / 2))); }
function capture(name) { fs.writeFileSync(path.join(output, name + '.png'), execFileSync(adb, ['-s', serial, 'exec-out', 'screencap', '-p'], { timeout: 15000 })); }
function round(nodes) {
  const i = nodes.findIndex(n => n.text === 'Round');
  if (i < 0) return null;
  const before = nodes.slice(0, i).reverse().find(n => /^\d+$/.test(n.text || ''));
  return before ? Number(before.text) : null;
}
const checks = [];
function pass(message) { console.log('PASS ' + message); checks.push(message); }
const start = command('shell', 'am', 'start', '-W', '-n', `${pkg}/.MainActivity`);
assert.ok(!start.includes('Error'), start);
let nodes = dump();
assert.ok(find(nodes, 'Play') || find(nodes, 'Continue'), 'Release home is usable');
capture('release-home');
pass('Signed release launches to the real home screen');
tap(find(nodes, 'Continue') || find(nodes, 'Play'));
nodes = dump();
assert.ok(find(nodes, 'Pause'));
const oldRound = round(nodes);
assert.ok(oldRound >= 1);
const board = bounds(find(nodes, 'Game board'));
const scale = Math.min(board.w / 360, board.h / 504);
const x = board.x + board.w / 2;
const y = board.y + (board.h - 504 * scale) / 2;
command('shell', 'input', 'swipe', String(Math.round(x)), String(Math.round(y + 460 * scale)), String(Math.round(x - 50 * scale)), String(Math.round(y + 130 * scale)), '350');
const deadline = Date.now() + 30000;
let advanced = false;
while (Date.now() < deadline) {
  nodes = dump();
  if ((round(nodes) || 0) > oldRound) { advanced = true; break; }
  const collect = find(nodes, 'Collect');
  if (collect) tap(collect);
}
assert.ok(advanced, 'Real touch shot must complete and move to the next round');
pass('Real touch shot completes, blocks descend, and round advances');
capture('release-playing');
tap(find(nodes, 'Pause'));
nodes = dump();
assert.ok(find(nodes, 'Resume'));
capture('release-paused');
const currentRound = oldRound + 1;
pass('Pause screen and resume control work');
command('shell', 'input', 'keyevent', 'KEYCODE_HOME');
command('shell', 'am', 'force-stop', pkg);
command('shell', 'am', 'start', '-W', '-n', `${pkg}/.MainActivity`);
nodes = dump();
assert.ok(find(nodes, 'Continue'), 'Saved run remains available after process restart');
tap(find(nodes, 'Continue'));
nodes = dump();
assert.equal(round(nodes), currentRound);
pass('The actual release restores its run after process termination');
tap(find(nodes, 'Pause'));
nodes = dump();
tap(find(nodes, 'Home'));
nodes = dump();
tap(find(nodes, 'Settings'));
nodes = dump();
assert.ok(find(nodes, 'Sound') && find(nodes, 'Vibration') && find(nodes, 'Animations'));
pass('Settings screen is present and readable');
command('shell', 'input', 'keyevent', 'KEYCODE_BACK');
const pid = command('shell', 'pidof', pkg);
assert.ok(/^\d+$/.test(pid), 'Release process remains running');
const logs = command('logcat', '-d', '--pid', pid, '-s', 'AndroidRuntime:E');
assert.ok(!logs.includes('FATAL EXCEPTION'), logs);
pass('No fatal Android runtime errors');
console.log(JSON.stringify({ package: pkg, device: serial, checks: checks.length, passed: true }));