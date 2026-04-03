package com.github.quiltservertools.ledger.mixin;

import com.github.quiltservertools.ledger.callbacks.EntityPlaceCallback;
import com.github.quiltservertools.ledger.utility.EntityPlacementTracker;
import com.github.quiltservertools.ledger.utility.Sources;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "addFreshEntity", at = @At("RETURN"))
    private void ledgerLogEntityPlacement(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }

        EntityPlacementTracker.PlacementSource source = EntityPlacementTracker.consume();
        if (source == null) {
            return;
        }

        Player player = source.player();
        EntityPlaceCallback.EVENT.invoker().place(
                (Level) (Object) this,
                entity.blockPosition(),
                entity,
                player == null ? Sources.REDSTONE : Sources.PLAYER,
                player
        );
    }
}
