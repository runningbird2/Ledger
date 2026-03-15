package com.github.quiltservertools.ledger.actions

import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component

class BlockBreakActionType : BlockChangeActionType() {
    override val identifier = "block-break"

    override fun getObjectMessage(source: CommandSourceStack): Component = getBlockObjectMessage(source, oldObjectIdentifier)
}
