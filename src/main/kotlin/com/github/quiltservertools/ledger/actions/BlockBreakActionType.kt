package com.github.quiltservertools.ledger.actions

import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStack

class BlockBreakActionType : BlockChangeActionType(), LoggedItemProvider {
    override val identifier = "block-break"

    override fun getLoggedItem(server: MinecraftServer): ItemStack = getLoggedBlockItem(oldObjectIdentifier, server)

    override fun getObjectMessage(source: CommandSourceStack): Component = getBlockObjectMessage(source, oldObjectIdentifier)
}
