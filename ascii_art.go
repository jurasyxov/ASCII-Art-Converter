// ascii_art.go
package main

import (
	"flag"
	"fmt"
	"image"
	_ "image/gif"
	_ "image/jpeg"
	_ "image/png"
	"io/fs"
	"io/ioutil"
	"os"
	"path/filepath"
	"strings"

	"github.com/disintegration/imaging"
	"golang.org/x/term"
)

const reset = "\033[0m"
const bold = "\033[1m"

func rgbToAnsi(r, g, b uint8) string {
	return fmt.Sprintf("\033[38;2;%d;%d;%dm", r, g, b)
}

var charsets = [][]string{
	{" ", ".", ":", "-", "=", "+", "*", "#", "%", "@"},                // default
	{" ", ".", "o", "O", "0", "@"},                                    // simple
	{" ", "`", ".", ",", ":", ";", "+", "*", "?", "%", "S", "#", "@"}, // detailed
	{" ", "░", "▒", "▓", "█"},                                          // block
}

func getTerminalWidth() int {
	if term.IsTerminal(int(os.Stdout.Fd())) {
		w, _, err := term.GetSize(int(os.Stdout.Fd()))
		if err == nil {
			return w
		}
	}
	return 80
}

func imageToAscii(path string, width, height int, charsetIdx int, invert, color bool) (string, error) {
	img, err := imaging.Open(path)
	if err != nil {
		return "", err
	}
	origW, origH := img.Bounds().Dx(), img.Bounds().Dy()

	if width == 0 && height == 0 {
		width = getTerminalWidth() / 2
	}
	if width == 0 {
		width = origW * height / origH
	}
	if height == 0 {
		height = origH * width / origW
	}
	img = imaging.Resize(img, width, height, imaging.Lanczos)

	chars := charsets[charsetIdx]
	var result strings.Builder
	bounds := img.Bounds()
	for y := bounds.Min.Y; y < bounds.Max.Y; y++ {
		for x := bounds.Min.X; x < bounds.Max.X; x++ {
			c := img.At(x, y)
			r, g, b, _ := c.RGBA()
			r8, g8, b8 := uint8(r>>8), uint8(g>>8), uint8(b>>8)
			gray := int(0.299*float64(r8) + 0.587*float64(g8) + 0.114*float64(b8))
			if invert {
				gray = 255 - gray
			}
			idx := gray * (len(chars) - 1) / 255
			if color {
				result.WriteString(rgbToAnsi(r8, g8, b8))
			}
			result.WriteString(chars[idx])
		}
		if color {
			result.WriteString(reset)
		}
		result.WriteString("\n")
	}
	return result.String(), nil
}

func batchConvert(inputDir, outputDir string, width, height, charsetIdx int, invert, color, verbose bool) error {
	os.MkdirAll(outputDir, 0755)
	exts := map[string]bool{".png": true, ".jpg": true, ".jpeg": true, ".bmp": true, ".tiff": true, ".webp": true}
	files := []string{}
	filepath.Walk(inputDir, func(path string, info fs.FileInfo, err error) error {
		if err != nil {
			return nil
		}
		if !info.IsDir() && exts[strings.ToLower(filepath.Ext(path))] {
			files = append(files, path)
		}
		return nil
	})
	total := len(files)
	for i, f := range files {
		outPath := filepath.Join(outputDir, strings.TrimSuffix(filepath.Base(f), filepath.Ext(f))+".txt")
		if verbose {
			fmt.Printf("[%d/%d] %s -> %s\n", i+1, total, f, outPath)
		}
		ascii, err := imageToAscii(f, width, height, charsetIdx, invert, color)
		if err != nil {
			continue
		}
		ioutil.WriteFile(outPath, []byte(ascii), 0644)
		if !verbose {
			pct := (i + 1) * 100 / total
			fmt.Printf("\r[%s] %d%% %d/%d", strings.Repeat("#", pct/2)+strings.Repeat(" ", 50-pct/2), pct, i+1, total)
		}
	}
	if !verbose {
		fmt.Println()
	}
	fmt.Println("✅ Batch conversion completed. Results in", outputDir)
	return nil
}

func main() {
	var (
		input     string
		output    string
		width     int
		height    int
		charset   string
		invert    bool
		noColor   bool
		batch     bool
		verbose   bool
	)
	flag.StringVar(&input, "input", "", "Input file or directory")
	flag.StringVar(&output, "o", "", "Output file or directory")
	flag.IntVar(&width, "w", 0, "Width")
	flag.IntVar(&height, "h", 0, "Height")
	flag.StringVar(&charset, "c", "default", "Charset: default, simple, detailed, block")
	flag.BoolVar(&invert, "i", false, "Invert")
	flag.BoolVar(&noColor, "n", false, "No color")
	flag.BoolVar(&batch, "b", false, "Batch")
	flag.BoolVar(&verbose, "v", false, "Verbose")
	flag.Parse()

	if flag.NArg() > 0 {
		input = flag.Arg(0)
	}
	if input == "" {
		fmt.Println("Укажите входной файл или папку.")
		flag.Usage()
		os.Exit(1)
	}

	charsetIdx := 0
	switch charset {
	case "simple":
		charsetIdx = 1
	case "detailed":
		charsetIdx = 2
	case "block":
		charsetIdx = 3
	}

	if batch {
		if output == "" {
			fmt.Println("Укажите выходную папку для batch.")
			os.Exit(1)
		}
		batchConvert(input, output, width, height, charsetIdx, invert, !noColor, verbose)
	} else {
		ascii, err := imageToAscii(input, width, height, charsetIdx, invert, !noColor)
		if err != nil {
			fmt.Println("Ошибка:", err)
			os.Exit(1)
		}
		if output != "" {
			ioutil.WriteFile(output, []byte(ascii), 0644)
			fmt.Println("✅ Result saved to", output)
		} else {
			fmt.Print(ascii)
		}
	}
}
