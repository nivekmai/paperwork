"""Independently rasterize the PDF fixtures produced by PdfWorkflowTest.
Run with a Python environment containing PyMuPDF: python tools/check-export.py
"""
from pathlib import Path
import pymupdf

root = Path(__file__).resolve().parents[1] / "app/build/test-artifacts"
with pymupdf.open(root / "rotated-export.pdf") as doc:
    assert len(doc) == 4
    for index, page in enumerate(doc):
        pix = page.get_pixmap()
        points = []
        for y in range(pix.height):
            for x in range(pix.width):
                red, green, blue = pix.pixel(x, y)[:3]
                if red > 200 and green < 40 and blue < 40:
                    points.append((x, y))
        assert points, f"No annotation on page {index + 1}"
        bounds = (min(x for x, y in points), min(y for x, y in points),
                  max(x for x, y in points), max(y for x, y in points))
        assert all(abs(a-b) <= 1 for a, b in zip(bounds, (40, 50, 109, 119))), bounds
        pix.save(root / f"export-page-{index + 1}.png")
        print(f"Page {index + 1}: rotation {page.rotation}, annotation bounds {bounds}: PASS")
with pymupdf.open(root / "extracted.pdf") as doc:
    assert len(doc) == 1
    assert "Original page 3" in doc[0].get_text()
print("Independent PDF visual and extraction checks passed.")
with pymupdf.open(root / "drawing-export.pdf") as doc:
    assert len(doc) == 1
    pix = doc[0].get_pixmap()
    for x, y in [(58, 85), (216, 75)]:
        red, green, blue = pix.pixel(x, y)[:3]
        assert blue > 180 and red < 80 and green < 80, (x, y, (red, green, blue))
    for x, y in [(110, 200), (180, 180)]:
        red, green, blue = pix.pixel(x, y)[:3]
        assert red > 180 and green < 80 and blue < 80, (x, y, (red, green, blue))
    assert all(channel > 240 for channel in pix.pixel(232, 80)[:3]), "Shaft protrudes beyond arrow tip"
    pix.save(root / "drawing-export.png")
print("Arrowhead fills, doodle strokes, and finger-drawn dots export correctly.")
