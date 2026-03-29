package com.github.quiltservertools.ledger.utility

import com.github.quiltservertools.ledger.logWarn
import com.mojang.serialization.Codec
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.Identifier
import net.minecraft.world.level.GameType
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.SavedDataType
import net.minecraft.util.datafix.DataFixTypes
import java.util.UUID

class ModSpawnSavedData private constructor(
    private val previousStates: MutableMap<UUID, ModSpawnState>
) : SavedData() {
    constructor() : this(mutableMapOf())

    fun get(uuid: UUID): ModSpawnState? = previousStates[uuid]

    fun put(uuid: UUID, state: ModSpawnState) {
        previousStates[uuid] = state
        setDirty()
    }

    fun remove(uuid: UUID): ModSpawnState? {
        val removed = previousStates.remove(uuid)
        if (removed != null) setDirty()
        return removed
    }

    fun contains(uuid: UUID): Boolean = previousStates.containsKey(uuid)

    private fun toTag(): CompoundTag {
        val root = CompoundTag()
        val entries = ListTag()

        previousStates.forEach { (uuid, state) ->
            val entry = CompoundTag()
            entry.putString("uuid", uuid.toString())
            entry.putString("world", state.world.toString())
            entry.putDouble("x", state.x)
            entry.putDouble("y", state.y)
            entry.putDouble("z", state.z)
            entry.putFloat("yRot", state.yRot)
            entry.putFloat("xRot", state.xRot)
            entry.putString("gameMode", state.gameMode.name)
            entries.addAndUnwrap(entry)
        }

        root.put("entries", entries)
        return root
    }

    companion object {
        private val CODEC: Codec<ModSpawnSavedData> = CompoundTag.CODEC.xmap(::fromTag, ModSpawnSavedData::toTag)
        val TYPE: SavedDataType<ModSpawnSavedData> = SavedDataType(
            "ledger_modspawn",
            ::ModSpawnSavedData,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
        )

        private fun fromTag(root: CompoundTag): ModSpawnSavedData {
            val states = mutableMapOf<UUID, ModSpawnState>()
            val entries = root.getList("entries").orElse(ListTag())

            entries.compoundStream().forEach { entry ->
                try {
                    val uuid = java.util.UUID.fromString(entry.getStringOr("uuid", ""))
                    val world = Identifier.parse(entry.getStringOr("world", "minecraft:overworld"))
                    val gameMode = GameType.valueOf(entry.getStringOr("gameMode", GameType.SURVIVAL.name))

                    states[uuid] = ModSpawnState(
                        world = world,
                        x = entry.getDoubleOr("x", 0.5),
                        y = entry.getDoubleOr("y", 150.0),
                        z = entry.getDoubleOr("z", 0.5),
                        yRot = entry.getFloatOr("yRot", 0f),
                        xRot = entry.getFloatOr("xRot", 0f),
                        gameMode = gameMode
                    )
                } catch (exception: IllegalArgumentException) {
                    logWarn("Failed to read a persisted modspawn state entry", exception)
                }
            }

            return ModSpawnSavedData(states)
        }
    }
}

data class ModSpawnState(
    val world: Identifier,
    val x: Double,
    val y: Double,
    val z: Double,
    val yRot: Float,
    val xRot: Float,
    val gameMode: GameType
)
