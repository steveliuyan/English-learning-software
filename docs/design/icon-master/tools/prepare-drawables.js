const fs = require('fs');
const path = require('path');
const { readPng, writePng } = require('./png');

const BG_TOL = 14;
const FEATHER_TOL = 34;

function px(data, w, x, y) {
  const o = (y * w + x) * 4;
  return [data[o], data[o + 1], data[o + 2], data[o + 3]];
}

function dist(c, bg) {
  const dr = c[0] - bg[0], dg = c[1] - bg[1], db = c[2] - bg[2];
  return Math.sqrt(dr * dr + dg * dg + db * db);
}

function detectBg(img) {
  const { width: w, height: h, data } = img;
  const samples = [];
  const pick = (x, y) => samples.push(px(data, w, x, y));
  pick(2, 2); pick(w - 3, 2); pick(2, h - 3); pick(w - 3, h - 3);
  pick(w >> 1, 2); pick(w >> 1, h - 3); pick(2, h >> 1); pick(w - 3, h >> 1);
  const avg = [0, 0, 0];
  for (const s of samples) { avg[0] += s[0]; avg[1] += s[1]; avg[2] += s[2]; }
  return avg.map((v) => Math.round(v / samples.length));
}

function cutBackground(img) {
  const { width: w, height: h, data } = img;
  const bg = detectBg(img);
  const n = w * h;
  const alpha = new Uint8ClampedArray(n);
  const visited = new Uint8Array(n);
  const queue = new Int32Array(n);
  let qh = 0, qt = 0;
  const push = (i) => { if (!visited[i]) { visited[i] = 1; queue[qt++] = i; } };
  for (let x = 0; x < w; x++) { push(x); push((h - 1) * w + x); }
  for (let y = 0; y < h; y++) { push(y * w); push(y * w + w - 1); }
  while (qh < qt) {
    const i = queue[qh++];
    const x = i % w, y = (i - x) / w;
    const o = i * 4;
    const d = dist([data[o], data[o + 1], data[o + 2]], bg);
    if (d > FEATHER_TOL) { visited[i] = 0; continue; }
    const a = d <= BG_TOL ? 0 : Math.round((255 * (d - BG_TOL)) / (FEATHER_TOL - BG_TOL));
    alpha[i] = a;
    if (a === 0) {
      if (x > 0) push(i - 1);
      if (x < w - 1) push(i + 1);
      if (y > 0) push(i - w);
      if (y < h - 1) push(i + w);
    }
  }
  for (let i = 0; i < n; i++) {
    if (visited[i] && alpha[i] === 0) data[i * 4 + 3] = 0;
    else if (visited[i]) data[i * 4 + 3] = Math.min(data[i * 4 + 3], alpha[i]);
  }
  return bg;
}

function bbox(img) {
  const { width: w, height: h, data } = img;
  let x0 = w, y0 = h, x1 = -1, y1 = -1;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      if (data[(y * w + x) * 4 + 3] > 16) {
        if (x < x0) x0 = x;
        if (x > x1) x1 = x;
        if (y < y0) y0 = y;
        if (y > y1) y1 = y;
      }
    }
  }
  if (x1 < 0) return null;
  return { x0, y0, x1, y1 };
}

function cropSquare(img, box, marginFraction) {
  const bw = box.x1 - box.x0 + 1;
  const bh = box.y1 - box.y0 + 1;
  const side = Math.round(Math.max(bw, bh) * (1 + marginFraction * 2));
  const out = new Uint8Array(side * side * 4);
  const ox = Math.round((side - bw) / 2);
  const oy = Math.round((side - bh) / 2);
  const { width: w, data } = img;
  for (let y = 0; y < bh; y++) {
    for (let x = 0; x < bw; x++) {
      const s = ((box.y0 + y) * w + (box.x0 + x)) * 4;
      const dx = ox + x, dy = oy + y;
      if (dx < 0 || dy < 0 || dx >= side || dy >= side) continue;
      const d = (dy * side + dx) * 4;
      out[d] = data[s];
      out[d + 1] = data[s + 1];
      out[d + 2] = data[s + 2];
      out[d + 3] = data[s + 3];
    }
  }
  return { width: side, height: side, data: out };
}

