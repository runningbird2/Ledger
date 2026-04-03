package com.github.quiltservertools.ledger.utility;

import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.Deque;

public final class EntityPlacementTracker {
    private static final ThreadLocal<Deque<PlacementContext>> CONTEXTS = ThreadLocal.withInitial(ArrayDeque::new);

    private EntityPlacementTracker() {
    }

    public static void begin(Player player) {
        CONTEXTS.get().addLast(new PlacementContext(new PlacementSource(player)));
    }

    public static void end() {
        Deque<PlacementContext> stack = CONTEXTS.get();
        if (!stack.isEmpty()) {
            stack.removeLast();
        }
        if (stack.isEmpty()) {
            CONTEXTS.remove();
        }
    }

    public static PlacementSource consume() {
        PlacementContext context = CONTEXTS.get().peekLast();
        if (context == null || context.consumed) {
            return null;
        }
        context.consumed = true;
        return context.source;
    }

    public record PlacementSource(Player player) {
    }

    private static final class PlacementContext {
        private final PlacementSource source;
        private boolean consumed;

        private PlacementContext(PlacementSource source) {
            this.source = source;
        }
    }
}
