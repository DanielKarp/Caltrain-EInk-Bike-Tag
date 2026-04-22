"""
Generate 264×176 pure black-and-white BMP images from a YAML station list.

YAML schema (stations.yaml):
  stations:
    - name: "San Francisco"       # required – text rendered in the image
                                        # use \n for manual line breaks
      display_name: "San Francisco"     # optional – used in filename; defaults to
                                        # name (newlines stripped)
      color: red                        # optional – one of: red, orange, yellow,
                                        #   green, blue, indigo, violet

File names are written as:
  <zero-padded index>_<color initial if set>_<display name>.bmp
  e.g.  07_R_Millbrae.bmp  or  00_SanFrancisco.bmp
"""

from PIL import Image, ImageDraw, ImageFont
import os
import yaml

# ── Configuration ────────────────────────────────────────────────────────────
IMG_W, IMG_H = 264, 176
PADDING = 12
OUTPUT_DIR = "output"
STATIONS_FILE = "stations.yaml"

# Recognised ROYGBIV color names → their single-character file prefix
COLOR_INITIALS: dict[str, str] = {
    "Red":    "R",
    "Orange": "O",
    "Yellow": "Y",
    "Green":  "G",
    "Blue":   "B",
    "Indigo": "I",
    "Violet": "V",
}
# ─────────────────────────────────────────────────────────────────────────────


def load_stations(path: str) -> list[dict]:
    """Load and validate station entries from a YAML file."""
    with open(path, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)

    stations = data.get("stations", [])
    if not stations:
        raise ValueError(f"No stations found under 'stations:' key in {path}")

    for i, entry in enumerate(stations):
        if "name" not in entry:
            raise ValueError(f"Station at index {i} is missing required 'name'")
        color = entry.get("color")
        if color is not None and color.capitalize() not in COLOR_INITIALS:
            raise ValueError(
                f"Station '{entry['name']}' has unknown color '{color}'. "
                f"Must be one of: {', '.join(COLOR_INITIALS)}"
            )

    return stations


def build_filename(index: int, station: dict) -> str:
    """
    Construct the output filename for a station entry.

    Format: <index>_<color_initial>_<display_name>.bmp
    """
    display_name = station.get("display_name") or station["name"].replace("\n", "")
    color = station.get("color")
    color_prefix = COLOR_INITIALS[color.capitalize()] + '_' if color else ""
    return f"{index:02d}_{color_prefix}{display_name}.bmp"


def find_font(preferred: list[str] | None = None) -> str | None:
    """Return the path of the first available TrueType font, or None."""
    candidates = (preferred or []) + [
        # Linux
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
        "/usr/share/fonts/truetype/freefont/FreeSansBold.ttf",
        # macOS
        "/System/Library/Fonts/Helvetica.ttc",
        "/Library/Fonts/Arial Bold.ttf",
        # Windows
        "C:/Windows/Fonts/arialbd.ttf",
        "C:/Windows/Fonts/arial.ttf",
    ]
    for path in candidates:
        if os.path.isfile(path):
            return path
    return None


def fit_font(
    draw: ImageDraw.ImageDraw,
    text: str,
    max_w: int,
    max_h: int,
    font_path: str | None,
) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    """
    Binary-search for the largest font size whose rendered text fits within
    (max_w × max_h).  Falls back to the default bitmap font if no TTF found.
    """
    if font_path is None:
        return ImageFont.load_default()

    lo, hi = 4, max(max_w, max_h) * 2
    best = ImageFont.truetype(font_path, lo)
    while lo <= hi:
        mid = (lo + hi) // 2
        font = ImageFont.truetype(font_path, mid)
        bbox = draw.multiline_textbbox((0, 0), text, font=font, align="center")
        tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
        if tw <= max_w and th <= max_h:
            best = font
            lo = mid + 1
        else:
            hi = mid - 1
    return best


def make_image(station: dict, index: int) -> None:
    name: str = station["name"]
    # Use explicit newlines if present; otherwise split on spaces for auto-wrap
    render_text = name if "\n" in name else name.replace(" ", "\n")

    img = Image.new("RGB", (IMG_W, IMG_H), color=(255, 255, 255))
    draw = ImageDraw.Draw(img)

    font_path = find_font()
    usable_w = IMG_W - 2 * PADDING
    usable_h = IMG_H - 2 * PADDING
    font = fit_font(draw, render_text, usable_w, usable_h, font_path)

    bbox = draw.textbbox((0, 0), render_text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x = (IMG_W - tw) // 2 - bbox[0]
    y = (IMG_H - th) // 2 - bbox[1]
    draw.multiline_text((x, y), render_text, font=font, fill=(0, 0, 0), align="center")

    # Convert to pure 1-bit (no dithering), then back to RGB for BMP save
    img = img.convert("L")
    img = img.point(lambda px: 255 if px > 128 else 0)
    img = img.convert("1")
    img = img.convert("RGB", dither=None)

    filename = os.path.join(OUTPUT_DIR, build_filename(index, station))
    img.save(filename, format="BMP")
    print(f"  saved → {filename}")


def main() -> None:
    stations = load_stations(STATIONS_FILE)
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    print(f"Generating {len(stations)} image(s) from '{STATIONS_FILE}' …")
    for i, station in enumerate(stations):
        make_image(station, i)
    print("Done.")


if __name__ == "__main__":
    main()
