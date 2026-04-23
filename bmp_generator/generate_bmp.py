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
      qr: "https://example.com"         # optional – URL/data to encode as a QR code.
                                        #   When present, a QR code (v6, EC-M) is
                                        #   placed on the left and the name text is
                                        #   placed on the right.

File names are written as:
  <zero-padded index>_<color initial if set>_<display name>.bmp
  e.g.  07_R_Millbrae.bmp  or  00_SanFrancisco.bmp
"""

import os

import qrcode
import yaml
from PIL import Image, ImageDraw, ImageFont
from qrcode.constants import ERROR_CORRECT_M

# ── Configuration ────────────────────────────────────────────────────────────
IMG_W, IMG_H = 264, 176
PADDING = 12
OUTPUT_DIR = "output"
STATIONS_FILE = "stations.yaml"

# QR layout constants (Version 6, 3 px/module, 4-module quiet zone each side)
# Total pixel size: (41 data modules + 8 quiet-zone modules) × 3 px = 147 px
QR_VERSION = 6
QR_BOX_SIZE = 3  # px per module
QR_BORDER = 4  # quiet-zone modules (standard minimum)
QR_SIZE = (17 + 4 * QR_VERSION + QR_BORDER * 2) * QR_BOX_SIZE  # = 147 px
QR_COL_GAP = 8  # gap between QR image and text column

# Recognised ROYGBIV color names → their single-character file prefix
COLOR_INITIALS: dict[str, str] = {
    "Red": "R",
    "Orange": "O",
    "Yellow": "Y",
    "Green": "G",
    "Blue": "B",
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
    color_prefix = COLOR_INITIALS[color.capitalize()] + "_" if color else ""
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


def _to_bw(img: Image.Image) -> Image.Image:
    """Convert an RGB image to pure 1-bit black-and-white (no dithering)."""
    img = img.convert("L")
    img = img.point(lambda px: 255 if px > 128 else 0)
    img = img.convert("1")
    return img.convert("RGB", dither=None)


def make_image(station: dict, index: int) -> None:
    """Render a text-only BMP (original behaviour)."""
    name: str = station["name"]
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

    img = _to_bw(img)
    filename = os.path.join(OUTPUT_DIR, build_filename(index, station))
    img.save(filename, format="BMP")
    print(f"  saved → {filename}")


def make_qr_image(station: dict, index: int) -> None:
    """
    Render a BMP with a QR code on the left and the station name on the right.

    Layout (all values in pixels):
      ┌──────────────────────────────────────────┐
      │  pad  │  QR (147×147)  │ gap │  text col │  pad  │
      └──────────────────────────────────────────┘

    QR spec: Version 6, EC-M, 3 px/module, 4-module quiet zone → 147×147 px
    Text column width: 264 − 12 − 147 − 8 − 12 = 85 px
    """
    name: str = station["name"]
    url: str = station["qr"]
    render_text = name if "\n" in name else name.replace(" ", "\n")

    # ── Build QR code image ──────────────────────────────────────────────────
    # Capacity for Version 6 / EC-M per encoding mode.
    # The library picks the most efficient mode automatically after add_data().
    _QR_CAPACITY: dict[int, tuple[int, str, str]] = {
        # mode constant → (max chars/bytes, unit label, mode name)
        qrcode.util.MODE_NUMBER: (255, "digits", "numeric"),
        qrcode.util.MODE_ALPHA_NUM: (154, "characters", "alphanumeric"),
        qrcode.util.MODE_8BIT_BYTE: (106, "bytes", "byte"),
    }

    qr = qrcode.QRCode(
        version=QR_VERSION,
        error_correction=ERROR_CORRECT_M,
        box_size=QR_BOX_SIZE,
        border=QR_BORDER,
    )
    qr.add_data(url)

    # Inspect mode chosen by the library (set during add_data, before make)
    mode_const = qr.data_list[0].mode if qr.data_list else qrcode.util.MODE_8BIT_BYTE
    cap, unit, mode_name = _QR_CAPACITY.get(mode_const, (106, "bytes", "byte"))
    data_len = (
        len(url.encode("utf-8"))
        if mode_const == qrcode.util.MODE_8BIT_BYTE
        else len(url)
    )

    try:
        qr.make(fit=False)  # force exact version; raises if data overflows v6/EC-M
    except qrcode.exceptions.DataOverflowError:
        print(
            f"  SKIP  '{station['name']}' — QR data too long for "
            f"v{QR_VERSION}/EC-M ({data_len} {unit} in {mode_name} mode, "
            f"max {cap}).\n"
            f"          Mode limits for v{QR_VERSION}/EC-M: "
            f"numeric=255 digits · alphanumeric=154 chars · byte=106 bytes\n"
            f"          Data: {url!r}"
        )
        return
    qr_img = qr.make_image(fill_color="black", back_color="white").convert("RGB")

    actual_qr_size = qr_img.size[0]  # square; should equal QR_SIZE (147)

    # ── Compose canvas ───────────────────────────────────────────────────────
    img = Image.new("RGB", (IMG_W, IMG_H), color=(255, 255, 255))
    draw = ImageDraw.Draw(img)

    # Paste QR, centred vertically
    qr_x = PADDING
    qr_y = (IMG_H - actual_qr_size) // 2
    img.paste(qr_img, (qr_x, qr_y))

    # ── Fit text into the remaining column ──────────────────────────────────
    text_x = PADDING + actual_qr_size + QR_COL_GAP
    text_w = IMG_W - text_x - PADDING  # 264 − 167 − 12 = 85 px
    text_h = IMG_H - 2 * PADDING  # 152 px

    font_path = find_font()
    font = fit_font(draw, render_text, text_w, text_h, font_path)

    bbox = draw.multiline_textbbox((0, 0), render_text, font=font, align="center")
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    tx = text_x + (text_w - tw) // 2 - bbox[0]
    ty = (IMG_H - th) // 2 - bbox[1]
    draw.multiline_text(
        (tx, ty), render_text, font=font, fill=(0, 0, 0), align="center"
    )

    img = _to_bw(img)
    filename = os.path.join(OUTPUT_DIR, build_filename(index, station))
    img.save(filename, format="BMP")
    print(f"  saved → {filename} (QR Code)")


def main() -> None:
    stations = load_stations(STATIONS_FILE)
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    print(f"Generating {len(stations)} image(s) from '{STATIONS_FILE}' …")
    for i, station in enumerate(stations):
        if station.get("qr"):
            make_qr_image(station, i)
        else:
            make_image(station, i)
    print("Done.")


if __name__ == "__main__":
    main()
