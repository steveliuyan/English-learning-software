const fs = require('fs');
const zlib = require('zlib');

function crcTable() {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c;
  }
  return t;
}
const CRC = crcTable();
function crc32(buf) {
  let c = -1;
  for (let i = 0; i < buf.length; i++) c = CRC[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ -1) >>> 0;
}

function paeth(a, b, c) {
  const p = a + b - c;
  const pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
  if (pa <= pb && pa <= pc) return a;
  if (pb <= pc) return b;
  return c;
}

function readPng(file) {
  const buf = fs.readFileSync(file);
  if (buf.readUInt32BE(0) !== 0x89504e47) throw new Error('not png: ' + file);
  let off = 8, width = 0, height = 0, bitDepth = 0, colorType = 0, interlace = 0;
  const idat = [];
  while (off < buf.length) {
    const len = buf.readUInt32BE(off);
    const type = buf.toString('ascii', off + 4, off + 8);
    const data = buf.subarray(off + 8, off + 8 + len);
    if (type === 'IHDR') {
      width = data.readUInt32BE(0);
      height = data.readUInt32BE(4);
      bitDepth = data[8];
      colorType = data[9];
      interlace = data[12];
    } else if (type === 'IDAT') idat.push(data);
    else if (type === 'IEND') break;
    off += 12 + len;
  }
  if (bitDepth !== 8 || interlace !== 0 || (colorType !== 2 && colorType !== 6)) {
    throw new Error(`unsupported png ${file} depth=${bitDepth} color=${colorType} interlace=${interlace}`);
  }
  const bpp = colorType === 6 ? 4 : 3;
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const rowBytes = width * bpp;
  const out = new Uint8Array(width * height * 4);
  const prev = new Uint8Array(rowBytes);
  const cur = new Uint8Array(rowBytes);
  for (let y = 0; y < height; y++) {
    const f = raw[y * (rowBytes + 1)];
    const line = raw.subarray(y * (rowBytes + 1) + 1, (y + 1) * (rowBytes + 1));
    for (let x = 0; x < rowBytes; x++) {
      const a = x >= bpp ? cur[x - bpp] : 0;
      const b = prev[x];
      const c = x >= bpp ? prev[x - bpp] : 0;
      let v = line[x];
      if (f === 1) v += a;
      else if (f === 2) v += b;
      else if (f === 3) v += (a + b) >> 1;
      else if (f === 4) v += paeth(a, b, c);
      cur[x] = v & 0xff;
    }
    const o = y * width * 4;
    for (let x = 0; x < width; x++) {
      const s = x * bpp;
      out[o + x * 4] = cur[s];
      out[o + x * 4 + 1] = cur[s + 1];
      out[o + x * 4 + 2] = cur[s + 2];
      out[o + x * 4 + 3] = colorType === 6 ? cur[s + 3] : 255;
    }
    prev.set(cur);
  }
  return { width, height, data: out };
}

function writePng(file, width, height, data) {
  const rowBytes = width * 4;
  const raw = Buffer.alloc((rowBytes + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (rowBytes + 1)] = 0;
    Buffer.from(data.buffer, data.byteOffset + y * rowBytes, rowBytes).copy(raw, y * (rowBytes + 1) + 1);
  }
  const idat = zlib.deflateSync(raw, { level: 9 });
  const chunks = [];
  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  function chunk(type, payload) {
    const b = Buffer.alloc(12 + payload.length);
    b.writeUInt32BE(payload.length, 0);
    b.write(type, 4, 'ascii');
    payload.copy(b, 8);
    b.writeUInt32BE(crc32(b.subarray(4, 8 + payload.length)), 8 + payload.length);
    return b;
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  chunks.push(sig, chunk('IHDR', ihdr), chunk('IDAT', idat), chunk('IEND', Buffer.alloc(0)));
  fs.writeFileSync(file, Buffer.concat(chunks));
}

module.exports = { readPng, writePng };
