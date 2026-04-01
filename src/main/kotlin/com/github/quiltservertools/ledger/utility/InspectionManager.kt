package com.github.quiltservertools.ledger.utility

import com.github.quiltservertools.ledger.Ledger
import com.github.quiltservertools.ledger.actions.ActionType
import com.github.quiltservertools.ledger.actionutils.ActionSearchParams
import com.github.quiltservertools.ledger.actionutils.SearchResults
import com.github.quiltservertools.ledger.database.DatabaseManager
import com.mojang.brigadier.exceptions.CommandSyntaxException
import kotlinx.coroutines.launch
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BedPart
import net.minecraft.world.level.block.state.properties.ChestType
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.level.levelgen.structure.BoundingBox
import java.util.*

enum class InspectMode {
    FULL,
    RESTRICTED
}

private val inspectingUsers = HashMap<UUID, InspectMode>()

fun Player.inspectMode(): InspectMode? = inspectingUsers[this.uuid]

fun Player.isInspecting(mode: InspectMode? = null): Boolean =
    if (mode == null) inspectMode() != null else inspectMode() == mode

fun Player.inspectOn(mode: InspectMode = InspectMode.FULL): Int {
    inspectingUsers[this.uuid] = mode
    this.displayClientMessage(
        Component.translatable(
            "text.ledger.inspect.toggle",
            "text.ledger.inspect.on".translate().withStyle(ChatFormatting.GREEN)
        ).setStyle(TextColorPallet.secondary),
        false
    )

    return 1
}

fun Player.inspectOff(): Int {
    inspectingUsers.remove(this.uuid)
    this.displayClientMessage(
        Component.translatable(
            "text.ledger.inspect.toggle",
            "text.ledger.inspect.off".translate().withStyle(ChatFormatting.RED)
        ).setStyle(TextColorPallet.secondary),
        false
    )

    return 1
}

fun CommandSourceStack.inspectBlock(pos: BlockPos) {
    val params = buildInspectParams(this, pos)
    inspectBlock(
        pos,
        params,
        actionTransformer = { it }
    )
}

fun CommandSourceStack.inspectBlockRestricted(pos: BlockPos) {
    val restrictedParams = try {
        RestrictedLedgerAccess.restrictInspectParams(this, buildInspectParams(this, pos))
    } catch (exception: CommandSyntaxException) {
        sendFailure(exception.toComponent())
        return
    }

    inspectBlock(
        pos,
        restrictedParams,
        pageCommandFactory = { page -> "/search page $page" },
        actionTransformer = RestrictedLedgerAccess::sanitizeRestrictedActions
    )
}

private fun CommandSourceStack.inspectBlock(
    pos: BlockPos,
    params: ActionSearchParams,
    pageCommandFactory: (Int) -> String = { "/lg pg $it" },
    actionTransformer: (List<ActionType>) -> List<ActionType>
) {
    val source = this

    Ledger.launch {
        Ledger.searchCache[source.textName] = params

        MessageUtils.warnBusy(source)
        val results = DatabaseManager.searchActions(params, 1)
        val transformedResults = results.copy(actions = actionTransformer(results.actions))

        if (transformedResults.actions.isEmpty()) {
            source.sendFailure(Component.translatable("error.ledger.command.no_results"))
            return@launch
        }

        MessageUtils.sendSearchResults(
            source,
            transformedResults,
            Component.translatable(
                "text.ledger.header.search.pos",
                "${pos.x} ${pos.y} ${pos.z}".literal()
            ).setStyle(TextColorPallet.primary),
            pageCommandFactory,
            actionTransformer
        )
    }
}

private fun buildInspectParams(source: CommandSourceStack, pos: BlockPos): ActionSearchParams {
    var area = BoundingBox(pos)

    val state = source.level.getBlockState(pos)
    if (state.block is ChestBlock) {
        getOtherChestSide(state, pos)?.let {
            area = BoundingBox.fromCorners(pos, it)
        }
    } else if (state.block is DoorBlock) {
        getOtherDoorHalf(state, pos).let {
            area = BoundingBox.fromCorners(pos, it)
        }
    } else if (state.block is BedBlock) {
        getOtherBedPart(state, pos).let {
            area = BoundingBox.fromCorners(pos, it)
        }
    }

    return ActionSearchParams.build {
        bounds = area
        worlds = mutableSetOf(Negatable.allow(source.level.dimension().identifier()))
    }
}

fun getOtherChestSide(state: BlockState, pos: BlockPos): BlockPos? {
    val type = state.getValue(ChestBlock.TYPE)
    return if (type != ChestType.SINGLE) {
        // We now need to query other container results in the same chest
        val facing = state.getValue(ChestBlock.FACING)
        if (type == ChestType.RIGHT) {
            // Chest is right, so left as you look at it
            pos.relative(facing.getCounterClockWise(Direction.Axis.Y))
        } else {
            pos.relative(facing.getClockWise(Direction.Axis.Y))
        }
    } else {
        null
    }
}

private fun getOtherDoorHalf(state: BlockState, pos: BlockPos): BlockPos {
    val half = state.getValue(DoorBlock.HALF)
    return if (half == DoubleBlockHalf.LOWER) {
        pos.relative(Direction.UP)
    } else {
        pos.relative(Direction.DOWN)
    }
}

private fun getOtherBedPart(state: BlockState, pos: BlockPos): BlockPos {
    val part = state.getValue(BedBlock.PART)
    val direction = state.getValue(BedBlock.FACING)
    return if (part == BedPart.FOOT) {
        pos.relative(direction)
    } else {
        pos.relative(direction.opposite)
    }
}

suspend fun ServerPlayer.getInspectResults(pos: BlockPos): SearchResults {
    val source = this.createCommandSourceStack()
    val params = buildInspectParams(source, pos)

    Ledger.searchCache[source.textName] = params
    MessageUtils.warnBusy(source)
    return DatabaseManager.searchActions(params, 1)
}

private fun CommandSyntaxException.toComponent(): Component {
    val rawMessage = this.rawMessage
    return if (rawMessage is Component) {
        rawMessage
    } else {
        Component.literal(rawMessage.string)
    }
}
