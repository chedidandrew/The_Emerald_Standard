package com.chedidandrew.emeraldstandard.minecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Visiting construction crew, not breeding/trading villagers and never part of population/food counts. */
public final class ConstructionBuilder extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> HAMMERING = SynchedEntityData.defineId(ConstructionBuilder.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> CLOTHING = SynchedEntityData.defineId(ConstructionBuilder.class, EntityDataSerializers.STRING);
    private boolean clothingChosen;
    public static final java.util.List<String> CLOTHING_STYLES = java.util.List.of("plains","taiga","desert","savanna","snow","jungle","swamp");
    String job = "";
    BlockPos destination = BlockPos.ZERO;
    BlockPos focus = BlockPos.ZERO;
    boolean leaving;
    boolean allowedToWork;
    static final int PAUSE_DEPARTURE_TICKS = 200;
    private int departureTicks;
    private int pausedTicks;
    private int nextPathTick;
    private BlockPos failedDestination;
    private int failedUntil;
    int pathRequests; // Bounded native regression observation, not saved economic state.
    private int stuckTicks;
    private double lastDistance = Double.MAX_VALUE;
    public ConstructionBuilder(EntityType<? extends ConstructionBuilder> type, Level level) {
        super(type, level); setPersistenceRequired(); setCanPickUpLoot(false); xpReward = 0;
    }
    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, .30)
                .add(Attributes.FOLLOW_RANGE, 64).add(Attributes.STEP_HEIGHT, 1);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new net.minecraft.world.entity.ai.goal.RandomStrollGoal(this,.6) {
            @Override public boolean canUse() { return job.isEmpty() && super.canUse(); }
            @Override public boolean canContinueToUse() { return job.isEmpty() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(6, new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(this) {
            @Override public boolean canUse() { return job.isEmpty() && super.canUse(); }
            @Override public boolean canContinueToUse() { return job.isEmpty() && super.canContinueToUse(); }
        });
    }
    @Override protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
        return new net.minecraft.world.entity.ai.navigation.GroundPathNavigation(this,level) {
            @Override protected void followThePath() {
                if (path == null || path.isDone()) return;
                var position = getTempMobPos();
                var waypoint = path.getNextEntityPos(mob);
                // Vanilla's generous waypoint tolerance/corner cutting can clip a worker's
                // shoulders into the end of a tape rail. Pass near each node center first.
                maxDistanceToWaypoint = .2F;
                if (Math.abs(position.x-waypoint.x)<=.2 && Math.abs(position.z-waypoint.z)<=.2
                        && Math.abs(position.y-waypoint.y)<1) path.advance();
                doStuckDetection(position);
            }
        };
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data); data.define(HAMMERING, false); data.define(CLOTHING,"plains");
    }
    public boolean hammering() { return entityData.get(HAMMERING); }
    public String clothing() {return entityData.get(CLOTHING);}
    public static String validClothing(String style) {return style!=null && CLOTHING_STYLES.contains(style)?style:"plains";}
    private void chooseClothing(ServerLevel level,BlockPos site) {
        if(clothingChosen||!level.hasChunk(site.getX()>>4,site.getZ()>>4))return;
        var type=net.minecraft.world.entity.npc.villager.VillagerType.byBiome(level.getBiome(site));
        entityData.set(CLOTHING,validClothing(type.identifier().getPath()));clothingChosen=true;
    }
    void assign(String job, BlockPos target, BlockPos work, boolean working) {
        if (!this.job.equals(job) || !destination.equals(target)) {
            getNavigation().stop(); stuckTicks = 0; lastDistance = Double.MAX_VALUE; nextPathTick = 0;
        }
        this.job = job; destination = target.immutable(); focus = work.immutable();
        if(level() instanceof ServerLevel level)chooseClothing(level,work);
        allowedToWork = working; leaving = false; departureTicks = 0;
        if (working) pausedTicks = 0;
    }
    void depart(BlockPos exit) {
        if (leaving) return;
        leaving = true; destination = exit; allowedToWork = false; departureTicks = 0;
        getNavigation().stop(); nextPathTick = 0; stuckTicks = 0; lastDistance = Double.MAX_VALUE;
        entityData.set(HAMMERING, false);
    }
    boolean needsNewWorkSpot() { return stuckTicks >= 100; }
    boolean avoids(BlockPos spot) { return tickCount < failedUntil && spot.equals(failedDestination); }
    boolean atWorkSpot() {
        return distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(destination)) <= .64;
    }
    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        chooseClothing(level,job.isEmpty()?blockPosition():focus);
        if (job.isEmpty()) { entityData.set(HAMMERING,false); return; } // Manual creative/summoned visitor.
        Boolean active = ConstructionSitePresentation.active(level, job);
        if (Boolean.FALSE.equals(active) && !leaving) depart(ConstructionSitePresentation.exit(level, blockPosition()));
        if (active == null && !leaving) {
            allowedToWork = false; entityData.set(HAMMERING, false); getNavigation().stop(); return;
        }
        if (!leaving) {
            if (!Boolean.TRUE.equals(ConstructionSitePresentation.working(level, job))) {
                allowedToWork = false;
                if (++pausedTicks >= PAUSE_DEPARTURE_TICKS)
                    depart(ConstructionSitePresentation.exit(level, blockPosition()));
            } else pausedTicks = 0;
        }
        if (leaving) {
            departureTicks++;
            if ((departureTicks > 40 && blockPosition().distSqr(destination) < 9
                    || departureTicks > 200) && ConstructionSitePresentation.unseen(level, position().add(0, 1, 0), 16)) {
                discard(); return;
            }
            if (departureTicks % 160 == 0) {
                destination = ConstructionSitePresentation.exit(level, blockPosition());
                getNavigation().stop(); nextPathTick = 0;
            }
        }
        double distance = distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(destination));
        boolean work = !leaving && allowedToWork && atWorkSpot() && !isInWater() && !isPanicking();
        entityData.set(HAMMERING, work);
        if (work) {
            stuckTicks = 0;
            getNavigation().stop();
            getLookControl().setLookAt(focus.getX() + .5, focus.getY() + .5, focus.getZ() + .5, 30, 30);
            if ((tickCount + getId()) % 20 == 0) playSound(SoundEvents.STONE_HIT, .22F, .85F + random.nextFloat() * .3F);
            return;
        }
        if (!leaving && atWorkSpot()) { getNavigation().stop(); return; }
        if (tickCount % 20 == 0) {
            if (distance >= lastDistance - .08) stuckTicks += 20; else stuckTicks = 0;
            lastDistance = distance;
            if (!leaving && needsNewWorkSpot()) {
                failedDestination = destination; failedUntil = tickCount + 240;
            }
        }
        // Keep a useful route instead of throwing it away every second. Replan only after
        // arrival/path failure or sustained lack of progress, paced even for unreachable goals.
        if (tickCount >= nextPathTick && (getNavigation().isDone() || stuckTicks >= 60)) {
            getNavigation().stop(); pathRequests++;
            var path = getNavigation().createPath(destination, 0);
            if (path != null) getNavigation().moveTo(path, .8);
            nextPathTick = tickCount + 40;
        }
        // Retire an unseen stranded visitor, never teleport through watched construction.
        if (stuckTicks > 600 && ConstructionSitePresentation.unseen(level, position().add(0, 1, 0), 16)) discard();
    }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public void remove(Entity.RemovalReason reason) {
        if (reason.shouldDestroy()) ConstructionSitePresentation.removed(this);
        super.remove(reason);
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putString("Worksite", job);
        output.putLong("Destination", destination.asLong()); output.putLong("WorkFocus", focus.asLong());
        output.putBoolean("Leaving", leaving); output.putInt("DepartureTicks", departureTicks);
        if(clothingChosen)output.putString("BuilderClothing",clothing());
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); job = input.getStringOr("Worksite", "");
        destination = BlockPos.of(input.getLongOr("Destination", 0)); focus = BlockPos.of(input.getLongOr("WorkFocus", 0));
        leaving = input.getBooleanOr("Leaving", false); departureTicks = input.getIntOr("DepartureTicks", 0);
        String savedClothing=input.getStringOr("BuilderClothing","");
        clothingChosen=CLOTHING_STYLES.contains(savedClothing);
        entityData.set(CLOTHING,validClothing(savedClothing));
        allowedToWork = false; // A live site must re-authorize animation after loading.
    }
}
