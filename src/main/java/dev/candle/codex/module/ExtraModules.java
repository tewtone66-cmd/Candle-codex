package dev.candle.codex.module;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Extra built-in modules (part 4). No Mixins, no automation: they only read game state or latch a key the player pressed. */
public final class ExtraModules {
    private ExtraModules() {}

    /**
     * Shared hit detection for Combo Counter and Reach Display, ticked once per game tick by ModuleManager
     * and only while one of the two modules is enabled. Uses the same signal as Hit Color (hurtTime of the target).
     */
    static final class HitTracker {
        private static int combo;
        private static long lastHit;
        private static double reach = -1.0;
        private static long reachTime;
        private static int targetId = -1, targetHurt, selfHurt;

        private HitTracker() {}

        static void tick(Minecraft mc) {
            var p = mc.player;
            if (p == null) return;
            long now = System.currentTimeMillis();
            if (p.hurtTime > selfHurt) combo = 0;
            selfHurt = p.hurtTime;
            if (mc.crosshairPickEntity instanceof LivingEntity le && le != p) {
                if (le.getId() != targetId) {
                    targetId = le.getId();
                    targetHurt = le.hurtTime;
                } else {
                    if (le.hurtTime > targetHurt) {
                        combo++;
                        lastHit = now;
                        var eye = p.getEyePosition();
                        var b = le.getBoundingBox();
                        double dx = Math.max(Math.max(b.minX - eye.x, 0.0), eye.x - b.maxX);
                        double dy = Math.max(Math.max(b.minY - eye.y, 0.0), eye.y - b.maxY);
                        double dz = Math.max(Math.max(b.minZ - eye.z, 0.0), eye.z - b.maxZ);
                        reach = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        reachTime = now;
                    }
                    targetHurt = le.hurtTime;
                }
            } else {
                targetId = -1;
                targetHurt = 0;
            }
            if (combo > 0 && now - lastHit > 3000) combo = 0;
        }

        static int combo() {
            return combo;
        }

        /** Distance of the last hit in blocks, or -1 when there is none or it is older than 3 seconds. */
        static double reach() {
            return reach >= 0.0 && System.currentTimeMillis() - reachTime <= 3000 ? reach : -1.0;
        }
    }

    private static int count(Minecraft mc, net.minecraft.world.item.Item item) {
        if (mc.player == null) return 0;
        int n = 0;
        var inv = mc.player.getInventory();
        int size = Math.min(inv.getContainerSize(), 36);
        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        ItemStack off = mc.player.getOffhandItem();
        if (off.is(item)) n += off.getCount();
        return n;
    }

    // ------------------------------------------------------------------ gameplay

    /**
     * Tap the sneak key once to keep sneaking, tap again to stop. It only watches the real key state:
     * a press latches sneak on, the next press followed by a release latches it off.
     */
    public static final class ToggleSneak extends Module {
        private boolean latched, holding, hadScreen;

        public ToggleSneak() {
            super("togglesneak", "Toggle Sneak", "Tap sneak once to keep sneaking, tap again to stop.", "Utility");
        }

        @Override
        public void onTick(Minecraft mc) {
            if (mc.player == null) {
                latched = false;
                holding = false;
                return;
            }
            // with the vanilla "toggle sneak" option on, the game already does this
            if (mc.options.toggleCrouch().get()) return;
            var key = mc.options.keyShift;
            if (mc.screen != null) {
                hadScreen = true;
                return;
            }
            if (hadScreen) {
                hadScreen = false;
                if (latched) {
                    holding = false;
                    key.setDown(true);
                }
                return;
            }
            if (!latched) {
                if (key.isDown()) {
                    latched = true;
                    holding = true;
                }
                return;
            }
            if (holding) {
                // first press still held; when it is released, stay latched on
                if (!key.isDown()) holding = false;
                key.setDown(true);
            } else if (!key.isDown()) {
                // the key was pressed again and released: stop sneaking
                latched = false;
            } else {
                key.setDown(true);
            }
        }

        @Override
        public void onDisable() {
            if (latched) Minecraft.getInstance().options.keyShift.setDown(false);
            latched = false;
            holding = false;
            hadScreen = false;
        }
    }

    // ------------------------------------------------------------------ HUD pills

    public static final class Combo extends TextHud {
        public Combo() {
            super("combo", "Combo Counter", "Consecutive hits on a target without taking damage.", "PvP", "COMBO", 0.33f, 0.02f, 100);
        }

        @Override
        protected boolean defaultEnabled() {
            return false;
        }

        @Override
        protected String compute(Minecraft mc) {
            return Integer.toString(HitTracker.combo());
        }
    }

    public static final class Reach extends TextHud {
        public Reach() {
            super("reach", "Reach Display", "Distance of your last hit in blocks.", "PvP", "REACH", 0.33f, 0.07f, 100);
        }

        @Override
        protected boolean defaultEnabled() {
            return false;
        }

        @Override
        protected String compute(Minecraft mc) {
            double r = HitTracker.reach();
            return r < 0.0 ? "-" : String.format(Locale.ROOT, "%.2f", r);
        }
    }

    public static final class Food extends TextHud {
        public Food() {
            super("food", "Food & Saturation", "Hunger level and saturation.", "HUD", "FOOD", 0.33f, 0.12f, 250);
        }

        @Override
        protected boolean defaultEnabled() {
            return false;
        }

        @Override
        protected String compute(Minecraft mc) {
            if (mc.player == null) return "-";
            var fd = mc.player.getFoodData();
            return fd.getFoodLevel() + " | " + String.format(Locale.ROOT, "%.1f", fd.getSaturationLevel());
        }
    }

    public static final class ItemCount extends TextHud {
        public ItemCount() {
            super("itemcount", "Item Counter", "How many of the item in your hand you carry.", "HUD", "ITEMS", 0.33f, 0.17f, 250);
        }

        @Override
        protected boolean defaultEnabled() {
            return false;
        }

        @Override
        protected String compute(Minecraft mc) {
            if (mc.player == null) return "-";
            ItemStack held = mc.player.getMainHandItem();
            if (held.isEmpty()) return "-";
            return Integer.toString(count(mc, held.getItem()));
        }
    }
}
