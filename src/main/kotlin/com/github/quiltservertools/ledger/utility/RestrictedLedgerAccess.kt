package com.github.quiltservertools.ledger.utility

import com.github.quiltservertools.ledger.actions.ActionType
import com.github.quiltservertools.ledger.actions.BlockChangeActionType
import com.github.quiltservertools.ledger.actionutils.ActionSearchParams
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.WorldlyContainerHolder
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.levelgen.structure.BoundingBox
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object RestrictedLedgerAccess {
    const val SEARCH_PERMISSION = "ledger.commands.moderator.search"
    const val ROLLBACK_PERMISSION = "ledger.commands.moderator.rollback"
    const val INSPECT_PERMISSION = "ledger.commands.moderator.inspect"
    const val MODSPAWN_PERMISSION = "ledger.commands.moderator.modspawn"
    val DISALLOWED_PARAMS: Set<String> = setOf("before", "world")
    val DISALLOWED_SOURCE_SUGGESTIONS: Set<String> = setOf("@player")

    private const val ORIGIN_LIMIT = 1000
    private const val ROLLBACK_PREVIEW_MAX_RANGE = 150
    private val ROLLBACK_PREVIEW_MAX_AGE: Duration = Duration.ofDays(3)
    private val OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld")
    private val ORIGIN_SEARCH_BOUNDS =
        BoundingBox(-ORIGIN_LIMIT, -Int.MAX_VALUE, -ORIGIN_LIMIT, ORIGIN_LIMIT, Int.MAX_VALUE, ORIGIN_LIMIT)
    val ALLOWED_ACTIONS = setOf("block-break", "block-place")

    fun restrictSearchParams(source: CommandSourceStack, params: ActionSearchParams): ActionSearchParams {
        requirePlayerWithinOrigin(source)
        return copyWithRestrictions(
            params,
            restrictedBounds = when (params.bounds) {
                null, ActionSearchParams.GLOBAL -> ORIGIN_SEARCH_BOUNDS
                else -> clampToOrigin(params.bounds)
            }
        )
    }

    fun restrictRollbackParams(source: CommandSourceStack, params: ActionSearchParams): ActionSearchParams {
        requirePlayerWithinOrigin(source)

        if (params.bounds == ActionSearchParams.GLOBAL) {
            throw SimpleCommandExceptionType(Component.translatable("error.ledger.restricted.range_global")).create()
        }

        val restrictedBounds = params.bounds?.let {
            val clamped = clampToOrigin(it)
            ensureRollbackRange(clamped)
            clamped
        }
        ensureSpecificRollbackSource(params)

        val cutoff = Instant.now().minus(ROLLBACK_PREVIEW_MAX_AGE)
        val restrictedAfter = when (val after = params.after) {
            null -> cutoff
            else -> maxOf(after, cutoff)
        }

        return copyWithRestrictions(
            params,
            restrictedBounds = restrictedBounds,
            restrictedAfter = restrictedAfter
        )
    }

    fun restrictInspectParams(source: CommandSourceStack, params: ActionSearchParams): ActionSearchParams {
        requirePlayerWithinOrigin(source)
        val restrictedBounds = params.bounds?.let(::clampToOrigin)

        return copyWithRestrictions(
            params,
            restrictedBounds = restrictedBounds,
            restrictedActions = params.actions?.toMutableSet()
        )
    }

    fun requirePlayerWithinOrigin(source: CommandSourceStack): ServerPlayer {
        val player = source.playerOrException
        if (abs(player.blockX) > ORIGIN_LIMIT || abs(player.blockZ) > ORIGIN_LIMIT) {
            throw SimpleCommandExceptionType(Component.translatable("error.ledger.restricted.origin")).create()
        }
        return player
    }

    fun sanitizeRestrictedActions(actions: List<ActionType>): List<ActionType> =
        actions.onEach { action ->
            if (action is BlockChangeActionType && shouldStripBlockEntityData(action)) {
                action.extraData = null
            }
        }

    private fun copyWithRestrictions(
        params: ActionSearchParams,
        restrictedBounds: BoundingBox?,
        restrictedAfter: Instant? = params.after,
        restrictedActions: MutableSet<Negatable<String>>? = restrictActions(params.actions)
    ): ActionSearchParams = params.copy(
        bounds = restrictedBounds,
        after = restrictedAfter,
        actions = restrictedActions,
        objects = params.objects?.toMutableSet(),
        sourceNames = params.sourceNames?.toMutableSet(),
        sourcePlayerIds = params.sourcePlayerIds?.toMutableSet(),
        worlds = mutableSetOf(Negatable.allow(OVERWORLD))
    )

    private fun clampToOrigin(bounds: BoundingBox): BoundingBox {
        val minX = max(bounds.minX(), -ORIGIN_LIMIT)
        val maxX = min(bounds.maxX(), ORIGIN_LIMIT)
        val minZ = max(bounds.minZ(), -ORIGIN_LIMIT)
        val maxZ = min(bounds.maxZ(), ORIGIN_LIMIT)

        if (minX > maxX || minZ > maxZ) {
            throw SimpleCommandExceptionType(Component.translatable("error.ledger.restricted.origin")).create()
        }

        return BoundingBox(minX, bounds.minY(), minZ, maxX, bounds.maxY(), maxZ)
    }

    private fun ensureRollbackRange(bounds: BoundingBox) {
        val range = (max(bounds.xSpan, max(bounds.ySpan, bounds.zSpan)) + 1) / 2
        if (range > ROLLBACK_PREVIEW_MAX_RANGE) {
            throw SimpleCommandExceptionType(
                Component.translatable(
                    "error.ledger.restricted.range_too_big",
                    ROLLBACK_PREVIEW_MAX_RANGE
                )
            ).create()
        }
    }

    private fun ensureSpecificRollbackSource(params: ActionSearchParams) {
        val allowedPlayerSources = params.sourcePlayerIds.orEmpty().filter { it.allowed }
        val allowedSpecialSources = params.sourceNames.orEmpty().filter { it.allowed }
        val hasDeniedSource = params.sourcePlayerIds.orEmpty().any { !it.allowed } ||
                params.sourceNames.orEmpty().any { !it.allowed }
        val targetsAllPlayers = allowedSpecialSources.any { it.property == Sources.PLAYER }

        if (targetsAllPlayers || hasDeniedSource || allowedPlayerSources.size + allowedSpecialSources.size != 1) {
            throw SimpleCommandExceptionType(Component.translatable("error.ledger.restricted.source")).create()
        }
    }

    private fun restrictActions(actions: MutableSet<Negatable<String>>?): MutableSet<Negatable<String>> {
        val positiveRequested = actions.orEmpty().filter { it.allowed }
        val deniedAllowedActions = actions.orEmpty()
            .filterNot { it.allowed }
            .map { it.property }
            .filter(ALLOWED_ACTIONS::contains)
            .toSet()

        if (positiveRequested.any { it.property !in ALLOWED_ACTIONS }) {
            throw SimpleCommandExceptionType(Component.translatable("error.ledger.restricted.action")).create()
        }

        val allowedSubset = if (positiveRequested.isEmpty()) {
            ALLOWED_ACTIONS.toMutableSet()
        } else {
            positiveRequested.mapTo(mutableSetOf()) { it.property }
        }

        allowedSubset.removeAll(deniedAllowedActions)
        if (allowedSubset.isEmpty()) {
            throw SimpleCommandExceptionType(Component.translatable("error.ledger.restricted.action")).create()
        }

        return allowedSubset.mapTo(mutableSetOf()) { Negatable.allow(it) }
    }

    private fun shouldStripBlockEntityData(action: BlockChangeActionType): Boolean =
        isInventoryBlock(action.objectIdentifier) || isInventoryBlock(action.oldObjectIdentifier)

    private fun isInventoryBlock(identifier: Identifier): Boolean {
        val block = BuiltInRegistries.BLOCK.getOptional(identifier).orElse(null) ?: return false
        if (block is WorldlyContainerHolder) {
            return true
        }

        val entityBlock = block as? EntityBlock ?: return false
        val blockEntity = entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState()) ?: return false
        return blockEntity is Container
    }
}
