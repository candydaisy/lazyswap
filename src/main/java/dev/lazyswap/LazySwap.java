package dev.lazyswap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LazySwap implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("lazyswap");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static class Config {
        int secondarySlot = 1;
        long switchDelayMs = 0; // 0 = same-tick pre-attack switch, >0 = post-hit delay
        boolean autoReturn = true;
    }

    static Config config = new Config();
    static boolean enabled;
    private static long switchAt = -1; // wall-clock ms of a pending post-hit switch, -1 = none
    private static boolean awaitingReturn;
    private static int returnSlot; // slot held when the attack started

    @Override
    public void onInitializeClient() {
        config = loadConfig(FabricLoader.getInstance().getConfigDir().resolve("lazyswap.json"));
        KeyMapping toggle = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.lazyswap.toggle", InputConstants.KEY_V, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (toggle.consumeClick()) {
                enabled = !enabled;
                switchAt = -1;
                awaitingReturn = false;
                if (mc.player != null) mc.player.sendOverlayMessage(Component.literal("LazySwap: " + (enabled ? "ON" : "OFF")));
            }
            tick(mc);
        });
    }

    /** Called at the head of MultiPlayerGameMode#attack, before the attack packet is built. */
    public static void onAttack(Player player) {
        int slot = player.getInventory().getSelectedSlot();
        if (!enabled || slot == config.secondarySlot) return;
        returnSlot = slot;
        if (config.switchDelayMs == 0) {
            // attack() calls ensureHasSentCarriedItem() next, so the slot packet goes out right before the attack packet.
            select(player, returnSlot, config.secondarySlot);
        } else {
            switchAt = System.currentTimeMillis() + config.switchDelayMs;
        }
    }

    private static void tick(Minecraft mc) {
        Player player = mc.player;
        if (player == null) {
            switchAt = -1;
            awaitingReturn = false;
            return;
        }
        if (switchAt >= 0 && System.currentTimeMillis() >= switchAt) {
            switchAt = -1;
            select(player, returnSlot, config.secondarySlot);
        }
        // Return once the swing cooldown is full again; the game's tick sends the slot packet.
        if (awaitingReturn && player.getAttackStrengthScale(0f) >= 1f) {
            awaitingReturn = false;
            if (player.getInventory().getSelectedSlot() == config.secondarySlot) player.getInventory().setSelectedSlot(returnSlot);
        }
    }

    // Only moves if the player is still on `from`, so a manual scroll in between wins.
    private static void select(Player player, int from, int to) {
        if (player.getInventory().getSelectedSlot() != from) return;
        player.getInventory().setSelectedSlot(to);
        awaitingReturn = config.autoReturn;
    }

    static Config loadConfig(Path path) {
        Config c = new Config();
        try {
            if (Files.exists(path)) {
                Config read = GSON.fromJson(Files.readString(path), Config.class);
                if (read != null) c = read;
            } else {
                Files.writeString(path, GSON.toJson(c));
            }
        } catch (IOException | JsonParseException e) {
            // Keep the user's broken file untouched; run on defaults.
            LOG.error("Failed to load {}, using defaults", path, e);
            return new Config();
        }
        c.secondarySlot = Math.clamp(c.secondarySlot, 0, 8);
        c.switchDelayMs = Math.max(0, c.switchDelayMs);
        return c;
    }
}
