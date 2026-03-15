package com.github.quiltservertools.ledger.utility

import com.mojang.logging.LogUtils
import com.mojang.serialization.Dynamic
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderGetter
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.Tag
import net.minecraft.nbt.TagParser
import net.minecraft.resources.Identifier
import net.minecraft.util.ProblemReporter
import net.minecraft.util.datafix.DataFixers
import net.minecraft.util.datafix.fixes.References
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.TypedEntityData
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.level.storage.TagValueOutput

const val ITEM_NBT_DATA_VERSION = 3817
const val ITEM_COMPONENTS_DATA_VERSION = 3825

const val PROPERTIES = "Properties" // BlockState
const val COUNT_PRE_1_20_5 = "Count" // ItemStack
const val COUNT = "count" // ItemStack
const val UUID = "UUID" // Entity
private const val ID = "id"
private const val PAGES = "pages"
private const val FILTERED_PAGES = "filtered_pages"
private const val WRITTEN_BOOK = "minecraft:written_book"
private const val WRITABLE_BOOK = "minecraft:writable_book"
private const val WRITTEN_BOOK_CONTENT = "minecraft:written_book_content"
private const val WRITABLE_BOOK_CONTENT = "minecraft:writable_book_content"

val LOGGER = LogUtils.getLogger()

object NbtUtils {
    fun blockStateToProperties(state: BlockState): CompoundTag? {
        val stateTag = NbtUtils.writeBlockState(state)
        if (state.block.defaultBlockState() == state) return null // Don't store default block state
        return stateTag.getCompound(PROPERTIES).orElse(null)
    }

    fun blockStateFromProperties(
        tag: CompoundTag,
        name: Identifier,
        blockLookup: HolderGetter<Block>
    ): BlockState {
        val stateTag = CompoundTag()
        stateTag.putString("Name", name.toString())
        stateTag.put(PROPERTIES, tag)
        return NbtUtils.readBlockState(blockLookup, stateTag)
    }

    fun itemFromProperties(tag: String?, name: Identifier, registries: HolderLookup.Provider): ItemStack {
        val extraDataTag = TagParser.parseCompoundFully(tag ?: "{}")
        var itemTag = extraDataTag
        if (!extraDataTag.contains(COUNT)) {
            // 1.20.4 and lower (need data fixing)
            itemTag.putString("id", name.toString())
            if (!itemTag.contains(COUNT_PRE_1_20_5)) {
                // Ledger ItemStack in 1.20.4 and earlier had "Count" omitted if it was 1
                itemTag.putByte(COUNT_PRE_1_20_5, 1)
            }
            itemTag = DataFixers.getDataFixer().update(
                References.ITEM_STACK,
                Dynamic(NbtOps.INSTANCE, itemTag), ITEM_NBT_DATA_VERSION, ITEM_COMPONENTS_DATA_VERSION
            ).cast(NbtOps.INSTANCE) as CompoundTag
        }
        ProblemReporter.ScopedCollector({ "ledger:itemstack@$name" }, LOGGER).use {
            val readView = TagValueInput.create(it, registries, itemTag)
            return readView.read(ItemStack.MAP_CODEC).orElse(ItemStack.EMPTY)
        }
    }

    fun blockItemFromProperties(tag: String?, name: Identifier, registries: HolderLookup.Provider): ItemStack {
        val block = BuiltInRegistries.BLOCK.getOptional(name)
        if (block.isEmpty) {
            return ItemStack.EMPTY
        }

        val item = block.get().asItem()
        if (item == Items.AIR) {
            return ItemStack.EMPTY
        }

        val stack = item.defaultInstance
        if (tag.isNullOrBlank()) {
            return stack
        }

        val entityBlock = block.get() as? EntityBlock ?: return stack
        val blockEntity = entityBlock.newBlockEntity(BlockPos.ZERO, block.get().defaultBlockState()) ?: return stack

        val blockEntityTag = try {
            TagParser.parseCompoundFully(tag)
        } catch (_: CommandSyntaxException) {
            return stack
        }

        TypedEntityData.of(blockEntity.type, blockEntityTag).loadInto(blockEntity, registries)
        stack.applyComponents(blockEntity.collectComponents())
        return stack
    }

    fun BlockEntity.createNbt(registries: HolderLookup.Provider): CompoundTag {
        ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)
            .use {
                val writeView = TagValueOutput.createWithContext(it, registries)
                this.saveWithId(writeView)
                return redactBookPages(writeView.buildResult())
            }
    }

    fun Entity.createNbt(): CompoundTag {
        ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)
            .use {
                val writeView = TagValueOutput.createWithContext(it, this.registryAccess())
                this.saveWithoutId(writeView)
                return redactBookPages(writeView.buildResult())
            }
    }

    fun ItemStack.createNbt(registries: HolderLookup.Provider): CompoundTag {
        ProblemReporter.ScopedCollector({ "ledger:itemstack@${this.item}" }, LOGGER)
            .use {
                val writeView = TagValueOutput.createWithContext(it, registries)
                writeView.store(ItemStack.MAP_CODEC, this)
                return redactBookPages(writeView.buildResult())
            }
    }

    // Ledger intentionally strips book text from persisted SNBT to avoid oversized exploit payloads.
    fun redactBookPages(root: CompoundTag): CompoundTag {
        redactBookContent(root as Tag)
        return root
    }

    private fun redactBookContent(tag: Tag) {
        when (tag) {
            is CompoundTag -> redactBookContentCompound(tag)
            is ListTag -> redactBookContentList(tag)
            else -> Unit
        }
    }

    private fun redactBookContentCompound(tag: CompoundTag) {
        redactModernBookContent(tag)
        redactLegacyBookContent(tag)

        for (key in tag.keySet().toList()) {
            val child = tag.get(key) ?: continue
            redactBookContent(child)
        }
    }

    private fun redactBookContentList(tag: ListTag) {
        for (index in 0 until tag.size) {
            redactBookContent(tag.get(index))
        }
    }

    private fun redactModernBookContent(tag: CompoundTag) {
        tag.getCompound(WRITTEN_BOOK_CONTENT).ifPresent {
            it.put(PAGES, ListTag())
        }
        tag.getCompound(WRITABLE_BOOK_CONTENT).ifPresent {
            it.put(PAGES, ListTag())
        }
    }

    private fun redactLegacyBookContent(tag: CompoundTag) {
        when (tag.getStringOr(ID, "")) {
            WRITTEN_BOOK, WRITABLE_BOOK -> {
                if (tag.contains(PAGES)) {
                    tag.put(PAGES, ListTag())
                }
                tag.remove(FILTERED_PAGES)
            }
        }
    }
}
