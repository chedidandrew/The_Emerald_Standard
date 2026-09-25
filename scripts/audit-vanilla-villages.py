"""Read-only inventory of the pinned Minecraft village NBT/resources; no world edits."""
import gzip, hashlib, io, json, struct, sys, zipfile
def read_nbt(data):
    src = io.BytesIO(gzip.decompress(data))
    def number(fmt):
        return struct.unpack(">" + fmt, src.read(struct.calcsize(">" + fmt)))[0]
    def string():
        return src.read(number("H")).decode("utf-8")
    def value(kind):
        if kind in (1, 2, 3, 4, 5, 6):
            return number({1:"b",2:"h",3:"i",4:"q",5:"f",6:"d"}[kind])
        if kind == 7:
            return list(src.read(number("i")))
        if kind == 8:
            return string()
        if kind == 9:
            item, size = number("B"), number("i")
            return [value(item) for _ in range(size)]
        if kind == 10:
            result = {}
            while (item := number("B")):
                name = string()
                result[name] = value(item)
            return result
        if kind in (11,12):
            return [number("i" if kind == 11 else "q") for _ in range(number("i"))]
        raise ValueError(kind)
    assert number("B") == 10
    string()
    return value(10)
def inventory(path):
    with zipfile.ZipFile(path) as jar:
        for name in sorted(jar.namelist()):
            if not name.startswith("data/minecraft/structure/village/") or not name.endswith(".nbt"):
                continue
            bits = name.split("/")
            if bits[4] not in ("plains","desert","savanna","taiga","snowy") or bits[5] not in ("houses","town_centers","decor"):
                continue
            data = jar.read(name)
            nbt = read_nbt(data)
            palette = nbt.get("palette", nbt.get("palettes", [[]])[0])
            blocks = [(b, palette[b["state"]]) for b in nbt["blocks"]]
            jigsaws = [dict(pos=b["pos"], props=p.get("Properties",{}), nbt=b.get("nbt",{})) for b,p in blocks if p["Name"]=="minecraft:jigsaw"]
            yield dict(id="minecraft:"+name.removeprefix("data/minecraft/structure/").removesuffix(".nbt"),
                       hash=hashlib.sha256(data).hexdigest(), size=nbt["size"],
                       blocks=len(blocks), beds=sum(p["Name"].endswith("_bed") and p.get("Properties",{}).get("part")=="head" for b,p in blocks),
                       jigsaws=jigsaws, entities=len(nbt.get("entities",[])),
                       fluids=[dict(pos=b["pos"],state=p) for b,p in blocks if p["Name"] in ("minecraft:water","minecraft:lava")])
if __name__ == "__main__":
    records=list(inventory(sys.argv[1]))
    if len(sys.argv)>2 and sys.argv[2]=="manifest":
        print(json.dumps(records, separators=(",",":")))
    else:
        for r in records:
            print(json.dumps(r))
