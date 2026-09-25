package com.chedidandrew.emeraldstandard.minecraft.mixin;
import com.chedidandrew.emeraldstandard.minecraft.NewsRuntime;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractContainerMenu.class)
public abstract class PlayerNewsContainerMixin {
    @Unique private List<NewsRuntime.ContainerBefore> emerald$newsBefore=List.of();
    @Inject(method="clicked",at=@At("HEAD"))
    private void emerald$before(int slot,int button,ContainerInput input,Player player,CallbackInfo ci) {
        emerald$newsBefore=NewsRuntime.beforeClick((AbstractContainerMenu)(Object)this,player);
    }
    @Inject(method="clicked",at=@At("RETURN"))
    private void emerald$after(int slot,int button,ContainerInput input,Player player,CallbackInfo ci) {
        var before=emerald$newsBefore;emerald$newsBefore=List.of();NewsRuntime.afterClick(player,before);
    }
}
