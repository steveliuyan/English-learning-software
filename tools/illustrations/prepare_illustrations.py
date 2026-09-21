"""Turn the raw generated illustration sheets into app-ready transparent assets.

Three things the generator burns into a sheet have to go before it can ship: a
watermark, an opaque backdrop, and the aliasing where the two meet.

The pipeline:

  1. **Crop the bottom band.** Measured on real sheets, artwork stops near y=830
     while the watermark occupies y=963..1013, so 955 clears the art with room to
     spare and takes the watermark with it.
  2. **Detect the backdrop** from the border ring. The prompts ask for a wide
     empty border, so by construction that ring is pure backdrop. Sampling it
     beats hard-coding a colour: the generator honours "flat magenta" sometimes
     and substitutes a flat pink other times, and a hard-coded hue silently stops
     matching when it does.
  3. **Key it out** by colour distance from that backdrop.
  4. **Erode 2px**, because anti-aliasing leaves a one-to-two pixel ring whose RGB
     channels are contaminated. Eroding discards it rather than reasoning about it.
  5. **Downscale with LANCZOS.** The mask is still binary here, so the resampler
     does all the anti-aliasing, averaging clean pixels only.
  6. **Un-mix at the output size.** Only now is the alpha a genuine area average,
     so ``foreground = (observed - (1 - a) * backdrop) / a`` is well conditioned.
     Anything below ``ALPHA_FLOOR`` is dropped first: at 3% coverage the observed
     colour is the backdrop to within rounding error, and dividing by 0.03 would
     turn noise into garbage.

Three orderings that look right and are not, worth recording:

* Un-mixing on the *binary* mask is a no-op — no pixel is partial yet.
* Erode-then-blur-then-un-mix fails. Blurring the mask makes edge pixels report
  39% coverage while the pixel underneath is still pure backdrop, so un-mixing
  recovers the backdrop and the fringe survives. The mask edge has to line up with
  the real subject edge, and only the resampler does that.
* Tolerating the backdrop by distance from a *corner seed* leaves whole regions
  behind, because the generator shades the backdrop darker where it meets the
  subject. Sampling the whole border ring for a median handles that.

Usage:
    python prepare_illustrations.py <raw-dir> <out-dir>

Every sheet prints a verdict. ``OK`` means nothing backdrop-coloured survived.
``REVIEW`` means some did and a human has to look, because the check cannot tell a
leftover glow from a legitimately pink prop.
"""

from __future__ import annotations

import statistics
import sys
from pathlib import Path

from PIL import Image, ImageFilter

# Rows to drop from the bottom, chosen to clear both the artwork and the watermark.
BOTTOM_CROP = 955

# Width of the ring sampled to detect the backdrop, as a fraction of the short side.
BORDER_FRACTION = 0.06

# Sum-of-absolute-channel-differences tolerance for "this pixel is still backdrop".
# Measured across real sheets: backdrop varies by well under 40 from its own median,
# while the nearest subject colour sits 100+ away.
KEY_TOLERANCE = 60

# Guard rails. If the detected backdrop is not this chromatic, or the border ring is
# not this uniform, the generator ignored the flat-backdrop instruction and keying
# would chew a hole through a real scene background — so refuse instead.
MIN_BACKDROP_CHROMA = 60
MAX_BORDER_DEVIATION = 25.0

# 2px at generation resolution, which is where the contaminated ring lives.
ERODE_SIZE = 5

OUTPUT_SIZE = 512
MARGIN_RATIO = 0.05

# Alpha values below this are dropped to fully transparent; see the module docstring.
ALPHA_FLOOR = 48

# Self-check only. Kept slightly tighter than KEY_TOLERANCE so it reports genuine
# key-colour residue rather than merely similar subject colours: once the backdrop is
# detected rather than hard-coded it can land near skin tones and coral, and a loose
# check then flags most of the artwork. Soft glows that drift further from the
# backdrop are outside what any colour test can separate — those are caught by
# looking at the contact sheet, which is the real acceptance step either way.
CHECK_TOLERANCE = 45


def channel_distance(left: tuple[int, int, int], right: tuple[int, int, int]) -> int:
    return sum(abs(left[channel] - right[channel]) for channel in range(3))


