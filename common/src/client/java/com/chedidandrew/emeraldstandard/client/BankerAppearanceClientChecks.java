package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.minecraft.BankerProfessionSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.resources.metadata.animation.VillagerMetadataSection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.villager.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Opt-in native renderer preview: no world, entities, gameplay or player settings are modified. */
final class BankerAppearanceClientChecks extends Screen {
    private final int view;
    BankerAppearanceClientChecks(int view) {super(Component.literal("Icon-matching Bankers"));this.view=view;}
    static void verify(Minecraft client) throws Exception {
        for(String kind:new String[]{"villager","zombie_villager"}) {
            var id=Identifier.fromNamespaceAndPath("the_emerald_standard","textures/entity/"+kind+"/profession/banker.png");
            var resource=client.getResourceManager().getResource(id).orElseThrow();
            var metadata=resource.metadata().getSection(VillagerMetadataSection.TYPE).orElseThrow();
            if(metadata.hat()!=VillagerMetadataSection.Hat.FULL)throw new IllegalStateException("Banker biome hat suppression missing");
            client.getTextureManager().getTexture(id);
        }
        if(BankerProfessionSupport.registeredBanker().isEmpty())throw new IllegalStateException("Native Banker profession missing");
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0xff344247);
        g.text(font,"Bankers: native villager model / icon outfit",8,8,0xffebf3e9,false);
        var types=java.util.List.of(VillagerType.PLAINS,VillagerType.DESERT,VillagerType.SAVANNA,
                VillagerType.TAIGA,VillagerType.SNOW,VillagerType.JUNGLE,VillagerType.SWAMP);
        int cellW=width/4,cellH=(height-34)/2;
        for(int i=0;i<types.size();i++) {
            var state=new VillagerRenderState();state.entityType=EntityTypes.VILLAGER;
            state.boundingBoxWidth=.6F;state.boundingBoxHeight=1.95F;state.eyeHeight=1.62F;
            state.scale=state.ageScale=1;state.pose=Pose.STANDING;state.lightCoords=15728880;
            state.villagerData=new VillagerData(BuiltInRegistries.VILLAGER_TYPE.getOrThrow(types.get(i)),
                    BankerProfessionSupport.registeredBanker().orElseThrow(),i%2==0?1:5);
            int x=i%4*cellW,y=24+i/4*cellH;
            var rotation=new Quaternionf().rotateZ((float)Math.PI).rotateY(view==0?.25F:view==1?(float)Math.PI+.35F:1.15F);
            g.entity(state,Math.min(cellW*.68F,cellH*.46F),new Vector3f(0,1,0),rotation,null,x,y,x+cellW,y+cellH-14);
            g.text(font,types.get(i).identifier().getPath(),x+8,y+cellH-10,0xffebf3e9,false);
        }
    }
}
