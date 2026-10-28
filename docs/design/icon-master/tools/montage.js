const fs = require('fs');
const path = require('path');
const { readPng, writePng } = require('./png');

const [inDir, outFile, colsArg, cellArg] = process.argv.slice(2);
const cols = parseInt(colsArg || '7', 10);
const cell = parseInt(cellArg || '140', 10);
const files = fs.readdirSync(inDir).filter((f) => f.endsWith('.png')).sort();
const rows = Math.ceil(files.length / cols);
const W = cols * cell, H = rows * cell;
const out = new Uint8Array(W * H * 4);

for (let y = 0; y < H; y++) {
  for (let x = 0; x < W; x++) {
    const o = (y * W + x) * 4;
    const checker = ((x >> 3) + (y >> 3)) % 2 === 0 ? 238 : 226;
    out[o] = checker; out[o + 1] = checker + 4; out[o + 2] = checker; out[o + 3] = 255;
  }
}

files.forEach((f, i) => {
  const img = readPng(path.join(inDir, f));
  const cx = (i % cols) * cell, cy = Math.floor(i / cols) * cell;
  const ox = cx + Math.round((cell - img.width) / 2);
  const oy = cy + Math.round((cell - img.height) / 2);
  for (let y = 0; y < img.height; y++) {
    for (let x = 0; x < img.width; x++) {
      const s = (y * img.width + x) * 4;
      const a = img.data[s + 3] / 255;
      if (a === 0) continue;
      const dx = ox + x, dy = oy + y;
      if (dx < 0 || dy < 0 || dx >= W || dy >= H) continue;
      const d = (dy * W + dx) * 4;
      out[d] = Math.round(img.data[s] * a + out[d] * (1 - a));
      out[d + 1] = Math.round(img.data[s + 1] * a + out[d + 1] * (1 - a));
      out[d + 2] = Math.round(img.data[s + 2] * a + out[d + 2] * (1 - a));
      out[d + 3] = 255;
    }
  }
});
writePng(outFile, W, H, out);
console.log(`${files.length} icons -> ${outFile} (${W}x${H})`);
