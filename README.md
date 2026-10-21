# LazySwap

A client-side Fabric mod for Minecraft 26.3 that does attribute swapping for you. When you attack, it switches to your secondary hotbar slot (for example a sword) so the hit lands with it, then switches back to the slot you were holding.

Made for private and practice testing. Many servers count automatic slot switching as cheating, so check the rules before using it anywhere else.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.16 or newer
- Fabric API for 26.3
- Java 25 or newer

## Install

1. Build the jar (see [Building](#building)) or download one from Releases.
2. Put `lazyswap-<version>.jar` and Fabric API in your `.minecraft/mods` folder.
3. Start Minecraft 26.3 with the Fabric profile.

## Usage

Press **V** to turn LazySwap on or off. It starts off each time you launch the game, and the action bar shows `LazySwap: ON` or `LazySwap: OFF`. You can change the key in Options → Controls → Key Binds → Miscellaneous.

While it's on, attacking a mob or player from your primary slot swaps to the secondary slot for the hit. If you're already holding the secondary slot, nothing happens.

## Commands

Slots are numbered 1–9, the same as the hotbar keys.

| Command | What it does |
|---|---|
| `/lazyswap` | Show the current settings |
| `/lazyswap primary <1-9>` | Only swap when attacking from this slot |
| `/lazyswap primary any` | Swap when attacking from any slot |
| `/lazyswap secondary <1-9>` | The slot to swap to |
| `/lazyswap swapback <true\|false>` | Switch back to the slot you had before |

Changes are saved straight away.

## Config file

The settings live in `.minecraft/config/lazyswap.json`, which is created the first time you launch the game with the mod. In this file, slots are numbered **0–8** (slot 1 is `0`).

```json
{
  "primarySlot": -1,
  "secondarySlot": 1,
  "switchDelayMs": 0,
  "autoReturn": true
}
```

| Key | Meaning |
|---|---|
| `primarySlot` | Slot that triggers the swap. `-1` means any slot. |
| `secondarySlot` | Slot to swap to. |
| `switchDelayMs` | `0` swaps before the hit, so the hit uses the secondary item. More than `0` lands the hit with your current item, then swaps after that many milliseconds for a follow-up hit. One game tick is 50 ms. |
| `autoReturn` | Switch back afterwards. With a delay of `0` it switches back right after the hit. With a delay, it switches back once the attack cooldown is full. |

`switchDelayMs` has no command yet. Edit the file and restart the game to change it.

## Building

You need JDK 25 or newer. Use the included Gradle wrapper, because older system Gradle versions can't load Fabric Loom.

```bash
./gradlew build
```

This creates the jar at `build/libs/lazyswap-<version>.jar`. To launch a test client with the mod loaded, run:

```bash
./gradlew runClient
```

## License

MIT, see [LICENSE](LICENSE).
