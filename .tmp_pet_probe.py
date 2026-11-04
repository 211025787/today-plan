from PIL import Image
im = Image.open(r"app/src/main/assets/dafeiyu/idle.webp")
print(im.size, im.mode, getattr(im, "n_frames", 1))
im.seek(0)
fr = im.convert("RGBA")
a = fr.split()[3]
print("alpha bbox:", a.getbbox())
# coarse alpha occupancy grid 10x8
w,h = fr.size
import statistics
gw, gh = 12, 8
row=[]
for gy in range(gh):
    line=""
    for gx in range(gw):
        box=(gx*w//gw, gy*h//gh, (gx+1)*w//gw, (gy+1)*h//gh)
        crop=a.crop(box)
        px=list(crop.getdata())
        avg=sum(px)/len(px)
        line += "#" if avg>128 else ("+" if avg>40 else (":" if avg>5 else "."))
    row.append(line)
print("\n".join(row))
