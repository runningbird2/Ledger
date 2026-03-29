package com.github.quiltservertools.ledger.utility

import com.github.quiltservertools.ledger.logInfo
import com.github.quiltservertools.ledger.logWarn
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ModSpawnManager {
    private const val MODSPAWN_X = 0.5
    private const val MODSPAWN_Y = 150.0
    private const val MODSPAWN_Z = 0.5

    private val previousStates = ConcurrentHashMap<UUID, PreviousState>()

    fun start(player: ServerPlayer): StartResult {
        if (previousStates.containsKey(player.uuid)) {
            return StartResult.ALREADY_ACTIVE
        }

        val previousState = PreviousState(
            world = player.level().dimension().identifier(),
            x = player.x,
            y = player.y,
            z = player.z,
            yRot = player.yRot,
            xRot = player.xRot,
            gameMode = player.gameMode.gameModeForPlayer
        )

        previousStates[player.uuid] = previousState
        logInfo(
            "Stored modspawn return for ${player.scoreboardName} at " +
                    "${previousState.world} ${previousState.x} ${previousState.y} ${previousState.z}"
        )

        val overworld = player.level().server.getLevel(Level.OVERWORLD)
        if (overworld == null) {
            previousStates.remove(player.uuid)
            logWarn("Failed to find the overworld while starting modspawn for ${player.scoreboardName}")
            return StartResult.FAILED
        }

        player.setGameMode(GameType.SPECTATOR)
        val teleported = player.teleportTo(
            overworld,
            MODSPAWN_X,
            MODSPAWN_Y,
            MODSPAWN_Z,
            emptySet(),
            player.yRot,
            player.xRot,
            true
        )

        if (!teleported) {
            player.setGameMode(previousState.gameMode)
            previousStates.remove(player.uuid)
            logWarn("Failed to teleport ${player.scoreboardName} into modspawn")
            return StartResult.FAILED
        }

        return StartResult.STARTED
    }

    fun stop(player: ServerPlayer): StopResult {
        val previousState = previousStates.remove(player.uuid) ?: return StopResult.NOT_ACTIVE
        restore(player, previousState)
        return StopResult.STOPPED
    }

    fun restoreOnDisconnect(player: ServerPlayer) {
        val previousState = previousStates.remove(player.uuid) ?: return
        restore(player, previousState)
    }

    private fun restore(player: ServerPlayer, previousState: PreviousState) {
        val targetWorld = player.level().server.getWorld(previousState.world)
        val restoredLocation = if (targetWorld != null) {
            player.teleportTo(
                targetWorld,
                previousState.x,
                previousState.y,
                previousState.z,
                emptySet(),
                previousState.yRot,
                previousState.xRot,
                true
            )
        } else {
            false
        }

        player.setGameMode(previousState.gameMode)

        if (targetWorld == null || !restoredLocation) {
            logWarn(
                "Failed to fully restore modspawn return for ${player.scoreboardName}; " +
                        "world=${previousState.world} restoredLocation=$restoredLocation"
            )
            return
        }

        logInfo(
            "Restored modspawn return for ${player.scoreboardName} to " +
                    "${previousState.world} ${previousState.x} ${previousState.y} ${previousState.z}"
        )
    }

    private data class PreviousState(
        val world: Identifier,
        val x: Double,
        val y: Double,
        val z: Double,
        val yRot: Float,
        val xRot: Float,
        val gameMode: GameType
    )

    enum class StartResult {
        STARTED,
        ALREADY_ACTIVE,
        FAILED
    }

    enum class StopResult {
        STOPPED,
        NOT_ACTIVE
    }
}
