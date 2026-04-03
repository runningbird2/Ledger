package com.github.quiltservertools.ledger.callbacks

import com.github.quiltservertools.ledger.utility.Sources
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

fun interface EntityPlaceCallback {
    fun place(
        world: Level,
        pos: BlockPos,
        entity: Entity,
        source: String,
        player: Player?
    )

    fun place(world: Level, pos: BlockPos, entity: Entity, player: Player) =
        place(world, pos, entity, Sources.PLAYER, player)

    fun place(world: Level, pos: BlockPos, entity: Entity, source: String) =
        place(world, pos, entity, source, null)

    companion object {
        @JvmField
        val EVENT: Event<EntityPlaceCallback> =
            EventFactory.createArrayBacked(EntityPlaceCallback::class.java) { listeners ->
                EntityPlaceCallback { world, pos, entity, source, player ->
                    for (listener in listeners) {
                        listener.place(world, pos, entity, source, player)
                    }
                }
            }
    }
}
