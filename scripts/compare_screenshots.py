#!/usr/bin/env python3
"""Compara dos carpetas de capturas (referencia y nueva) y avisa de los cambios visuales.

Uso:
  HALO_SHOTS=/tmp/nuevas ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTour*'
  python3 scripts/compare_screenshots.py referencia/ /tmp/nuevas/ [--umbral 0.5]

Para cada captura indica el % de píxeles distintos y guarda una imagen *-diff.png
con las zonas cambiadas en rojo. Devuelve código 1 si alguna supera el umbral.
"""
import sys
from pathlib import Path
from PIL import Image, ImageChops

def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    umbral = 0.5
    if "--umbral" in sys.argv:
        umbral = float(sys.argv[sys.argv.index("--umbral") + 1])
        args = [a for a in args if a != str(sys.argv[sys.argv.index("--umbral") + 1])]
    if len(args) != 2:
        print(__doc__); return 2
    ref, new = Path(args[0]), Path(args[1])
    fallos = 0
    for r in sorted(ref.glob("*.png")):
        if r.name.endswith("-diff.png"):
            continue
        n = new / r.name
        if not n.exists():
            print(f"FALTA   {r.name}"); fallos += 1; continue
        a, b = Image.open(r).convert("RGB"), Image.open(n).convert("RGB")
        if a.size != b.size:
            print(f"TAMAÑO  {r.name}: {a.size} → {b.size}"); fallos += 1; continue
        diff = ImageChops.difference(a, b).convert("L").point(lambda v: 255 if v > 24 else 0)
        pct = 100.0 * sum(1 for v in (diff.get_flattened_data() if hasattr(diff, "get_flattened_data") else diff.getdata()) if v) / (a.width * a.height)
        estado = "CAMBIO " if pct > umbral else "ok     "
        print(f"{estado} {r.name}: {pct:.2f} %")
        if pct > umbral:
            fallos += 1
            marca = Image.new("RGB", a.size, (255, 40, 40))
            Image.composite(marca, b, diff).save(new / r.name.replace(".png", "-diff.png"))
    for n in sorted(new.glob("*.png")):
        if not n.name.endswith("-diff.png") and not (ref / n.name).exists():
            print(f"NUEVA   {n.name}")
    return 1 if fallos else 0

if __name__ == "__main__":
    sys.exit(main())
