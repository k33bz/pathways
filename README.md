# Pathways

| Branch | Minecraft | Status |
|---|---|---|
| `main` | 26.3 | [![build main](https://github.com/k33bz/pathways/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/k33bz/pathways/actions/workflows/build.yml?query=branch%3Amain) ![mod main](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpathways%2Fbadges%2Fmain%2Fmod.json) ![minecraft main](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpathways%2Fbadges%2Fmain%2Fminecraft.json) ![loader main](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpathways%2Fbadges%2Fmain%2Floader.json) ![fabric-api main](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpathways%2Fbadges%2Fmain%2Ffabric-api.json) ![sanctuary main](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpathways%2Fbadges%2Fmain%2Fsanctuary.json) ![server-test main](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpathways%2Fbadges%2Fmain%2Fserver-test.json) |
| `26.2` | 26.2 | [![build 26.2](https://github.com/k33bz/pathways/actions/workflows/build.yml/badge.svg?branch=26.2)](https://github.com/k33bz/pathways/actions/workflows/build.yml?query=branch%3A26.2) ![mod 26.2](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpathways%2Fbadges%2F26.2%2Fmod.json) ![minecraft 26.2](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpathways%2Fbadges%2F26.2%2Fminecraft.json) ![loader 26.2](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpathways%2Fbadges%2F26.2%2Floader.json) ![fabric-api 26.2](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpathways%2Fbadges%2F26.2%2Ffabric-api.json) ![sanctuary 26.2](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpathways%2Fbadges%2F26.2%2Fsanctuary.json) ![server-test 26.2](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpathways%2Fbadges%2F26.2%2Fserver-test.json) |
| `26.1` | 26.1.x | [![build 26.1](https://github.com/k33bz/pathways/actions/workflows/build.yml/badge.svg?branch=26.1)](https://github.com/k33bz/pathways/actions/workflows/build.yml?query=branch%3A26.1) ![mod 26.1](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpathways%2Fbadges%2F26.1%2Fmod.json) ![minecraft 26.1](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpathways%2Fbadges%2F26.1%2Fminecraft.json) ![loader 26.1](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpathways%2Fbadges%2F26.1%2Floader.json) ![fabric-api 26.1](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpathways%2Fbadges%2F26.1%2Ffabric-api.json) ![sanctuary 26.1](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpathways%2Fbadges%2F26.1%2Fsanctuary.json) ![server-test 26.1](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpathways%2Fbadges%2F26.1%2Fserver-test.json) |

Server-side Fabric mod for Minecraft 26.1.x, 26.2 and 26.3 (one branch per line, see below). Built paths grant vanilla **Speed** — at full
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

Values in the file are held to the same ranges as `/pathways set` when loaded. If the file
can't be parsed, pathways logs an error, runs on defaults and **leaves the file untouched**, so
fix it and `/pathways reload`.

## Commands

- `/pathways status` — anyone: on-path?, zone distance, the boost level *here*
- `/pathways at <x> <z>` — ops (works from the console): the boost a path would give at any
  spot, with its distance past the nearest sanctuary edge. For planning roads.
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

## Branches, CI and releases

| Branch | Minecraft | Jar |
|---|---|---|
| `main` | 26.3 | `pathways-<version>+26.3.jar` |
| `26.2` | 26.2 | `pathways-<version>+26.2.jar` |
| `26.1` | 26.1.2 | `pathways-<version>+26.1.2.jar` |

Version pins (loader, fabric-api) follow sanctuary's branch of the same Minecraft version. Each
jar only loads on its own Minecraft line (`fabric.mod.json` pins `~minecraft_version`).

Every push and PR builds, runs the unit tests, and boots real Fabric servers
(`scripts/server_test.py`): pathways alone (config, commands, path tag, a malformed config left
untouched), then with sanctuary built from its branch of the same Minecraft version (the bridge
resolves and `/pathways at` matches the falloff math from spawn out to the deep wilds). The
in-game Speed effect itself needs a player, so that stays with the mineflayer harness
(`.claude/skills/verify`).

Releases are per line: push a tag `v<mod_version>+<minecraft_version>` (for example
`v0.2.1+26.1.2`) on that line's branch. CI checks the tag against the commit, builds, runs the
server test, and publishes the jar as a [GitHub release](../../releases) with notes from
`CHANGELOG.md`. Only `main` releases are marked latest.
