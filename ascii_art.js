// ascii_art.js
#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');
const sharp = require('sharp');
const { execSync } = require('child_process');

const reset = '\x1b[0m';
const bold = '\x1b[1m';

function rgbToAnsi(r, g, b) {
    return `\x1b[38;2;${r};${g};${b}m`;
}

const charsets = {
    default: [' ', '.', ':', '-', '=', '+', '*', '#', '%', '@'],
    simple: [' ', '.', 'o', 'O', '0', '@'],
    detailed: [' ', '`', '.', ',', ':', ';', '+', '*', '?', '%', 'S', '#', '@'],
    block: [' ', '░', '▒', '▓', '█']
};

function getTerminalWidth() {
    try {
        return process.stdout.columns || 80;
    } catch {
        return 80;
    }
}

async function imageToAscii(input, width, height, charsetName, invert, color) {
    const meta = await sharp(input).metadata();
    let origW = meta.width, origH = meta.height;

    if (width === 0 && height === 0) {
        width = Math.floor(getTerminalWidth() / 2);
    }
    if (width === 0) {
        width = Math.floor(origW * height / origH);
    }
    if (height === 0) {
        height = Math.floor(origH * width / origW);
    }
    if (width < 1) width = 1;
    if (height < 1) height = 1;

    const buffer = await sharp(input)
        .resize(width, height, { kernel: sharp.kernel.lanczos2 })
        .raw()
        .toBuffer({ resolveWithObject: true });

    const data = buffer.data;
    const w = buffer.info.width;
    const h = buffer.info.height;
    const chars = charsets[charsetName] || charsets.default;
    let result = '';

    for (let y = 0; y < h; y++) {
        for (let x = 0; x < w; x++) {
            const idx = (y * w + x) * 3;
            const r = data[idx], g = data[idx+1], b = data[idx+2];
            const gray = Math.round(0.299 * r + 0.587 * g + 0.114 * b);
            const finalGray = invert ? 255 - gray : gray;
            const ci = Math.floor(finalGray / 255 * (chars.length - 1));
            if (color) {
                result += rgbToAnsi(r, g, b);
            }
            result += chars[ci];
        }
        if (color) result += reset;
        result += '\n';
    }
    return result;
}

async function batchConvert(inputDir, outputDir, opts) {
    if (!fs.existsSync(outputDir)) fs.mkdirSync(outputDir, { recursive: true });
    const exts = ['.png', '.jpg', '.jpeg', '.bmp', '.tiff', '.webp'];
    const files = fs.readdirSync(inputDir)
        .filter(f => exts.includes(path.extname(f).toLowerCase()));
    const total = files.length;
    for (let i = 0; i < total; i++) {
        const inPath = path.join(inputDir, files[i]);
        const outPath = path.join(outputDir, path.basename(files[i], path.extname(files[i])) + '.txt');
        if (opts.verbose) {
            console.log(`[${i+1}/${total}] ${files[i]} -> ${outPath}`);
        }
        const ascii = await imageToAscii(inPath, opts.width, opts.height, opts.charset, opts.invert, opts.color);
        fs.writeFileSync(outPath, ascii, 'utf8');
        if (!opts.verbose) {
            const pct = Math.floor((i+1) / total * 100);
            const bar = '█'.repeat(Math.floor(pct/2)) + '░'.repeat(50 - Math.floor(pct/2));
            process.stdout.write(`\r[${bar}] ${pct}% ${i+1}/${total}`);
        }
    }
    if (!opts.verbose) console.log();
    console.log(`✅ Batch conversion completed. Results in ${outputDir}`);
}

async function main() {
    const args = process.argv.slice(2);
    let input = null, output = null, width = 0, height = 0;
    let charset = 'default', invert = false, noColor = false, batch = false, verbose = false;

    for (let i = 0; i < args.length; i++) {
        if (args[i] === '-o' && i+1 < args.length) output = args[++i];
        else if (args[i] === '-w' && i+1 < args.length) width = parseInt(args[++i], 10);
        else if (args[i] === '-h' && i+1 < args.length) height = parseInt(args[++i], 10);
        else if (args[i] === '-c' && i+1 < args.length) charset = args[++i];
        else if (args[i] === '-i') invert = true;
        else if (args[i] === '-n') noColor = true;
        else if (args[i] === '-b') batch = true;
        else if (args[i] === '-v') verbose = true;
        else if (args[i] === '-h' || args[i] === '--help') {
            console.log(`Usage: node ascii_art.js <input> [options]
  -o <file>   Output file/dir
  -w <N>      Width
  -h <N>      Height
  -c <set>    Charset: default, simple, detailed, block
  -i          Invert
  -n          No color
  -b          Batch
  -v          Verbose`);
            process.exit(0);
        } else if (!input) input = args[i];
    }
    if (!input) { console.error('Укажите входной файл или папку.'); process.exit(1); }

    const opts = { width, height, charset, invert, color: !noColor, verbose };

    if (batch) {
        if (!output) { console.error('Укажите выходную папку для batch.'); process.exit(1); }
        await batchConvert(input, output, opts);
    } else {
        const ascii = await imageToAscii(input, width, height, charset, invert, !noColor);
        if (output) {
            fs.writeFileSync(output, ascii, 'utf8');
            console.log(`✅ Result saved to ${output}`);
        } else {
            console.log(ascii);
        }
    }
}

main().catch(console.error);
