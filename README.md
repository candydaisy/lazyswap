# LazySwap

A client-side Fabric mod for Minecraft 26.3 that does attribute swapping for you. When you attack, it switches to another hotbar slot (for example a sword) so the hit lands with it, then switches back to the slot you were holding.

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

While it's on, attacking a mob or player follows your **rules**. A rule says "when I attack from these slots, swap to these slots":

- `1 -> 2`: attacking from slot 1 swaps to slot 2 for the hit.
- `1 -> 2,3`: several targets cycle one per hit: 2, then 3, then 2 again.
- `4 -> 5,4`: alternates between a hit with slot 5 and a normal hit with slot 4.
- `1,5 -> 3`: attacking from slot 1 or slot 5 swaps to slot 3.

Attacking from a slot with no rule does nothing. The default is one rule, `1 -> 2`.

Swinging at air doesn't swap unless you turn it on with `/lazyswap miss true`.

## Commands

Slots are numbered 1–9, the same as the hotbar keys.

| Command | What it does |
|---|---|
| `/lazyswap` | Show your rules and the swapback and miss settings |
| `/lazyswap rule add <from> -> <to>` | Add a rule, e.g. `/lazyswap rule add 1,5 -> 2,3`. The `->` and `[ ]` are optional. |
| `/lazyswap rule remove <slot>` | Stop that slot from triggering a swap |
| `/lazyswap rule clear` | Remove every rule |
| `/lazyswap swapback <true\|false>` | Switch back to the slot you had before |
| `/lazyswap miss <true\|false>` | Also swap when you swing at air (default `false`) |

Each slot can trigger only one rule. Adding a rule for a slot that's already used moves that slot to the new rule. Changes are saved straight away.

## Config file

The settings live in `.minecraft/config/lazyswap.json`, which is created the first time you launch the game with the mod. In this file, slots are numbered **0–8** (slot 1 is `0`).

```json
{
  "rules": [
    { "from": [0], "to": [1, 2] },
    { "from": [3], "to": [4, 3] }
  ],
  "switchDelayMs": 0,
  "autoReturn": true,
  "swapOnMiss": false
}
```

| Key | Meaning |
|---|---|
| `rules` | List of rules. `from` holds the slots that trigger the rule; `to` holds the slots it swaps to, cycling one per hit. |
| `switchDelayMs` | `0` swaps before the hit, so the hit uses the target item. More than `0` lands the hit with your current item, then swaps after that many milliseconds for a follow-up hit. One game tick is 50 ms. |
| `swapOnMiss` | Also follow the rules when the swing hits air, not just a mob or player. |
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
