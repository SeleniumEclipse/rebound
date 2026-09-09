// Reproduce the CC0 audio asset; hash pins the exact source, not just its URL.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const url = 'https://cdn.freesound.org/previews/253/253956_1196472-hq.mp3';
const sha256 = '09a72dba775c2407cd2227fb5fceb385d5dd18979fc1715f4d07c63d5f444a83';
(async () => {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`Audio download returned ${response.status}`);
  const data = Buffer.from(await response.arrayBuffer());
  if (crypto.createHash('sha256').update(data).digest('hex') !== sha256) throw new Error('Source changed; review the file and license before updating');
  fs.writeFileSync(path.resolve(__dirname, '../app/src/main/res/raw/block_pop.mp3'), data);
  console.log(`CC0 Bubble Pop by Mafon2: ${data.length} bytes, SHA-256 ${sha256}`);
})().catch(error => { console.error(error); process.exitCode = 1; });