package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.minecraft.ConstructionContent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Opt-in real-renderer preview, without opening or editing any player world. */
final class ConstructionVisualChecks extends Screen {
    private final float frame;
    ConstructionVisualChecks(float frame) { super(Component.literal("Construction crew preview")); this.frame=frame; }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0xff192820);
        if(frame<0) { fence(g); return; }
        g.text(font,"Builder workwear / native Minecraft renderer",12,10,0xfff4ce56,false);
        int cellWidth=width/4, cellHeight=(height-40)/2;
        var styles=com.chedidandrew.emeraldstandard.minecraft.ConstructionBuilder.CLOTHING_STYLES;
        for(int i=0;i<8;i++) {
            var s=new ConstructionBuilderRenderer.State(); s.entityType=ConstructionContent.builder;
            s.scale=1; s.ageScale=1; s.pose=Pose.STANDING; s.boundingBoxHeight=1.95F; s.boundingBoxWidth=.6F;
            s.clothing=styles.get(i%styles.size()); s.bodyRot=i==7?90:155;
            s.ageInTicks=frame; s.hammering=frame>0 && frame!=12;
            s.walkAnimationPos=frame; s.walkAnimationSpeed=frame==12?.65F:0;
            int x=i%4*cellWidth,y=24+i/4*cellHeight;
            g.entity(s,(cellHeight-24)*.43F,new Vector3f(0,1,0),new Quaternionf().rotateZ((float)Math.PI),null,
                    x+2,y,x+cellWidth-2,y+cellHeight-16);
            g.text(font,i==7?"Hammer profile":s.clothing,x+8,y+cellHeight-12,0xffeeeecc,false);
        }
        g.text(font,frame==0?"Idle / biome clothing":frame==12?"Walking / articulated arms":"Working / fore-aft hammer strike",12,height-12,0xffeeeecc,false);
    }
    private void fence(GuiGraphicsExtractor g) {
        g.text(font,"Temporary construction caution fence",12,12,0xfff4ce56,false);
        var s=new net.minecraft.client.renderer.entity.state.BlockDisplayEntityRenderState();
        s.entityType=net.minecraft.world.entity.EntityTypes.BLOCK_DISPLAY;
        s.boundingBoxWidth=1; s.boundingBoxHeight=1;
        var transform=new com.mojang.math.Transformation(new Vector3f(-.5F,0,-.5F),new Quaternionf(),new Vector3f(1),new Quaternionf());
        s.renderState=new net.minecraft.world.entity.Display.RenderState(f -> transform,
                net.minecraft.world.entity.Display.BillboardConstraints.FIXED,15728880,f->0,f->0,0);
        new net.minecraft.client.renderer.block.BlockModelResolver(minecraft.getModelManager()).update(s.blockModel,
                ConstructionContent.fence.defaultBlockState().setValue(net.minecraft.world.level.block.FenceBlock.NORTH,true)
                        .setValue(net.minecraft.world.level.block.FenceBlock.SOUTH,true),
                net.minecraft.client.renderer.entity.DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
        if(s.blockModel.isEmpty()) throw new IllegalStateException("Construction fence model missing");
        g.entity(s,height*.65F,new Vector3f(0,.5F,0),new Quaternionf().rotateZ((float)Math.PI).rotateX(.35F).rotateY(.9F),null,
                width/4,35,width*3/4,height-30);
        g.text(font,"Original plants return after the site closes",12,height-14,0xffeeeecc,false);
    }
    static void verifyAnimation() {
        var resources=net.minecraft.client.Minecraft.getInstance().getResourceManager();
        for(String style:com.chedidandrew.emeraldstandard.minecraft.ConstructionBuilder.CLOTHING_STYLES)
            if(resources.getResource(ConstructionBuilderRenderer.clothingTexture(style)).isEmpty())
                throw new IllegalStateException("Missing native builder clothes: "+style);
        if(resources.getResource(ConstructionBuilderRenderer.WORK_APRON).isEmpty())
            throw new IllegalStateException("Missing native work apron");
        var tool=new ConstructionBuilderRenderer.Model(ConstructionBuilderRenderer.HAMMER);
        var head=tool.hammer.getRandomCube(net.minecraft.util.RandomSource.create(0));
        if(head.maxZ-head.minZ<=head.maxX-head.minX || head.minY<0)
            throw new IllegalStateException("Hammer head must sit below the fist and extend fore-and-aft, not sideways");
        var model=new ConstructionBuilderRenderer.Model(0); var s=new ConstructionBuilderRenderer.State();
        s.hammering=true; s.ageInTicks=0; model.setupAnim(s); float before=model.rightArm.xRot;
        s.ageInTicks=2; model.setupAnim(s);
        if(Math.abs(before-model.rightArm.xRot)<.3F) throw new IllegalStateException("Builder hammer arm did not animate");
        float first=model.rightArm.xRot;
        for(int kind=1;kind<=ConstructionBuilderRenderer.HAMMER;kind++) {
            var layer=new ConstructionBuilderRenderer.Model(kind); layer.setupAnim(s);
            if(Math.abs(layer.rightArm.xRot-first)>.001F || layer.hammer.x!=-1 || layer.hammer.y!=7.5F)
                throw new IllegalStateException("Workwear/tool layer detached from moving hand");
        }
        s.hammerPhase=7; model.setupAnim(s);
        if(Math.abs(first-model.rightArm.xRot)<.2F) throw new IllegalStateException("Crew hammer phases synchronized");
        s.hammering=false; s.walkAnimationSpeed=0; model.setupAnim(s);
        if(Math.abs(model.rightArm.xRot)>.001F || Math.abs(model.leftArm.zRot)>.001F)
            throw new IllegalStateException("Paused worker retained a hammer pose");
    }
}
