# BMP Generator for E-Ink Bike Tag

A Python script that generates 264×176 pixel black-and-white BMP images for the E-Ink Bike Tag Android app. It reads station names and attributes from a YAML file and creates appropriately sized and named BMP files for display on the e-ink tag.

## Features

- Generates pure black-and-white BMP images optimized for e-ink displays
- Automatic font sizing to fit text within the display bounds
- Support for custom colors (ROYGBIV) with filename prefixes
- Manual line breaks and automatic text wrapping
- Cross-platform font detection (Windows, macOS, Linux)

## Requirements

- Python 3.13 or higher
- Dependencies: Pillow, PyYAML

## Installation

1. Ensure Python 3.13+ is installed
2. Install uv if not already installed:

   ```bash
   pip install uv
   ```

3. Install dependencies:

   ```bash
   uv sync
   ```

## Usage

1. Edit `stations.yaml` to define your station list
2. Run the generator:

   ```bash
   python generate_bmp.py
   ```

3. Generated BMPs will be saved in the `output/` directory

## Configuration

### stations.yaml Format

The script reads station data from `stations.yaml` with the following schema:

```yaml
stations:
  - name: "Station Name"          # Required: Text to render (automatically splits on words, use \n for line breaks elsewhere)
    display_name: "DisplayName"   # Optional: What is printed in the filename and what shows in the app (defaults to name without newlines)
    color: Red                   # Optional: Color for highlight (Red, Orange, Yellow, Green, Blue, Indigo, Violet). When not provided, defaults to transparent.
```

### Examples

```yaml
stations:
  - name: "San Francisco"
    color: Red
  - name: "22nd St"
    color: Red
  - name: "Bayshore"
  - name: "South SF"
    color: Red
  - name: "San Bruno"
  - name: "Millbrae"
    color: Red
  - name: "Broadway"
    color: Violet
  - name: "Burlin\ngame"          # Manual line break
    display_name: "Burlingame"   # Custom filename
```

## Output

Files are named as: `<index>_<color_initial>_<display_name>.bmp`

- Index: Zero-padded two-digit number (00, 01, etc.)
- Color initial: Single letter for ROYGBIV colors (R, O, Y, G, B, I, V)
- Display name: Station name with spaces/newlines removed

Examples:

- `07_R_Millbrae.bmp`
- `00_SanFrancisco.bmp`
- `15_B_MenloPark.bmp`

## Technical Details

- Image size: 264×176 pixels (matches Waveshare 2.7" e-ink display)
- Color depth: 1-bit black and white
- Font: Automatically selects best available system font
- Text fitting: Binary search for optimal font size
- Padding: 12 pixels on all sides

## Integration with Android App

The generated BMPs are designed to work with the E-Ink Bike Tag Android app. Place the BMP files in `app/src/main/assets/images/` and the app will automatically parse the filenames for display names and colors.