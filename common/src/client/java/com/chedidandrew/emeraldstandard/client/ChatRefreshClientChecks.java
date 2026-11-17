package com.chedidandrew.emeraldstandard.client;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Explicit opt-in, copied disposable-world check; no actions during ordinary launches. */
public final class ChatRefreshClientChecks {
    private static final AtomicInteger DEFERRED=new AtomicInteger(), RUN=new AtomicInteger();
    public static void deferred() {if(Boolean.getBoolean("the_emerald_standard.clientShutdownOnly"))DEFERRED.incrementAndGet();}
    public static void dispatched(Minecraft client) {
        if(!Boolean.getBoolean("the_emerald_standard.clientShutdownOnly"))return;
        if(!client.isSameThread())throw new IllegalStateException("Chat refresh dispatched off render thread");
        RUN.incrementAndGet();
    }
    static void verify(Minecraft client) throws Exception {
        String name="TES-chat-shutdown-fixture";
        if(!java.nio.file.Files.isRegularFile(client.gameDirectory.toPath().resolve("saves").resolve(name).resolve("level.dat")))
            throw new IllegalStateException("Missing copied disposable shutdown world");
        for(int restart=0;restart<2;restart++) {
            onClient(client,()->{client.createWorldOpenFlows().openWorld(name,()->{});return null;});
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);
            while(!onClient(client,()->client.player!=null&&client.getSingleplayerServer()!=null)) {
                if(System.nanoTime()>deadline)throw new IllegalStateException("Disposable integrated world failed to load");
                Thread.sleep(100);
            }
            var player=onClient(client,()->client.player);var server=onClient(client,client::getSingleplayerServer);
            DEFERRED.set(0);RUN.set(0);
            // Hold the render thread while the server queues UI refreshes. A synchronous handoff
            // would deadlock; an unguarded vanilla call mutates chat on the wrong thread.
            onClient(client,()->{
                CompletableFuture<Void> calls=new CompletableFuture<>();
                server.execute(()->{try{for(int i=0;i<256;i++)player.refreshChatAbilities();calls.complete(null);}catch(Throwable t){calls.completeExceptionally(t);}});
                try{calls.get(10,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}
                if(DEFERRED.get()!=256||RUN.get()!=0)throw new IllegalStateException("Off-thread refresh was not safely deferred");
                for(int i=0;i<256;i++)player.sendSystemMessage(Component.literal("Shutdown chat fixture "+i));
                return null;
            });
            onClient(client,()->{if(RUN.get()!=256)throw new IllegalStateException("Queued chat refreshes not delivered");player.refreshChatAbilities();return null;});
            onClient(client,()->{client.disconnectWithSavingScreen();return null;});
            if(!server.isStopped()) {
                long stopDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
                while(!server.isStopped()){if(System.nanoTime()>stopDeadline)throw new IllegalStateException("Integrated shutdown hung");Thread.sleep(50);}
            }
            int beforeStale=RUN.get();
            onClient(client,()->{
                if(client.player!=null)throw new IllegalStateException("Disconnected player still active");
                int ran=RUN.get();CompletableFuture<Void> old=new CompletableFuture<>();
                new Thread(()->{player.refreshChatAbilities();old.complete(null);},"tes-old-world-chat-test").start();
                try{old.get(10,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}
                if(RUN.get()!=ran)throw new IllegalStateException("Old-world chat ran synchronously");return null;
            });
            onClient(client,()->{if(RUN.get()!=beforeStale)throw new IllegalStateException("Stale callback touched disconnected chat");return null;});
            System.out.println("PASS native client chat: queued 256 server-thread refreshes, render ownership, disconnect, stale callback; restart="+restart);
        }
    }
    private static <T>T onClient(Minecraft client,Supplier<T> work)throws Exception {
        CompletableFuture<T> result=new CompletableFuture<>();client.execute(()->{try{result.complete(work.get());}catch(Throwable t){result.completeExceptionally(t);}});
        return result.get(150,TimeUnit.SECONDS);
    }
    private ChatRefreshClientChecks(){}
}
