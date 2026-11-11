#!/usr/bin/env python3
"""Builds assets/preview3d from CC0 originals kept outside the repo.

Usage: python3 tools/preview3d_assets.py <assets-src/preview3d> [--sky 2k|1k]
Needs Pillow and numpy. Sources and licences: <assets-src>/preview3d/SOURCES.md.
"""
import argparse
import io
import shutil
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

MODELS = ["sedan", "suv", "van", "hatchback-sports", "taxi", "truck", "delivery"]
# colormap.png is 8 columns x 4 rows of colour cells; (col, row), row 0 = top.
CELL_W, CELL_H = 64, 128
BODY = [(3, 1), (4, 1), (6, 1), (7, 1)]  # paint; the app tints it per car with baseColorFactor
GLASS, TRIM = (5, 2), (3, 2)
# app name: (ambientCG id, size px, brightness gain, tint or None)
TEXTURES = {
    "asphalt": ("Asphalt023S", 1024, 0.70, None),
    "concrete": ("Concrete034", 512, 1.00, None),
    "grass": ("Grass004", 512, 0.90, None),
    "paving": ("PavingStones126A", 512, 1.00, None),
    "epoxy": ("Concrete034", 512, 1.00, (0.33, 0.60, 0.43)),
}


def recolor(src: Path, dst: Path) -> None:
    """Realistic paint, dark glass and black trim; keeps each cell's light-to-dark shading."""
    im = np.asarray(Image.open(src).convert("RGBA")).astype(np.float32)

    def fill(cell, rgb):
        col, row = cell
        ys, xs = slice(row * CELL_H, (row + 1) * CELL_H), slice(col * CELL_W, (col + 1) * CELL_W)
        lum = im[ys, xs, :3] @ np.array([0.2126, 0.7152, 0.0722], np.float32)
        shade = (lum / max(float(lum.max()), 1.0))[..., None]
        im[ys, xs, :3] = shade * np.array(rgb, np.float32)

    for cell in BODY:
        fill(cell, (236, 238, 240))
    fill(GLASS, (34, 42, 52))
    fill(TRIM, (40, 42, 46))
    Image.fromarray(im.clip(0, 255).astype(np.uint8)).save(dst, optimize=True)


def texture(src: Path, out: Path, ident: str, size: int, gain: float, tint) -> None:
    with zipfile.ZipFile(src / "ambientcg" / f"{ident}.zip") as z:
        data = z.read(f"{ident}_1K-JPG_Color.jpg")
    im = Image.open(io.BytesIO(data)).convert("RGB").resize((size, size), Image.LANCZOS)
    a = np.asarray(im).astype(np.float32) * gain
    if tint is not None:
        a = a.mean(axis=2, keepdims=True) * np.array(tint, np.float32)
    Image.fromarray(a.clip(0, 255).astype(np.uint8)).save(out, quality=85, optimize=True)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("src", type=Path)
    ap.add_argument("--sky", choices=["2k", "1k"], default="2k")
    args = ap.parse_args()
    root = Path(__file__).resolve().parent.parent / "assets" / "preview3d"
    (root / "models" / "Textures").mkdir(parents=True, exist_ok=True)
    (root / "textures").mkdir(parents=True, exist_ok=True)
    glb = args.src / "kenney-car-kit" / "glb"
    for m in MODELS:
        shutil.copyfile(glb / f"{m}.glb", root / "models" / f"{m}.glb")
    recolor(glb / "Textures" / "colormap.png", root / "models" / "Textures" / "colormap.png")
    for name, (ident, size, gain, tint) in TEXTURES.items():
        texture(args.src, root / "textures" / f"{name}.jpg", ident, size, gain, tint)
    shutil.copyfile(args.src / "polyhaven" / f"kloofendal_48d_partly_cloudy_puresky_{args.sky}.hdr", root / "sky.hdr")
    for p in sorted(root.rglob("*")):
        if p.is_file():
            print(f"{p.stat().st_size:>9}  {p.relative_to(root)}")


if __name__ == "__main__":
    main()
