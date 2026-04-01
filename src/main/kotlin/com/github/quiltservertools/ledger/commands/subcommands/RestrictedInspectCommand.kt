package com.github.quiltservertools.ledger.commands.subcommands

import com.github.quiltservertools.ledger.commands.BuildableCommand
import com.github.quiltservertools.ledger.commands.CommandConsts
import com.github.quiltservertools.ledger.utility.Context
import com.github.quiltservertools.ledger.utility.InspectMode
import com.github.quiltservertools.ledger.utility.LiteralNode
import com.github.quiltservertools.ledger.utility.RestrictedLedgerAccess
import com.github.quiltservertools.ledger.utility.inspectBlockRestricted
import com.github.quiltservertools.ledger.utility.inspectOff
import com.github.quiltservertools.ledger.utility.inspectOn
import com.github.quiltservertools.ledger.utility.isInspecting
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.arguments.coordinates.BlockPosArgument
import net.minecraft.core.BlockPos

object RestrictedInspectCommand : BuildableCommand {
    override fun build(): LiteralNode =
        literal("inspect")
            .requires {
                it.entity is net.minecraft.server.level.ServerPlayer &&
                        Permissions.check(
                            it.playerOrException,
                            RestrictedLedgerAccess.INSPECT_PERMISSION,
                            CommandConsts.PERMISSION_LEVEL
                        )
            }
            .executes { toggleInspect(it) }
            .then(
                literal("on")
                    .executes { it.source.playerOrException.inspectOn(InspectMode.RESTRICTED) }
            )
            .then(
                literal("off")
                    .executes { it.source.playerOrException.inspectOff() }
            )
            .then(
                argument("pos", BlockPosArgument.blockPos())
                    .executes { inspectBlock(it, BlockPosArgument.getBlockPos(it, "pos")) }
            )
            .build()

    private fun toggleInspect(context: Context): Int {
        val player = context.source.playerOrException

        return if (player.isInspecting(InspectMode.RESTRICTED)) {
            player.inspectOff()
        } else {
            player.inspectOn(InspectMode.RESTRICTED)
        }
    }

    private fun inspectBlock(context: Context, pos: BlockPos): Int {
        context.source.inspectBlockRestricted(pos)
        return 1
    }
}
