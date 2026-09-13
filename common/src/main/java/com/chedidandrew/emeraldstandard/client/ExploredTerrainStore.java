package com.chedidandrew.emeraldstandard.client;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.CRC32;

/** Client-only derivative map data. All disk work is bounded and off the render/game thread. */
public final class ExploredTerrainStore implements AutoCloseable {
    // Must exceed one whole map view (130 x 82 distinct tiles at the widest zoom).
    // Otherwise asynchronous reads can be evicted before the raster consumes them.
    public static final int SIDE=8, COLORS=64, MEMORY_LIMIT=16384, IO_QUEUE_LIMIT=128;
    private static final int MAGIC=0x54455332, RECORD_BYTES=8+COLORS*4;
    private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(IO_QUEUE_LIMIT),r->{Thread t=new Thread(r,"emerald-map-cache");t.setDaemon(true);return t;},
            new ThreadPoolExecutor.AbortPolicy());
    static { IO.allowCoreThreadTimeOut(true); }
    private static final class Tile { int[] colors; long version; boolean dirty,busy; }
    private final LinkedHashMap<Long,Tile> memory=new LinkedHashMap<>(64,.75f,true);
    private final Path directory;
    private boolean closed;
    private long retryAfter;
    private volatile String error="";
    public ExploredTerrainStore(Path root,String world,String dimension) {
        directory=root.resolve(identity(world+"\n"+dimension));
    }
    public static String identity(String value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public static long key(int x,int z){return (x&0xffffffffL)|((long)z<<32);}
    private Tile entry(long key) {
        Tile tile=memory.get(key);if(tile!=null)return tile;
        if(memory.size()>=MEMORY_LIMIT) {
            var it=memory.entrySet().iterator();boolean removed=false;
            while(it.hasNext()) {var e=it.next();if(!e.getValue().dirty&&!e.getValue().busy){it.remove();removed=true;break;}}
            if(!removed)return null; // Slow/unavailable disk must never grow RAM without bound.
        }
        tile=new Tile();memory.put(key,tile);return tile;
    }
    /** Zero means unknown/pending; callers never wait on an I/O future. */
    public synchronized int color(int blockX,int blockZ) {
        if(closed)return 0;
        long key=key(Math.floorDiv(blockX,16),Math.floorDiv(blockZ,16));Tile t=entry(key);
        if(t==null)return 0;
        if(t.colors==null)schedule(key,t);
        return t.colors==null?0:t.colors[Math.floorMod(blockZ,16)/2*SIDE+Math.floorMod(blockX,16)/2];
    }
    public synchronized boolean record(int chunkX,int chunkZ,int[] colors) {
        if(closed||colors==null||colors.length!=COLORS)return false;
        if(Arrays.stream(colors).allMatch(c->c==0))return true;
        long key=key(chunkX,chunkZ);Tile t=entry(key);if(t==null)return false;
        // Known transparent/void samples remain unknown; never erase a prior observed surface.
        int[] merged=colors.clone();
        if(t.colors!=null)for(int i=0;i<COLORS;i++)if(merged[i]==0)merged[i]=t.colors[i];
        if(!Arrays.equals(t.colors,merged)){t.colors=merged;t.version++;t.dirty=true;}
        schedule(key,t);return true;
    }
    public synchronized void flush() {
        if(closed)return;
        int n=0;for(var e:memory.entrySet())if(e.getValue().dirty&&!e.getValue().busy&&n++<8)schedule(e.getKey(),e.getValue());
    }
    private void schedule(long key,Tile tile) {
        if(tile.busy||(!tile.dirty&&tile.colors!=null)||System.nanoTime()<retryAfter)return;
        tile.busy=true;
        try {IO.execute(()->work(key,tile));}
        catch(RejectedExecutionException full){tile.busy=false;} // Retry from a later tick/view; never block.
    }
    private void work(long key,Tile tile) {
        try {
            int[] snapshot;long version;
            synchronized(this){snapshot=tile.colors==null?null:tile.colors.clone();version=tile.version;}
            if(snapshot==null) {
                int[] disk=read(key);
                synchronized(this){if(tile.version==version&&tile.colors==null)tile.colors=disk;}
            } else {
                // If a loaded chunk arrived before its old disk tile, preserve unsampled old cells.
                if(Arrays.stream(snapshot).anyMatch(c->c==0)) {
                    int[] previous=read(key);
                    for(int i=0;i<COLORS;i++)if(snapshot[i]==0)snapshot[i]=previous[i];
                }
                write(key,snapshot);
                synchronized(this){if(tile.version==version){tile.dirty=false;tile.colors=snapshot;}}
            }
            error="";
        } catch(IOException|RuntimeException failed) {
            synchronized(this){retryAfter=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);}
            error="Terrain cache unavailable; showing session observations.";
        } finally {
            synchronized(this){
                tile.busy=false;
                // A newer sample while a write ran must not be lost, including during disconnect.
                if(tile.dirty&&System.nanoTime()>=retryAfter)schedule(key,tile);
                if(closed)drainClosed();
            }
        }
    }
    private Path region(long key) {
        return directory.resolve("r."+Math.floorDiv((int)key,32)+"."+Math.floorDiv((int)(key>>32),32)+".map");
    }
    private long offset(long key){return (Math.floorMod((int)(key>>32),32)*32L+Math.floorMod((int)key,32))*RECORD_BYTES;}
    private int[] read(long key)throws IOException {
        Path path=region(key);int[] colors=new int[COLORS];
        if(!Files.isRegularFile(path))return colors;
        try(var file=new RandomAccessFile(path.toFile(),"r")){
            long offset=offset(key);if(file.length()<offset+RECORD_BYTES)return colors;
            file.seek(offset);if(file.readInt()!=MAGIC)return colors;
            int crc=file.readInt();byte[] bytes=new byte[COLORS*4];file.readFully(bytes);
            CRC32 check=new CRC32();check.update(bytes);if(crc!=(int)check.getValue())return colors;
            var input=new DataInputStream(new ByteArrayInputStream(bytes));
            for(int i=0;i<COLORS;i++)colors[i]=input.readInt();
            return colors;
        }
    }
    private void write(long key,int[] colors)throws IOException {
        Files.createDirectories(directory);
        var bytes=new ByteArrayOutputStream(COLORS*4);var out=new DataOutputStream(bytes);
        for(int color:colors)out.writeInt(color);
        byte[] data=bytes.toByteArray();CRC32 check=new CRC32();check.update(data);
        try(var file=new RandomAccessFile(region(key).toFile(),"rw")){
            file.seek(offset(key));file.writeInt(MAGIC);file.writeInt((int)check.getValue());file.write(data);
        } // Torn records fail their checksum; this is disposable terrain, never game-save data.
    }
    public synchronized int memoryTiles(){return memory.size();}
    public synchronized int pending(){return (int)memory.values().stream().filter(t->t.busy||t.dirty).count();}
    public String error(){return error;}
    public Path directory(){return directory;}
    private void drainClosed() {
        // A departing world drains through the same bounded worker queue, never a main-thread wait.
        int n=0;
        for(var e:memory.entrySet())if(e.getValue().dirty&&!e.getValue().busy&&n++<8)schedule(e.getKey(),e.getValue());
    }
    @Override public synchronized void close(){closed=true;drainClosed();}
}
