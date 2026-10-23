package dev.lazyswap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class LazySwap implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("lazyswap");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Attacking from any slot in {@code from} swaps to the next slot in {@code to}, cycling per hit. Slots are 0-8. */
    static class Rule {
        List<Integer> from = new ArrayList<>();
        List<Integer> to = new ArrayList<>();
        transient int next;

        Rule(List<Integer> from, List<Integer> to) {
            this.from = from;
            this.to = to;
        }

        int nextTarget() {
            return to.get(next++ % to.size());
        }

        @Override
        public String toString() {
            return slots(from) + " -> " + slots(to);
        }
    }

    static class Config {
        List<Rule> rules = new ArrayList<>(List.of(new Rule(new ArrayList<>(List.of(0)), new ArrayList<>(List.of(1)))));
        long switchDelayMs = 0; // 0 = same-tick pre-attack switch, >0 = post-hit delay
        boolean autoReturn = true;
        boolean swapOnMiss = false; // also swap when the attack hits air
    }

    static Config config = new Config();
    private static Path configPath;
    static boolean enabled;
    private static long switchAt = -1; // wall-clock ms of a pending post-hit switch, -1 = none
    private static boolean awaitingReturn;
    private static int returnSlot; // slot held when the attack started
    private static int swapSlot; // slot we swapped to

    @Override
    public void onInitializeClient() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("lazyswap.json");
        config = loadConfig(configPath);
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

        // Slots are 1-9 here to match the hotbar keys; the config file stores 0-8.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(literal("lazyswap")
                .executes(c -> show(c.getSource()))
                .then(literal("rule")
                        .then(literal("add").then(argument("rule", StringArgumentType.greedyString()).executes(c -> {
                            Rule rule;
                            try {
                                rule = parseRule(StringArgumentType.getString(c, "rule"));
                            } catch (IllegalArgumentException e) {
                                c.getSource().sendError(Component.literal("LazySwap: " + e.getMessage() + ". Example: /lazyswap rule add 1,5 -> 2,3"));
                                return 0;
                            }
                            return set(c.getSource(), () -> {
                                removeFrom(rule.from);
                                config.rules.add(rule);
                            });
                        })))
                        .then(literal("remove").then(argument("slot", IntegerArgumentType.integer(1, 9))
                                .executes(c -> set(c.getSource(), () -> removeFrom(List.of(IntegerArgumentType.getInteger(c, "slot") - 1))))))
                        .then(literal("clear").executes(c -> set(c.getSource(), () -> config.rules.clear()))))
                .then(literal("swapback")
                        .then(argument("value", BoolArgumentType.bool())
                                .executes(c -> set(c.getSource(), () -> config.autoReturn = BoolArgumentType.getBool(c, "value")))))
                .then(literal("miss")
                        .then(argument("value", BoolArgumentType.bool())
                                .executes(c -> set(c.getSource(), () -> config.swapOnMiss = BoolArgumentType.getBool(c, "value")))))));
    }

    /** Parses "1,5 -> 2,3" or "1,5 2,3" (also accepts [brackets]); slots are 1-9 and come back as 0-8. */
    static Rule parseRule(String text) {
        String[] parts = text.replace("->", " ").replaceAll("[\\[\\]]", "").trim().split("\\s+");
        if (parts.length != 2) throw new IllegalArgumentException("expected <from> -> <to>");
        return new Rule(parseSlots(parts[0]), parseSlots(parts[1]));
    }

    private static List<Integer> parseSlots(String list) {
        List<Integer> slots = new ArrayList<>();
        for (String s : list.split(",")) {
            int slot;
            try {
                slot = Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + s + "' is not a slot");
            }
            if (slot < 1 || slot > 9) throw new IllegalArgumentException("slot " + slot + " is not 1-9");
            slots.add(slot - 1);
        }
        return slots;
    }

    // A source slot belongs to one rule only, so adding a rule takes its slots away from older ones.
    private static void removeFrom(List<Integer> slots) {
        config.rules.forEach(r -> r.from.removeAll(slots));
        config.rules.removeIf(r -> r.from.isEmpty());
    }

    private static String slots(List<Integer> slots) {
        return slots.stream().map(s -> String.valueOf(s + 1)).collect(Collectors.joining(",", "[", "]"));
    }

    private static int set(FabricClientCommandSource source, Runnable change) {
        change.run();
        try {
            Files.writeString(configPath, GSON.toJson(config));
        } catch (IOException e) {
            LOG.error("Failed to save {}", configPath, e);
            source.sendError(Component.literal("LazySwap: couldn't save config, change applies until restart"));
        }
        return show(source);
    }

    private static int show(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("LazySwap: swapback=" + config.autoReturn + ", miss=" + config.swapOnMiss
                + (config.rules.isEmpty() ? ", no rules" : ", rules: " + config.rules.stream().map(Rule::toString).collect(Collectors.joining("  ")))));
        return 1;
    }

    /** Called at the head of MultiPlayerGameMode#attack, before the attack packet is built. */
    public static void onAttack(Player player) {
        if (!enabled) return;
        int slot = player.getInventory().getSelectedSlot();
        Rule rule = config.rules.stream().filter(r -> r.from.contains(slot)).findFirst().orElse(null);
        if (rule == null) return;
        int target = rule.nextTarget();
        if (target == slot) return; // e.g. 4 -> [5,4]: this hit stays on 4
        returnSlot = slot;
        swapSlot = target;
        if (config.switchDelayMs == 0) {
            // attack() calls ensureHasSentCarriedItem() next, so the slot packet goes out right before the attack packet.
            select(player, returnSlot, swapSlot);
        } else {
            switchAt = System.currentTimeMillis() + config.switchDelayMs;
        }
    }

    /** Called when an attack hits air; returns whether onAttack ran. */
    public static boolean onMiss(Player player) {
        if (!config.swapOnMiss) return false;
        onAttack(player);
        return true;
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
            select(player, returnSlot, swapSlot);
        }
        // Instant mode returns right after the attack packet went out, so every click swaps again;
        // waiting for cooldown there meant spam-clicks on the secondary kept resetting it and never returned.
        // Delay mode keeps the secondary until cooldown is full for the follow-up hit. Next game tick sends the slot packet.
        if (awaitingReturn && (config.switchDelayMs == 0 || player.getAttackStrengthScale(0f) >= 1f)) {
            awaitingReturn = false;
            if (player.getInventory().getSelectedSlot() == swapSlot) player.getInventory().setSelectedSlot(returnSlot);
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
        // Hand-edited files may have nulls or out-of-range slots; drop those instead of crashing on attack.
        if (c.rules == null) c.rules = new ArrayList<>();
        c.rules.removeIf(Objects::isNull);
        for (Rule r : c.rules) {
            r.from = r.from == null ? new ArrayList<>() : new ArrayList<>(r.from.stream().filter(s -> s != null && s >= 0 && s <= 8).toList());
            r.to = r.to == null ? new ArrayList<>() : new ArrayList<>(r.to.stream().filter(s -> s != null && s >= 0 && s <= 8).toList());
        }
        c.rules.removeIf(r -> r.from.isEmpty() || r.to.isEmpty());
        c.switchDelayMs = Math.max(0, c.switchDelayMs);
        return c;
    }
}
