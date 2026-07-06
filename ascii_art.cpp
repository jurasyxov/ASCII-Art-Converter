// ascii_art.cpp
#include <opencv2/opencv.hpp>
#include <iostream>
#include <vector>
#include <string>
#include <filesystem>
#include <fstream>

using namespace std;
using namespace cv;
namespace fs = std::filesystem;

string RESET = "\033[0m";
string BOLD = "\033[1m";

string rgbToAnsi(int r, int g, int b) {
    return "\033[38;2;" + to_string(r) + ";" + to_string(g) + ";" + to_string(b) + "m";
}

vector<string> CHARSETS = {
    { ' ', '.', ':', '-', '=', '+', '*', '#', '%', '@' },         // default
    { ' ', '.', 'o', 'O', '0', '@' },                             // simple
    { ' ', '`', '.', ',', ':', ';', '+', '*', '?', '%', 'S', '#', '@' }, // detailed
    { ' ', '░', '▒', '▓', '█' }                                   // block
};

int getTerminalWidth() {
    struct winsize w;
    ioctl(STDOUT_FILENO, TIOCGWINSZ, &w);
    return w.ws_col;
}

string imageToAscii(const string& path, int width, int height,
                    int charsetIdx, bool invert, bool color) {
    Mat img = imread(path, IMREAD_COLOR);
    if (img.empty()) return "";
    int origW = img.cols, origH = img.rows;

    if (width == 0 && height == 0) {
        width = getTerminalWidth() / 2;
    }
    if (width == 0) width = origW * height / origH;
    if (height == 0) height = origH * width / origW;

    resize(img, img, Size(width, height), 0, 0, INTER_LANCZOS4);
    vector<char> chars = CHARSETS[charsetIdx];
    string result;

    for (int y = 0; y < img.rows; ++y) {
        for (int x = 0; x < img.cols; ++x) {
            Vec3b pixel = img.at<Vec3b>(y, x);
            int gray = (int)(0.299 * pixel[2] + 0.587 * pixel[1] + 0.114 * pixel[0]);
            if (invert) gray = 255 - gray;
            int idx = gray * (chars.size() - 1) / 255;
            if (color) {
                result += rgbToAnsi(pixel[2], pixel[1], pixel[0]) + chars[idx];
            } else {
                result += chars[idx];
            }
        }
        if (color) result += RESET;
        result += '\n';
    }
    return result;
}

void batchConvert(const string& inputDir, const string& outputDir,
                  int width, int height, int charsetIdx, bool invert, bool color, bool verbose) {
    fs::create_directories(outputDir);
    vector<string> exts = {".png", ".jpg", ".jpeg", ".bmp", ".tiff", ".webp"};
    vector<string> files;
    for (const auto& entry : fs::directory_iterator(inputDir)) {
        if (entry.is_regular_file()) {
            string ext = entry.path().extension().string();
            transform(ext.begin(), ext.end(), ext.begin(), ::tolower);
            if (find(exts.begin(), exts.end(), ext) != exts.end()) {
                files.push_back(entry.path().string());
            }
        }
    }
    int total = files.size();
    for (int i = 0; i < total; ++i) {
        string outPath = outputDir + "/" + fs::path(files[i]).stem().string() + ".txt";
        if (verbose) {
            cout << "[" << i+1 << "/" << total << "] " << files[i] << " -> " << outPath << endl;
        }
        string ascii = imageToAscii(files[i], width, height, charsetIdx, invert, color);
        ofstream f(outPath);
        if (f) f << ascii;
        if (!verbose) {
            int pct = (i+1) * 100 / total;
            cout << "\r[" << string(pct/2, '#') << string(50-pct/2, ' ') << "] " << pct << "% " << i+1 << "/" << total << flush;
        }
    }
    if (!verbose) cout << endl;
}

int main(int argc, char* argv[]) {
    string input, output;
    int width = 0, height = 0;
    int charsetIdx = 0;
    bool invert = false, noColor = false, batch = false, verbose = false;

    for (int i = 1; i < argc; ++i) {
        string arg = argv[i];
        if (arg == "-o" && i+1 < argc) output = argv[++i];
        else if (arg == "-w" && i+1 < argc) width = stoi(argv[++i]);
        else if (arg == "-c" && i+1 < argc) {
            string cs = argv[++i];
            if (cs == "default") charsetIdx = 0;
            else if (cs == "simple") charsetIdx = 1;
            else if (cs == "detailed") charsetIdx = 2;
            else if (cs == "block") charsetIdx = 3;
        } else if (arg == "-i") invert = true;
        else if (arg == "-n") noColor = true;
        else if (arg == "-b") batch = true;
        else if (arg == "-v") verbose = true;
        else if (arg == "-h" || arg == "--help") {
            cout << "Usage: ascii_art <input> [options]\n"
                 << "  -o <file>   Output file/dir\n"
                 << "  -w <N>      Width\n"
                 << "  -c <set>    Charset: default, simple, detailed, block\n"
                 << "  -i          Invert\n"
                 << "  -n          No color\n"
                 << "  -b          Batch\n"
                 << "  -v          Verbose\n";
            return 0;
        } else if (input.empty()) input = arg;
    }
    if (input.empty()) { cerr << "Укажите входной файл или папку." << endl; return 1; }

    if (batch) {
        if (output.empty()) { cerr << "Укажите выходную папку для batch." << endl; return 1; }
        batchConvert(input, output, width, height, charsetIdx, invert, !noColor, verbose);
        cout << "✅ Batch conversion completed. Results in " << output << endl;
    } else {
        string ascii = imageToAscii(input, width, height, charsetIdx, invert, !noColor);
        if (!output.empty()) {
            ofstream f(output);
            if (f) f << ascii;
            cout << "✅ Result saved to " << output << endl;
        } else {
            cout << ascii;
        }
    }
    return 0;
}
