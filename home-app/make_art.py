"""Launcher icon + 320x180 TV banner for the Home app."""
import os, sys
from PIL import Image, ImageDraw, ImageFont
res = sys.argv[1]
def tile(S):
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rounded_rectangle([S*.04, S*.04, S*.96, S*.96], radius=S*.22, fill=(22, 26, 34, 255))
    g = S*.16; w = (S*.92 - g*2 - S*.08) / 3 * .78
    for i in range(3):
        for j in range(2):
            x = S*.17 + i*(w + S*.06); y = S*.3 + j*(w*.62 + S*.07)
            d.rounded_rectangle([x, y, x+w, y+w*.62], radius=w*.15,
                                fill=(255, 255, 255, 235) if (i, j) == (0, 0) else (120, 132, 150, 255))
    return im
for dens, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144}.items():
    os.makedirs(f"{res}/drawable-{dens}", exist_ok=True)
    tile(px*4).resize((px, px), Image.LANCZOS).save(f"{res}/drawable-{dens}/ic_launcher.png")
W, H = 320*3, 180*3
b = Image.new("RGB", (W, H)); d = ImageDraw.Draw(b)
for y in range(H):
    t = y / H; d.line([(0, y), (W, y)], fill=(int(24+20*t), int(28+18*t), int(38+30*t)))
icon = tile(300); b.paste(icon, (90, (H-300)//2), icon)
try:
    f = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 120)
except Exception:
    f = ImageFont.load_default()
d.text((440, H//2), "Home", font=f, fill=(255, 255, 255), anchor="lm")
os.makedirs(f"{res}/drawable-xhdpi", exist_ok=True)
b.resize((320, 180), Image.LANCZOS).save(f"{res}/drawable-xhdpi/banner.png")
print("art written")
