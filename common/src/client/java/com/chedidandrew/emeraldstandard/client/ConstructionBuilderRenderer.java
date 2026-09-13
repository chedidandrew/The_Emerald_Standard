package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.minecraft.ConstructionBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** Native villager clothes and craft apron, with independent working arms and a fore-aft hammer. */
public final class ConstructionBuilderRenderer extends MobRenderer<ConstructionBuilder, ConstructionBuilderRenderer.State, ConstructionBuilderRenderer.Model> {
    static final int SKIN=0, CLOTHES=1, APRON=2, HANDLE=3, HAMMER=4;
    static final Identifier BASE=Identifier.withDefaultNamespace("textures/entity/villager/villager.png");
    static final Identifier WORK_APRON=Identifier.withDefaultNamespace("textures/entity/villager/profession/toolsmith.png");
    public static final class State extends LivingEntityRenderState {
        public boolean hammering;
        public float hammerPhase;
        public String clothing="plains";
    }
    public ConstructionBuilderRenderer(EntityRendererProvider.Context context) {
        super(context,new Model(SKIN),.45F);
        addLayer(new Gear(this,CLOTHES));
        addLayer(new Gear(this,APRON));
        addLayer(new Gear(this,HANDLE));
        addLayer(new Gear(this,HAMMER));
    }
    @Override public State createRenderState() {return new State();}
    @Override public Identifier getTextureLocation(State state) {return BASE;}
    @Override public void extractRenderState(ConstructionBuilder entity,State state,float partialTicks) {
        super.extractRenderState(entity,state,partialTicks);
        state.hammering=entity.hammering();state.hammerPhase=Math.floorMod(entity.getUUID().hashCode(),20);
        state.clothing=entity.clothing();
    }
    static Identifier clothingTexture(String style) {
        return Identifier.withDefaultNamespace("textures/entity/villager/type/"+ConstructionBuilder.validClothing(style)+".png");
    }
    private static final class Gear extends RenderLayer<State,Model> {
        private final Model model;
        private final int kind;
        Gear(ConstructionBuilderRenderer renderer,int kind) {
            super(renderer);this.kind=kind;model=new Model(kind);
        }
        @Override public void submit(PoseStack stack,SubmitNodeCollector collector,int light,State state,float yRot,float xRot) {
            Identifier texture=switch(kind) {
                case CLOTHES -> clothingTexture(state.clothing);
                case APRON -> WORK_APRON;
                case HANDLE -> Identifier.withDefaultNamespace("textures/block/spruce_planks.png");
                default -> Identifier.withDefaultNamespace("textures/block/iron_block.png");
            };
            // Native villager layers are coplanar: draw biome first, then apron, as vanilla does.
            // Giving both order 1 lets texture batching hide the apron on some biome outfits.
            coloredCutoutModelCopyLayerRender(model,texture,stack,collector,light,state,-1,kind);
        }
    }
    public static final class Model extends EntityModel<State> {
        final ModelPart head,rightArm,leftArm,rightLeg,leftLeg,hammer;
        Model(int kind) {
            super(layer(kind).bakeRoot());
            head=root.getChild("head");rightArm=root.getChild("right_arm");leftArm=root.getChild("left_arm");
            rightLeg=root.getChild("right_leg");leftLeg=root.getChild("left_leg");hammer=rightArm.getChild("hammer");
        }
        private static CubeListBuilder cube() {return CubeListBuilder.create();}
        private static LayerDefinition layer(int kind) {
            MeshDefinition mesh=new MeshDefinition();PartDefinition root=mesh.getRoot();
            boolean villager=kind<=APRON;
            CubeListBuilder h=cube(),b=cube(),ra=cube(),la=cube(),leg=cube();
            if(villager) {
                // Vanilla 64x64 villager UVs, including native biome hats/coat/apron overlays.
                h.texOffs(0,0).addBox(-4,-10,-4,8,10,8);
                b.texOffs(16,20).addBox(-4,0,-3,8,12,6);
                ra.texOffs(44,22).addBox(-3,-2,-2,4,8,4)
                        .texOffs(40,38).addBox(-3,6,-2,4,4,4);
                la.texOffs(44,22).mirror().addBox(-1,-2,-2,4,8,4)
                        .texOffs(40,38).addBox(-1,6,-2,4,4,4);
                leg.texOffs(0,22).addBox(-2,0,-2,4,12,4);
            }
            var head=root.addOrReplaceChild("head",h,PartPose.ZERO);
            var body=root.addOrReplaceChild("body",b,PartPose.ZERO);
            if(villager) {
                head.addOrReplaceChild("nose",cube().texOffs(24,0).addBox(-1,-1,-6,2,4,2),PartPose.offset(0,-2,0));
                var hat=head.addOrReplaceChild("hat",cube().texOffs(32,0)
                        .addBox(-4,-10,-4,8,10,8,new CubeDeformation(.51F)),PartPose.ZERO);
                hat.addOrReplaceChild("hat_rim",cube().texOffs(30,47).addBox(-8,-8,-6,16,16,1),
                        PartPose.rotation(-(float)Math.PI/2,0,0));
                body.addOrReplaceChild("jacket",cube().texOffs(0,38)
                        .addBox(-4,0,-3,8,20,6,new CubeDeformation(.5F)),PartPose.ZERO);
            }
            var right=root.addOrReplaceChild("right_arm",ra,PartPose.offset(-5,2,0));
            root.addOrReplaceChild("left_arm",la,PartPose.offset(5,2,0));
            root.addOrReplaceChild("right_leg",leg,PartPose.offset(-2,12,0));
            root.addOrReplaceChild("left_leg",leg,PartPose.offset(2,12,0));
            CubeListBuilder tool=cube();
            if(kind==HANDLE)tool.addBox(-.5F,-1.5F,-.5F,1,8,1);
            // Long head axis is Z (fore/aft), not X (side-to-side). Its striking faces now
            // travel in the Y/Z plane of the arm swing; the handle remains inside the fist.
            if(kind==HAMMER)tool.addBox(-1.5F,5,-2.5F,3,2,5);
            right.addOrReplaceChild("hammer",tool,PartPose.offset(-1,7.5F,0));
            return LayerDefinition.create(mesh,villager?64:16,villager?64:16);
        }
        @Override public void setupAnim(State state) {
            super.setupAnim(state);
            head.yRot=state.yRot*(float)Math.PI/180;head.xRot=state.xRot*(float)Math.PI/180;
            float stride=(float)Math.cos(state.walkAnimationPos*.6662)*state.walkAnimationSpeed;
            rightLeg.xRot=stride;leftLeg.xRot=-stride;rightArm.xRot=-stride;leftArm.xRot=stride;
            if(state.hammering) {
                rightArm.xRot=-1.45F+.65F*(float)Math.sin((state.ageInTicks+state.hammerPhase)*.65F);
                leftArm.xRot=-.55F;leftArm.zRot=-.16F;head.xRot=.25F;
            }
        }
    }
}
