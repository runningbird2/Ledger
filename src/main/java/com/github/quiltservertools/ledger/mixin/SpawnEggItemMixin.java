package com.github.quiltservertools.ledger.mixin;

import com.github.quiltservertools.ledger.utility.EntityPlacementTracker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SpawnEggItem.class)
public abstract class SpawnEggItemMixin {
    @WrapOperation(
            method = "useOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/SpawnEggItem;spawnMob(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ZZ)Lnet/minecraft/world/InteractionResult;"
            )
    )
    private InteractionResult ledgerTrackSpawnEggUseOn(
            SpawnEggItem instance,
            LivingEntity user,
            ItemStack stack,
            Level world,
            BlockPos pos,
            boolean alignPosition,
            boolean invertY,
            Operation<InteractionResult> original,
            UseOnContext context
    ) {
        EntityPlacementTracker.begin(user instanceof Player player ? player : context.getPlayer());
        try {
            return original.call(instance, user, stack, world, pos, alignPosition, invertY);
        } finally {
            EntityPlacementTracker.end();
        }
    }

    @WrapOperation(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/SpawnEggItem;spawnMob(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ZZ)Lnet/minecraft/world/InteractionResult;"
            )
    )
    private InteractionResult ledgerTrackSpawnEggUse(
            SpawnEggItem instance,
            LivingEntity user,
            ItemStack stack,
            Level spawnWorld,
            BlockPos pos,
            boolean alignPosition,
            boolean invertY,
            Operation<InteractionResult> original,
            Level world,
            Player player,
            InteractionHand hand
    ) {
        EntityPlacementTracker.begin(player);
        try {
            return original.call(instance, user, stack, spawnWorld, pos, alignPosition, invertY);
        } finally {
            EntityPlacementTracker.end();
        }
    }

    @WrapOperation(
            method = "spawnOffspringFromSpawnEgg",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"
            )
    )
    private void ledgerTrackSpawnEggOffspringPlacement(
            ServerLevel world,
            net.minecraft.world.entity.Entity entity,
            Operation<Void> original,
            Player player,
            Mob mob,
            EntityType<? extends Mob> entityType,
            ServerLevel serverLevel,
            Vec3 pos,
            ItemStack stack
    ) {
        EntityPlacementTracker.begin(player);
        try {
            original.call(world, entity);
        } finally {
            EntityPlacementTracker.end();
        }
    }
}
