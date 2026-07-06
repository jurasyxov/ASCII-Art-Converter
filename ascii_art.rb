#!/usr/bin/env ruby
# ascii_art.rb
# encoding: UTF-8

require 'rmagick'
include Magick
require 'optparse'
require 'fileutils'

COLORS = {
  reset: "\e[0m",
  bold: "\e[1m"
}

def rgb_to_ansi(r, g, b)
  "\e[38;2;#{r};#{g};#{b}m"
end

CHARSETS = {
  'default' => [' ', '.', ':', '-', '=', '+', '*', '#', '%', '@'],
  'simple' => [' ', '.', 'o', 'O', '0', '@'],
  'detailed' => [' ', '`', '.', ',', ':', ';', '+', '*', '?', '%', 'S', '#', '@'],
  'block' => [' ', '░', '▒', '▓', '█']
}

def terminal_width
  IO.console.winsize[1] rescue 80
end

def image_to_ascii(path, width, height, charset_name, invert, color)
  img = Image.read(path).first
  orig_w, orig_h = img.columns, img.rows

  if width == 0 && height == 0
    width = terminal_width / 2
  end
  if width == 0
    width = orig_w * height / orig_h
  end
  if height == 0
    height = orig_h * width / orig_w
  end
  width = 1 if width < 1
  height = 1 if height < 1

  img = img.resize(width, height)
  chars = CHARSETS[charset_name] || CHARSETS['default']
  result = ''

  (0...height).each do |y|
    (0...width).each do |x|
      pixel = img.pixel_color(x, y)
      r = pixel.red / 257
      g = pixel.green / 257
      b = pixel.blue / 257
      gray = (0.299 * r + 0.587 * g + 0.114 * b).to_i
      gray = 255 - gray if invert
      idx = gray * (chars.size - 1) / 255
      result += rgb_to_ansi(r, g, b) + chars[idx] if color
      result += chars[idx] unless color
    end
    result += COLORS[:reset] + "\n" if color
    result += "\n" unless color
  end
  result
end

def batch_convert(input_dir, output_dir, opts)
  FileUtils.mkdir_p(output_dir)
  exts = %w[.png .jpg .jpeg .bmp .tiff .webp]
  files = Dir.entries(input_dir)
             .select { |f| exts.include?(File.extname(f).downcase) }
             .map { |f| File.join(input_dir, f) }
  total = files.size
  files.each_with_index do |f, i|
    out_path = File.join(output_dir, File.basename(f, '.*') + '.txt')
    if opts[:verbose]
      puts "[#{i+1}/#{total}] #{f} -> #{out_path}"
    end
    ascii = image_to_ascii(f, opts[:width], opts[:height], opts[:charset], opts[:invert], opts[:color])
    File.write(out_path, ascii)
    unless opts[:verbose]
      pct = (i+1) * 100 / total
      print "\r[#{'█' * (pct/2)}#{'░' * (50 - pct/2)}] #{pct}% #{i+1}/#{total}"
    end
  end
  puts unless opts[:verbose]
  puts "✅ Batch conversion completed. Results in #{output_dir}"
end

def main
  options = { width: 0, height: 0, charset: 'default', invert: false, color: true, batch: false, verbose: false }
  input = output = nil

  OptionParser.new do |opts|
    opts.banner = "Usage: ascii_art.rb <input> [options]"
    opts.on('-o FILE', 'Output file/dir') { |v| output = v }
    opts.on('-w N', Integer, 'Width') { |v| options[:width] = v }
    opts.on('-h N', Integer, 'Height') { |v| options[:height] = v }
    opts.on('-c SET', 'Charset: default, simple, detailed, block') { |v| options[:charset] = v }
    opts.on('-i', 'Invert') { options[:invert] = true }
    opts.on('-n', 'No color') { options[:color] = false }
    opts.on('-b', 'Batch') { options[:batch] = true }
    opts.on('-v', 'Verbose') { options[:verbose] = true }
    opts.on('-h', 'Help') { puts opts; exit }
  end.parse!

  input = ARGV[0] if ARGV[0]
  unless input
    puts "Укажите входной файл или папку."
    exit 1
  end

  if options[:batch]
    unless output
      puts "Укажите выходную папку для batch."
      exit 1
    end
    batch_convert(input, output, options)
  else
    ascii = image_to_ascii(input, options[:width], options[:height], options[:charset], options[:invert], options[:color])
    if output
      File.write(output, ascii)
      puts "✅ Result saved to #{output}"
    else
      print ascii
    end
  end
end

main if __FILE__ == $0
