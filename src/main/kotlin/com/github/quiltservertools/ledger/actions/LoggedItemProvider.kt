package com.github.quiltservertools.ledger.actions

import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStack

interface LoggedItemProvider {
    fun getLoggedItem(server: MinecraftServer): ItemStack
}