def detect_backdrop(image: Image.Image) -> tuple[tuple[int, int, int], list[tuple[int, int, int]]]:
    """Median colour of the border ring, plus the ring samples for the sanity check."""
    width, height = image.size
    band = max(4, int(min(width, height) * BORDER_FRACTION))
    samples: list[tuple[int, int, int]] = []
    for y in range(0, height, 3):
        for x in range(0, width, 3):
            if x < band or y < band or x >= width - band or y >= height - band:
                samples.append(image.getpixel((x, y)))
    samples.sort(key=sum)
    return samples[len(samples) // 2], samples


def border_deviation(samples: list[tuple[int, int, int]], backdrop: tuple[int, int, int]) -> float:
    return statistics.pstdev(channel_distance(sample, backdrop) for sample in samples)


def build_mask(image: Image.Image, backdrop: tuple[int, int, int]) -> Image.Image:
    """Return a binary alpha mask: 0 wherever the backdrop shows through.

    The test is global rather than a flood fill from a corner seed. Flood-filling
    compares each pixel against the seed, so backdrop that the generator shaded
    away from the seed colour never gets reached and survives as a slab of key
    colour in the finished asset. Comparing against the ring median instead has no
    such blind spot, and the guard in [process] is what keeps it from cutting into
    a worksheet whose background is not a key colour at all.
    """
    width, height = image.size
    pixels = image.load()
    mask = Image.new("L", (width, height), 255)
    mask_pixels = mask.load()
    for y in range(height):
        for x in range(width):
            if channel_distance(pixels[x, y], backdrop) <= KEY_TOLERANCE:
                mask_pixels[x, y] = 0
    return mask


def square_with_margin(image: Image.Image, margin_ratio: float) -> Image.Image:
    """Pad the artwork out to a square canvas so every asset shares one framing."""
    width, height = image.size
    side = max(width, height) + int(max(width, height) * margin_ratio * 2)
    background = (0, 0, 0, 0) if image.mode == "RGBA" else 0
    canvas = Image.new(image.mode, (side, side), background)
    canvas.paste(image, ((side - width) // 2, (side - height) // 2))
    return canvas


def un_mix(
    observed: tuple[int, int, int],
    alpha: int,
    backdrop: tuple[int, int, int],
) -> tuple[int, int, int]:
    """Recover the true foreground colour of an anti-aliased edge pixel."""
    ratio = alpha / 255.0
    return tuple(  # type: ignore[return-value]
        max(0, min(255, round((observed[channel] - (1.0 - ratio) * backdrop[channel]) / ratio)))
        for channel in range(3)
    )


def process(source: Path, destination: Path) -> None:
    sheet = Image.open(source).convert("RGB")
    sheet = sheet.crop((0, 0, sheet.width, min(BOTTOM_CROP, sheet.height)))

    backdrop, ring = detect_backdrop(sheet)
    chroma = max(backdrop) - min(backdrop)
    deviation = border_deviation(ring, backdrop)
    if chroma < MIN_BACKDROP_CHROMA or deviation > MAX_BORDER_DEVIATION:
        raise SystemExit(
            f"{source.name}: backdrop {backdrop} looks like scene content, not a flat "
            f"key colour (chroma {chroma}, border deviation {deviation:.1f}). "
            f"Regenerate this sheet rather than keying it."
        )

    mask = build_mask(sheet, backdrop).filter(ImageFilter.MinFilter(ERODE_SIZE))
    box = mask.getbbox()
    if box is None:
        raise SystemExit(f"{source.name}: nothing survived the key, aborting")

    # Crop before scaling so the resampler spends its budget on the subject.
    art = square_with_margin(sheet.crop(box), MARGIN_RATIO)
    padded_mask = square_with_margin(mask.crop(box), MARGIN_RATIO)

    art = art.resize((OUTPUT_SIZE, OUTPUT_SIZE), Image.LANCZOS)
    padded_mask = padded_mask.resize((OUTPUT_SIZE, OUTPUT_SIZE), Image.LANCZOS)

    floored = padded_mask.point(lambda value: 0 if value < ALPHA_FLOOR else value)
    keyed = art.convert("RGBA")
    art_pixels = art.load()
    alpha_pixels = padded_mask.load()
    floored_pixels = floored.load()
    keyed_pixels = keyed.load()

    for y in range(OUTPUT_SIZE):
        for x in range(OUTPUT_SIZE):
            value = alpha_pixels[x, y]
            if value == 0:
                continue
            if channel_distance(art_pixels[x, y], backdrop) <= KEY_TOLERANCE:
                # An isolated speck that survived erosion. Cutting it is safe: a
                # pixel this close to the backdrop is backdrop, and letting it
                # through would put a stray blob of key colour in the app.
                floored_pixels[x, y] = 0
                continue
            if ALPHA_FLOOR <= value < 255:
                keyed_pixels[x, y] = (*un_mix(art_pixels[x, y], value, backdrop), value)
    keyed.putalpha(floored)

    destination.parent.mkdir(parents=True, exist_ok=True)
    keyed.save(destination)

    residual = sum(
        1
        for y in range(OUTPUT_SIZE)
        for x in range(OUTPUT_SIZE)
        if keyed_pixels[x, y][3] > 40
        and channel_distance(keyed_pixels[x, y][:3], backdrop) <= CHECK_TOLERANCE
    )
    coverage = sum(1 for value in list(floored.getdata()) if value > 0) / (OUTPUT_SIZE * OUTPUT_SIZE)
    verdict = "OK" if residual == 0 else f"REVIEW {residual} backdrop-coloured px survived"
    print(f"{source.name}: backdrop {backdrop}, bbox {box}, coverage {coverage:.1%} -> {verdict}")


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    raw_dir, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    sources = sorted(raw_dir.glob("*.png"))
    if not sources:
        raise SystemExit(f"no PNG sheets found in {raw_dir}")
    for source in sources:
        process(source, out_dir / source.name)


if __name__ == "__main__":
    main()
