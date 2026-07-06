# ascii_art.py
#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import sys
import argparse
from PIL import Image
import shutil

# ANSI-цвета
COLORS = {
    'reset': '\033[0m',
    'bold': '\033[1m',
}

def rgb_to_ansi(r, g, b):
    return f'\033[38;2;{r};{g};{b}m'

# Наборы символов
CHARSETS = {
    'default': [' ', '.', ':', '-', '=', '+', '*', '#', '%', '@'],
    'simple': [' ', '.', 'o', 'O', '0', '@'],
    'detailed': [' ', '`', '.', ',', ':', ';', '+', '*', '?', '%', 'S', '#', '@'],
    'block': [' ', '░', '▒', '▓', '█']
}

def get_terminal_width():
    try:
        return shutil.get_terminal_size().columns
    except:
        return 80

def image_to_ascii(image_path, width=0, height=0, charset='default', invert=False, color=False, verbose=False):
    img = Image.open(image_path).convert('RGB')
    orig_w, orig_h = img.size

    if width == 0 and height == 0:
        width = get_terminal_width() // 2
    if width == 0:
        width = int(orig_w * height / orig_h)
    if height == 0:
        height = int(orig_h * width / orig_w)

    # Сохраняем пропорции
    if width > 0 and height == 0:
        height = int(orig_h * width / orig_w)
    if height > 0 and width == 0:
        width = int(orig_w * height / orig_h)

    img = img.resize((width, height), Image.Resampling.LANCZOS)
    pixels = img.getdata()
    chars = CHARSETS.get(charset, CHARSETS['default'])

    result = []
    for i in range(height):
        line = ''
        for j in range(width):
            r, g, b = pixels[i * width + j]
            gray = int(0.299 * r + 0.587 * g + 0.114 * b)
            if invert:
                gray = 255 - gray
            idx = int(gray / 255 * (len(chars) - 1))
            ch = chars[idx]
            if color:
                line += rgb_to_ansi(r, g, b) + ch
            else:
                line += ch
        if color:
            line += COLORS['reset']
        result.append(line)
    return '\n'.join(result)

def batch_convert(input_dir, output_dir, **kwargs):
    os.makedirs(output_dir, exist_ok=True)
    exts = ('.png', '.jpg', '.jpeg', '.bmp', '.tiff', '.webp')
    files = [f for f in os.listdir(input_dir) if f.lower().endswith(exts)]
    total = len(files)
    for i, f in enumerate(files):
        in_path = os.path.join(input_dir, f)
        out_path = os.path.join(output_dir, os.path.splitext(f)[0] + '.txt')
        if kwargs.get('verbose'):
            print(f"[{i+1}/{total}] {f} -> {out_path}")
        ascii_art = image_to_ascii(in_path, **kwargs)
        with open(out_path, 'w', encoding='utf-8') as out_f:
            out_f.write(ascii_art)
        if not kwargs.get('verbose'):
            pct = int((i+1) / total * 100)
            bar = '█' * (pct // 2) + '░' * (50 - pct // 2)
            print(f"\r[{bar}] {pct}% {i+1}/{total}", end='', flush=True)
    if not kwargs.get('verbose'):
        print()

def main():
    parser = argparse.ArgumentParser(description="ASCII Art Converter")
    parser.add_argument('input', help='Входной файл или папка')
    parser.add_argument('-o', '--output', help='Выходной файл или папка (для batch)')
    parser.add_argument('-w', '--width', type=int, default=0, help='Ширина ASCII (в символах)')
    parser.add_argument('-c', '--charset', choices=['default', 'simple', 'detailed', 'block'], default='default', help='Набор символов')
    parser.add_argument('-i', '--invert', action='store_true', help='Инвертировать яркость')
    parser.add_argument('-n', '--no-color', action='store_true', help='Отключить цвет')
    parser.add_argument('-b', '--batch', action='store_true', help='Пакетная обработка')
    parser.add_argument('-v', '--verbose', action='store_true', help='Подробный вывод')
    args = parser.parse_args()

    kwargs = {
        'width': args.width,
        'charset': args.charset,
        'invert': args.invert,
        'color': not args.no_color,
        'verbose': args.verbose,
    }

    if args.batch:
        if not args.output:
            print("Укажите выходную папку для пакетной обработки", file=sys.stderr)
            sys.exit(1)
        batch_convert(args.input, args.output, **kwargs)
        print(f"✅ Пакетная обработка завершена. Результаты в {args.output}")
    else:
        ascii_art = image_to_ascii(args.input, **kwargs)
        if args.output:
            with open(args.output, 'w', encoding='utf-8') as f:
                f.write(ascii_art)
            print(f"✅ Результат сохранён в {args.output}")
        else:
            print(ascii_art)

if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print("\nПрервано.")
        sys.exit(0)
