"""Read-only offline inspection of already-saved region cells; never opens a live world for writing."""
import io, pathlib, struct, sys, zlib, gzip

def read_nbt(stream):
    def number(fmt):
        return struct.unpack('>'+fmt, stream.read(struct.calcsize(fmt)))[0]
    def string():
        return stream.read(number('H')).decode('utf-8')
    def payload(kind):
        if kind in range(1,7): return number({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[kind])
        if kind==7: return stream.read(number('i'))
        if kind==8: return string()
        if kind==9:
            child, size=number('B'),number('i')
            return [payload(child) for _ in range(size)]
        if kind==10:
            result={}
            while True:
                child=number('B')
                if child==0: return result
                name=string(); result[name]=payload(child)
        if kind in (11,12): return [number('i' if kind==11 else 'q') for _ in range(number('i'))]
        raise ValueError(kind)
    kind=number('B'); string(); return payload(kind)

def cell(world,x,y,z):
    cx,cz=x//16,z//16
    path=world/'region'/f'r.{cx//32}.{cz//32}.mca'
    with path.open('rb') as region:
        region.seek(4*((cx%32)+(cz%32)*32)); location=int.from_bytes(region.read(4),'big')
        if location==0:return {'unavailable':True}
        region.seek((location>>8)*4096); length=int.from_bytes(region.read(4),'big'); kind=region.read(1)[0]
        raw=region.read(length-1)
    chunk=read_nbt(io.BytesIO(gzip.decompress(raw) if kind==1 else zlib.decompress(raw) if kind==2 else raw))
    section=next((s for s in chunk['sections'] if s['Y']==y//16),None)
    if not section or 'block_states' not in section:return {'Name':'minecraft:air'}
    blocks=section['block_states'];palette=blocks['palette']
    if len(palette)==1:return palette[0]
    bits=max(4,(len(palette)-1).bit_length()); per=64//bits
    index=(y%16)*256+(z%16)*16+(x%16)
    value=(blocks['data'][index//per]>>(bits*(index%per)))&((1<<bits)-1)
    return palette[value]

if __name__=='__main__':
    world=pathlib.Path(sys.argv[1]);x,y,z=map(int,sys.argv[2:5])
    for dy in range(-1,2):
        for dz in range(-1,2):
            for dx in range(-1,2):
                print((x+dx,y+dy,z+dz),cell(world,x+dx,y+dy,z+dz))
