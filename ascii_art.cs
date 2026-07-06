// ascii_art.cs
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.Processing;
using SixLabors.ImageSharp.PixelFormats;

class AsciiArt
{
    static string Colorize(string text, string color) => color + text + "\x1b[0m";
    static string RgbToAnsi(byte r, byte g, byte b) => $"\x1b[38;2;{r};{g};{b}m";

    static Dictionary<string, char[]> CHARSETS = new Dictionary<string, char[]>
    {
        {"default", new char[]{' ','.',':','-','=','+','*','#','%','@'}},
        {"simple", new char[]{' ','.','o','O','0','@'}},
        {"detailed", new char[]{' ','`','.',',',':',';','+','*','?','%','S','#','@'}},
        {"block", new char[]{' ','░','▒','▓','█'}}
    };

    static int GetTerminalWidth()
    {
        try { return Console.WindowWidth; } catch { return 80; }
    }

    static string ImageToAscii(string path, int width, int height, string charsetName, bool invert, bool color)
    {
        using var img = Image.Load<Rgb24>(path);
        int origW = img.Width, origH = img.Height;

        if (width == 0 && height == 0)
            width = GetTerminalWidth() / 2;
        if (width == 0)
            width = origW * height / origH;
        if (height == 0)
            height = origH * width / origW;
        if (width < 1) width = 1;
        if (height < 1) height = 1;

        img.Mutate(ctx => ctx.Resize(width, height));

        var chars = CHARSETS.ContainsKey(charsetName) ? CHARSETS[charsetName] : CHARSETS["default"];
        var sb = new StringBuilder();
        for (int y = 0; y < img.Height; y++)
        {
            for (int x = 0; x < img.Width; x++)
            {
                var pixel = img[x, y];
                int gray = (int)(0.299 * pixel.R + 0.587 * pixel.G + 0.114 * pixel.B);
                if (invert) gray = 255 - gray;
                int idx = gray * (chars.Length - 1) / 255;
                if (color)
                    sb.Append(RgbToAnsi(pixel.R, pixel.G, pixel.B));
                sb.Append(chars[idx]);
            }
            if (color) sb.Append("\x1b[0m");
            sb.Append('\n');
        }
        return sb.ToString();
    }

    static void BatchConvert(string inputDir, string outputDir, int width, int height, string charset, bool invert, bool color, bool verbose)
    {
        Directory.CreateDirectory(outputDir);
        var exts = new HashSet<string>{".png",".jpg",".jpeg",".bmp",".tiff",".webp"};
        var files = Directory.GetFiles(inputDir, "*.*", SearchOption.TopDirectoryOnly)
            .Where(f => exts.Contains(Path.GetExtension(f).ToLower()))
            .ToList();
        int total = files.Count;
        for (int i = 0; i < total; i++)
        {
            string outPath = Path.Combine(outputDir, Path.GetFileNameWithoutExtension(files[i]) + ".txt");
            if (verbose)
                Console.WriteLine($"[{i+1}/{total}] {files[i]} -> {outPath}");
            string ascii = ImageToAscii(files[i], width, height, charset, invert, color);
            File.WriteAllText(outPath, ascii);
            if (!verbose)
            {
                int pct = (i+1) * 100 / total;
                Console.Write($"\r[{new string('█', pct/2)}{new string('░', 50-pct/2)}] {pct}% {i+1}/{total}");
            }
        }
        if (!verbose) Console.WriteLine();
        Console.WriteLine($"✅ Batch conversion completed. Results in {outputDir}");
    }

    static int Main(string[] args)
    {
        string input = null, output = null;
        int width = 0, height = 0;
        string charset = "default";
        bool invert = false, noColor = false, batch = false, verbose = false;

        for (int i = 0; i < args.Length; i++)
        {
            if (args[i] == "-o" && i+1 < args.Length) output = args[++i];
            else if (args[i] == "-w" && i+1 < args.Length) width = int.Parse(args[++i]);
            else if (args[i] == "-h" && i+1 < args.Length) height = int.Parse(args[++i]);
            else if (args[i] == "-c" && i+1 < args.Length) charset = args[++i];
            else if (args[i] == "-i") invert = true;
            else if (args[i] == "-n") noColor = true;
            else if (args[i] == "-b") batch = true;
            else if (args[i] == "-v") verbose = true;
            else if (args[i] == "-h" || args[i] == "--help")
            {
                Console.WriteLine("Usage: ascii_art <input> [options]\n  -o <file>   Output file/dir\n  -w <N>      Width\n  -h <N>      Height\n  -c <set>    Charset: default, simple, detailed, block\n  -i          Invert\n  -n          No color\n  -b          Batch\n  -v          Verbose");
                return 0;
            }
            else if (input == null) input = args[i];
        }
        if (input == null) { Console.Error.WriteLine("Укажите входной файл или папку."); return 1; }

        if (batch)
        {
            if (output == null) { Console.Error.WriteLine("Укажите выходную папку для batch."); return 1; }
            BatchConvert(input, output, width, height, charset, invert, !noColor, verbose);
        }
        else
        {
            string ascii = ImageToAscii(input, width, height, charset, invert, !noColor);
            if (output != null)
            {
                File.WriteAllText(output, ascii);
                Console.WriteLine($"✅ Result saved to {output}");
            }
            else
            {
                Console.Write(ascii);
            }
        }
        return 0;
    }
}
