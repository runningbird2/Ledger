package com.github.quiltservertools.ledger.mixin;

import com.github.quiltservertools.ledger.utility.EntityPlacementTracker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BoatItem.class)
public abstract class BoatItemMixin {
    @WrapOperation(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
            )
    )
    private boolean ledgerTrackBoatPlacement(
            Level instance,
            Entity entity,
            Operation<Boolean> original,
            Level world,
            Player player,
            InteractionHand hand
    ) {
        EntityPlacementTracker.begin(player);
        try {
            return original.call(instance, entity);
        } finally {
            EntityPlacementTracker.end();
        }
    }
}
