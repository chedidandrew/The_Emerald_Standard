package com.chedidandrew.emeraldstandard.client;
import java.nio.file.*;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

public final class ExploredTerrainStoreRegressionTest {
    private static final int GREEN=0xff559933, BLUE=0xff2255bb;
    public static void main(String[] args)throws Exception {
        check(ExploredTerrainStore.MEMORY_LIMIT>DistrictTerrainRaster.WIDTH*DistrictTerrainRaster.HEIGHT,
                "widest view must not evict every asynchronous result before its next raster pass");
        Path root=Files.createTempDirectory("tes-map-test-");
        try {
            var store=new ExploredTerrainStore(root,"world A","overworld");
            check(store.record(-1,-1,tile(GREEN)),"accept terrain");
            check(store.color(-1,-1)==GREEN&&store.color(-16,-16)==GREEN,"negative cells");
            check(store.color(0,0)==0,"unknown is not invented");
            waitFor(()->store.pending()==0,store);
            Path scope=store.directory();store.close();

            var reopen=new ExploredTerrainStore(root,"world A","overworld");
            waitFor(()->reopen.color(-1,-1)==GREEN,reopen);
            check(reopen.directory().equals(scope),"stable world scope");
            var other=new ExploredTerrainStore(root,"world B","overworld");
            var nether=new ExploredTerrainStore(root,"world A","nether");
            other.color(-1,-1);nether.color(-1,-1);
            waitFor(()->other.pending()==0&&nether.pending()==0,other);
            check(other.color(-1,-1)==0&&nether.color(-1,-1)==0,"world and dimension isolation");
            other.close();nether.close();
            // A cold disk read must never overwrite a freshly observed update.
            var racing=new ExploredTerrainStore(root,"world A","overworld");
            racing.color(-1,-1);
            racing.record(-1,-1,tile(BLUE));
            waitFor(()->racing.pending()==0,racing);
            check(racing.color(-1,-1)==BLUE,"new observations win disk race");
            for(int i=0;i<500;i++)racing.record(0,0,tile(0xff000000|i));
            racing.close();
            waitFor(()->racing.pending()==0,racing);
            var latest=new ExploredTerrainStore(root,"world A","overworld");
            waitFor(()->latest.color(0,0)==(0xff000000|499),latest);
            latest.record(1,0,tile(GREEN));waitFor(()->latest.pending()==0,latest);latest.close();
            // Corruption in one fixed-size slot must not affect its neighbor.
            try(var file=new RandomAccessFile(scope.resolve("r.0.0.map").toFile(),"rw")){
                file.seek(12);file.writeInt(123);
            }
            var corrupt=new ExploredTerrainStore(root,"world A","overworld");
            corrupt.color(0,0);corrupt.color(16,0);
            waitFor(()->corrupt.pending()==0,corrupt);
            check(corrupt.color(0,0)==0&&corrupt.color(16,0)==GREEN,"checksum isolates damaged tile");
            corrupt.close();
            // Disk-backed reads survive RAM eviction; a busy disk never grows the LRU indefinitely.
            for(int i=0;i<ExploredTerrainStore.MEMORY_LIMIT+100;i++) {
                reopen.color(i*16,1600);
                if(i%64==0)waitFor(()->reopen.pending()==0,reopen);
            }
            check(reopen.memoryTiles()<=ExploredTerrainStore.MEMORY_LIMIT,"bounded memory");
            waitFor(()->reopen.color(-1,-1)==BLUE,reopen);
            reopen.close();
            // Closing with more dirty tiles than the worker queue still drains coalesced writes.
            var bulk=new ExploredTerrainStore(root,"bulk","overworld");
            for(int i=0;i<600;i++)check(bulk.record(i,0,tile(GREEN)),"bounded burst accepted");
            bulk.close();waitFor(()->bulk.pending()==0,bulk);
            var bulkRead=new ExploredTerrainStore(root,"bulk","overworld");
            waitFor(()->bulkRead.color(599*16,0)==GREEN,bulkRead);bulkRead.close();
            System.out.println("PASS explored terrain: reopen, negative coordinates, isolation, races, corruption, eviction and disconnect");
        } finally {
            // The test's own resolved temporary directory only; no game or user cache is touched.
            try(var files=Files.walk(root)){for(Path path:files.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
        }
    }
    private static int[] tile(int color){int[] colors=new int[64];Arrays.fill(colors,color);return colors;}
    private static void waitFor(BooleanSupplier ready,ExploredTerrainStore store)throws Exception{
        long limit=System.nanoTime()+15_000_000_000L;
        while(!ready.getAsBoolean()){if(System.nanoTime()>limit)throw new AssertionError("cache timeout: "+store.error());store.flush();Thread.sleep(5);}
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
