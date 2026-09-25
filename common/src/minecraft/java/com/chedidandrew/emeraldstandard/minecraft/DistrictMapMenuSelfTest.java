package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;

/** Disposable map-only fixture: no dependency on building completion or construction timing. */
final class DistrictMapMenuSelfTest {
    static void verify(ServerLevel level) {
        try {
            var directory=Files.createTempDirectory("tes-map-menu-");
            var economy=new EconomyService();economy.start(directory,47,0);
            BlockPos center=new BlockPos(960,level.getMaxY()-24,960);
            var observed=economy.observeVillage(new EconomyService.VillageObservation(
                    level.dimension().identifier().toString(),center.asLong(),0,0,18,20,0,false,List.of()));
            require(observed!=null,"fixture village discovered");
            var player=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"MapMenuFixture"),
                    ClientInformation.createDefault());
            player.setPos(center.getX()+.5,center.getY(),center.getZ()+.5);
            var menu=new BankerMenu(98,player.getInventory(),economy,player,center);
            player.containerMenu=menu;
            require(menu.hasVillage(),"nearby district selected");
            byte[] before=Files.readAllBytes(directory.resolve("the_emerald_standard.properties"));
            require(menu.clickMenuButton(player,BankerMenu.BUTTON_MAP_OPEN),"open map");
            require(menu.districtMap().markers().stream().anyMatch(m->m.kind()==VillageDistrictMap.DISTRICT
                    && m.status()==VillageDistrictMap.CURRENT && m.value()==18),"initial focused district");
            var revision=BankerMenu.class.getDeclaredField("mapRevision");revision.setAccessible(true);
            var last=BankerMenu.class.getDeclaredField("lastMapTick");last.setAccessible(true);
            int initial=revision.getInt(menu);
            for(int i=0;i<100;i++) {
                var view=new VillageDistrictMap.View(20000000+i,20000000,64);
                for(int button:DistrictMapRequest.encode(view))require(menu.clickMenuButton(player,button),"pan accepted");
            }
            require(revision.getInt(menu)==initial,"flood coalesces without doing map work in packet handler");
            last.setLong(menu,level.getGameTime()-5);menu.broadcastChanges();
            var target=new VillageDistrictMap.View(20000099,20000000,64);
            require(menu.districtMap().view().equals(target),"last throttled camera position delivered");
            require(menu.districtMap().markers().isEmpty(),"new empty view replaces old sites");
            require(!level.hasChunk(target.centerX()>>4,target.centerZ()>>4),"map never loads distant chunks");
            int after=revision.getInt(menu);
            int[] packet=DistrictMapRequest.encode(new VillageDistrictMap.View(0,0,64));
            menu.clickMenuButton(player,packet[0]);menu.clickMenuButton(player,packet[1]);
            last.setLong(menu,level.getGameTime()-5);menu.broadcastChanges();
            require(revision.getInt(menu)==after,"incomplete viewport cannot publish");
            require(menu.clickMenuButton(player,BankerMenu.BUTTON_MAP_CLOSE),"close map");
            require(!menu.clickMenuButton(player,packet[2]),"closed map rejects request completion");
            menu.broadcastChanges();require(revision.getInt(menu)==after,"closed map does no refresh work");
            require(menu.clickMenuButton(player,BankerMenu.BUTTON_MAP_OPEN),"reopen map");
            require(menu.districtMap().view().equals(VillageDistrictMap.View.fit(menu.districtMap().focus())),"reopen resets camera");
            require(!menu.clickMenuButton(player,BankerMenu.BUTTON_MAP_NEXT),"retired page buttons rejected");
            player.containerMenu=player.inventoryMenu;
            require(!menu.clickMenuButton(player,packet[0]),"stale menu cannot receive map requests");
            player.containerMenu=menu;player.setPos(center.getX()+100,center.getY(),center.getZ());
            require(!menu.clickMenuButton(player,packet[0]),"out-of-range viewer rejected");
            require(Arrays.equals(before,Files.readAllBytes(directory.resolve("the_emerald_standard.properties"))),
                    "map operations do not write economy or building data");
            System.out.println("PASS native continuous map menu: focus, coalesced latest view, empty area, no chunks, partial/closed/stale/distant requests, no saved mutations");
        } catch(Exception failure) { throw new IllegalStateException("Continuous map menu fixture",failure); }
    }
    private static void require(boolean value,String message) { if(!value)throw new IllegalStateException(message); }
}
