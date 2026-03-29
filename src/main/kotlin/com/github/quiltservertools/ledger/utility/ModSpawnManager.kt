package com.github.quiltservertools.ledger.utility

import com.github.quiltservertools.ledger.logInfo
import com.github.quiltservertools.ledger.logWarn
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level

object ModSpawnManager {
    private const val MODSPAWN_X = 0.5
    private const val MODSPAWN_Y = 150.0
    private const val MODSPAWN_Z = 0.5

    fun start(player: ServerPlayer): StartResult {
        val previousStates = getSavedData(player.level().server) ?: return StartResult.FAILED
        if (previousStates.contains(player.uuid)) {
            return StartResult.ALREADY_ACTIVE
        }

        val previousState = ModSpawnState(
            world = player.level().dimension().identifier(),
            x = player.x,
            y = player.y,
            z = player.z,
            yRot = player.yRot,
            xRot = player.xRot,
            gameMode = player.gameMode.gameModeForPlayer
        )

        previousStates.put(player.uuid, previousState)
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
        val previousStates = getSavedData(player.level().server) ?: return StopResult.FAILED
        val previousState = previousStates.get(player.uuid) ?: return StopResult.NOT_ACTIVE
        return if (restore(player, previousState)) {
            previousStates.remove(player.uuid)
            StopResult.STOPPED
        } else {
            StopResult.FAILED
        }
    }

    fun restoreOnDisconnect(player: ServerPlayer) {
        val previousStates = getSavedData(player.level().server) ?: return
        val previousState = previousStates.get(player.uuid) ?: return
        if (restore(player, previousState)) {
            previousStates.remove(player.uuid)
        }
    }

    private fun restore(player: ServerPlayer, previousState: ModSpawnState): Boolean {
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

        if (targetWorld == null || !restoredLocation) {
            logWarn(
                "Failed to fully restore modspawn return for ${player.scoreboardName}; " +
                        "world=${previousState.world} restoredLocation=$restoredLocation"
            )
            return false
        }

        player.setGameMode(previousState.gameMode)

        logInfo(
            "Restored modspawn return for ${player.scoreboardName} to " +
                    "${previousState.world} ${previousState.x} ${previousState.y} ${previousState.z}"
        )
        return true
    }

    private fun getSavedData(server: MinecraftServer): ModSpawnSavedData? =
        server.getLevel(Level.OVERWORLD)?.dataStorage?.computeIfAbsent(ModSpawnSavedData.TYPE)

    enum class StartResult {
        STARTED,
        ALREADY_ACTIVE,
        FAILED
    }

    enum class StopResult {
        STOPPED,
        NOT_ACTIVE,
        FAILED
    }
}
