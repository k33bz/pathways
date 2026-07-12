# Pathways

Server-side Fabric mod for Minecraft 26.1.x. Built paths grant vanilla **Speed** — at full
strength where civilization is, fading through the wilds. With
[sanctuary](https://github.com/k33bz/sanctuary) installed, a defined road between sanctuaries
is meaningfully faster than cutting a straight line through the wilderness, which gives roads
a reason to exist.

Clean-room design: inspired by the *idea* of the Faster Paths datapack (speed on path blocks),
shares no code or mechanics with it. MIT licensed.

## Mechanics

- Standing on any block in the `#pathways:paths` tag (default: `minecraft:dirt_path`) applies
  the native Speed effect — ambient, no particles, icon visible. Vanilla clients need nothing.
- **2s linger** (`lingerTicks`): the effect survives stepping off the path, so natural-looking
  paths interleaved with grass, and sprint-jumps, keep the boost flowing.
- **Sanctuary falloff**: the boost level follows the same safety geometry sanctuary's mob
  scaling uses (`Sanctuary.blocksBeyondNearestAnchor` — config spawn anchors + placed crystals,
  dormant anchors grant nothing):
  - inside a safe zone → `insideLevel` (default **Speed II**)
  - each `ringBlocks`-wide ring beyond the zone edge costs one level
    (default **Speed I** for the first 384 wild blocks, **nothing past 768**)
  - `minLevel` floors the falloff (e.g. `1` = frontier roads keep Speed I out to
    `maxBeyondBlocks`)
- Without sanctuary (or before its config loads) paths give a flat `noSanctuaryLevel`
  (default Speed II) — the mod is fully standalone.
- **Step-up** (`stepUpEnabled`, default on): while path-boosted you also get `stepUpBonus`
  (default `+0.4` → total 1.0) on the step-height attribute — the path flows up full-block
  rises like stairs, no jumping. Applied as a **transient additive modifier** with its own id
  (`pathways:step_up`): it never touches the base value, never conflicts with other mods'
  modifiers, isn't persisted, and expires on the same 2s linger window as the speed boost
  (an expiry sweep also strips it immediately if an admin toggles it off live).
- A stronger Speed from a beacon or potion is never downgraded (vanilla `addEffect` semantics).
- Players only, survival/adventure/creative (spectators skipped), `dimensions`-gated
  (default overworld only — matching sanctuary's scaling dimension).

## Integration

Sanctuary is a **soft dependency** resolved by reflection (`SanctuaryBridge`): no compile-time
coupling, this repo builds alone, and if sanctuary's internals ever rename, pathways logs a
warning and falls back to `noSanctuaryLevel` instead of crashing.

## Config — `config/pathways.json`

| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | master switch |
| `insideLevel` | `2` | Speed level on paths inside a sanctuary zone (2 = Speed II) |
| `noSanctuaryLevel` | `2` | flat level when sanctuary is absent |
| `ringBlocks` | `384` | width of each one-level falloff ring beyond the zone edge |
| `minLevel` | `0` | falloff floor (0 = fades out entirely) |
| `stepUpEnabled` | `true` | walk up full-block ledges while path-boosted (knob takes 0/1) |
| `stepUpBonus` | `0.4` | added to vanilla 0.6 step height (0.4 → exactly one block) |
| `maxBeyondBlocks` | `4096` | hard cutoff (only matters when `minLevel` > 0) |
| `lingerTicks` | `40` | boost persistence after stepping off (2s) |
| `checkEveryTicks` | `5` | player scan cadence |
| `dimensions` | `["minecraft:overworld"]` | where paths boost at all |

## Commands

- `/pathways status` — anyone: on-path?, zone distance, the boost level *here*
- `/pathways set <knob> <value>` · `toggle` · `save` · `reload` — ops: live tuning,
  sanctuary-style (changes apply next tick; `save` persists)

## Extending the path palette

Override the tag with a datapack, e.g.
`data/pathways/tags/block/paths.json`:

```json
{ "values": ["minecraft:dirt_path", "minecraft:mud_bricks", "#minecraft:planks"] }
```

Gravel is deliberately **not** a default: it generates naturally everywhere and would hand out
free speed off-road.

## Build

```
./gradlew build    # needs JDK 25 (gradle toolchain auto-provisions); jar in build/libs/
```

Same toolchain discipline as sanctuary: MC 26.x is un-obfuscated — no mappings, no refmap.