function resizeArea(img, size) {
  const { width: sw, height: sh, data } = img;
  const out = new Uint8Array(size * size * 4);
  const sx = sw / size, sy = sh / size;
  for (let dy = 0; dy < size; dy++) {
    const y0 = Math.floor(dy * sy), y1 = Math.min(sh, Math.max(y0 + 1, Math.ceil((dy + 1) * sy)));
    for (let dx = 0; dx < size; dx++) {
      const x0 = Math.floor(dx * sx), x1 = Math.min(sw, Math.max(x0 + 1, Math.ceil((dx + 1) * sx)));
      let r = 0, g = 0, b = 0, a = 0, cnt = 0;
      for (let y = y0; y < y1; y++) {
        for (let x = x0; x < x1; x++) {
          const s = (y * sw + x) * 4;
          const al = data[s + 3] / 255;
          r += data[s] * al;
          g += data[s + 1] * al;
          b += data[s + 2] * al;
          a += data[s + 3];
          cnt++;
        }
      }
      const d = (dy * size + dx) * 4;
      const aSum = a / cnt;
      const al = aSum / 255;
      if (al > 0.0001) {
        out[d] = Math.min(255, Math.round(r / cnt / al));
        out[d + 1] = Math.min(255, Math.round(g / cnt / al));
        out[d + 2] = Math.min(255, Math.round(b / cnt / al));
      }
      out[d + 3] = Math.round(aSum);
    }
  }
  return { width: size, height: size, data: out };
}

function processOne(file, outFile, size, margin) {
  const img = readPng(file);
  const bg = cutBackground(img);
  const box = bbox(img);
  if (!box) throw new Error('empty after cut: ' + file);
  const square = cropSquare(img, box, margin);
  const resized = resizeArea(square, size);
  writePng(outFile, size, size, resized.data);
  const bw = box.x1 - box.x0 + 1, bh = box.y1 - box.y0 + 1;
  return { bg, box, bw, bh };
}

const [mode, ...rest] = process.argv.slice(2);

if (mode === 'analyze') {
  for (const f of rest) {
    const img = readPng(f);
    const bg = cutBackground(img);
    const box = bbox(img);
    let opaque = 0;
    for (let i = 3; i < img.data.length; i += 4) if (img.data[i] > 16) opaque++;
    console.log(`${path.basename(f)} | ${img.width}x${img.height} | bg=rgb(${bg.join(',')}) | ink=${(100 * opaque / (img.width * img.height)).toFixed(1)}% | bbox=${box ? `${box.x0},${box.y0}-${box.x1},${box.y1} (${box.x1 - box.x0 + 1}x${box.y1 - box.y0 + 1})` : 'EMPTY'}`);
  }
} else if (mode === 'batch') {
  const [inDir, outDir, sizeArg, marginArg] = rest;
  const size = parseInt(sizeArg || '256', 10);
  const margin = parseFloat(marginArg || '0.06');
  fs.mkdirSync(outDir, { recursive: true });
  const files = fs.readdirSync(inDir).filter((f) => /^icon-color-calm__\d\d-.*\.png$/.test(f)).sort();
  for (const f of files) {
    const out = path.join(outDir, 'app_icon_' + f.replace(/^icon-color-calm__\d\d-/, ''));
    const r = processOne(path.join(inDir, f), out, size, margin);
    console.log(`${f} -> ${path.basename(out)} | bg=rgb(${r.bg.join(',')}) | ink=${r.bw}x${r.bh}`);
  }
  console.log(`done ${files.length} files -> ${outDir}`);
}
