// ascii_art.java
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;

public class ascii_art {
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";

    private static String rgbToAnsi(int r, int g, int b) {
        return String.format("\u001B[38;2;%d;%d;%dm", r, g, b);
    }

    private static final String[][] CHARSETS = {
        {" ", ".", ":", "-", "=", "+", "*", "#", "%", "@"},           // default
        {" ", ".", "o", "O", "0", "@"},                               // simple
        {" ", "`", ".", ",", ":", ";", "+", "*", "?", "%", "S", "#", "@"}, // detailed
        {" ", "░", "▒", "▓", "█"}                                     // block
    };

    private static int getTerminalWidth() {
        try {
            return Integer.parseInt(System.getenv("COLUMNS"));
        } catch (Exception e) {
            return 80;
        }
    }

    private static String imageToAscii(String path, int width, int height, int charsetIdx, boolean invert, boolean color) throws Exception {
        BufferedImage img = ImageIO.read(new File(path));
        if (img == null) throw new Exception("Unsupported format");
        int origW = img.getWidth(), origH = img.getHeight();

        if (width == 0 && height == 0) {
            width = getTerminalWidth() / 2;
        }
        if (width == 0) width = origW * height / origH;
        if (height == 0) height = origH * width / origW;
        if (width < 1) width = 1;
        if (height < 1) height = 1;

        Image scaled = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.drawImage(scaled, 0, 0, null);
        g.dispose();

        String[] chars = CHARSETS[charsetIdx];
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = resized.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g2 = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int gray = (int)(0.299 * r + 0.587 * g2 + 0.114 * b);
                if (invert) gray = 255 - gray;
                int idx = gray * (chars.length - 1) / 255;
                if (color) sb.append(rgbToAnsi(r, g2, b));
                sb.append(chars[idx]);
            }
            if (color) sb.append(RESET);
            sb.append('\n');
        }
        return sb.toString();
    }

    private static void batchConvert(String inputDir, String outputDir, int width, int height, int charsetIdx, boolean invert, boolean color, boolean verbose) throws Exception {
        Files.createDirectories(Paths.get(outputDir));
        String[] exts = {".png", ".jpg", ".jpeg", ".bmp", ".tiff", ".webp"};
        Set<String> extSet = new HashSet<>(Arrays.asList(exts));
        File[] files = new File(inputDir).listFiles();
        if (files == null) return;
        int total = 0;
        java.util.List<File> imageFiles = new ArrayList<>();
        for (File f : files) {
            if (f.isFile() && extSet.contains(f.getName().substring(f.getName().lastIndexOf('.')).toLowerCase())) {
                imageFiles.add(f);
            }
        }
        total = imageFiles.size();
        for (int i = 0; i < total; i++) {
            File f = imageFiles.get(i);
            String outPath = outputDir + File.separator + f.getName().replaceFirst("\\.[^.]+$", "") + ".txt";
            if (verbose) {
                System.out.println("[" + (i+1) + "/" + total + "] " + f.getName() + " -> " + outPath);
            }
            String ascii = imageToAscii(f.getPath(), width, height, charsetIdx, invert, color);
            Files.write(Paths.get(outPath), ascii.getBytes());
            if (!verbose) {
                int pct = (i+1) * 100 / total;
                System.out.printf("\r[%s] %d%% %d/%d", "█".repeat(pct/2) + "░".repeat(50-pct/2), pct, i+1, total);
            }
        }
        if (!verbose) System.out.println();
        System.out.println("✅ Batch conversion completed. Results in " + outputDir);
    }

    public static void main(String[] args) throws Exception {
        String input = null, output = null;
        int width = 0, height = 0, charsetIdx = 0;
        boolean invert = false, noColor = false, batch = false, verbose = false;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-o") && i+1 < args.length) output = args[++i];
            else if (args[i].equals("-w") && i+1 < args.length) width = Integer.parseInt(args[++i]);
            else if (args[i].equals("-h") && i+1 < args.length) height = Integer.parseInt(args[++i]);
            else if (args[i].equals("-c") && i+1 < args.length) {
                String cs = args[++i];
                if (cs.equals("default")) charsetIdx = 0;
                else if (cs.equals("simple")) charsetIdx = 1;
                else if (cs.equals("detailed")) charsetIdx = 2;
                else if (cs.equals("block")) charsetIdx = 3;
            } else if (args[i].equals("-i")) invert = true;
            else if (args[i].equals("-n")) noColor = true;
            else if (args[i].equals("-b")) batch = true;
            else if (args[i].equals("-v")) verbose = true;
            else if (args[i].equals("-h") || args[i].equals("--help")) {
                System.out.println("Usage: ascii_art <input> [options]\n  -o <file>   Output file/dir\n  -w <N>      Width\n  -h <N>      Height\n  -c <set>    Charset\n  -i          Invert\n  -n          No color\n  -b          Batch\n  -v          Verbose");
                return;
            } else if (input == null) input = args[i];
        }
        if (input == null) { System.err.println("Укажите входной файл или папку."); System.exit(1); }

        if (batch) {
            if (output == null) { System.err.println("Укажите выходную папку для batch."); System.exit(1); }
            batchConvert(input, output, width, height, charsetIdx, invert, !noColor, verbose);
        } else {
            String ascii = imageToAscii(input, width, height, charsetIdx, invert, !noColor);
            if (output != null) {
                Files.write(Paths.get(output), ascii.getBytes());
                System.out.println("✅ Result saved to " + output);
            } else {
                System.out.print(ascii);
            }
        }
    }
}
