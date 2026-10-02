# Changelog

## 0.2.1

Same release on every line: `main` (26.3), `26.2` and `26.1` (26.1.2).

- **Minecraft 26.3.** `main` now targets 26.3. 26.2 moved to its own `26.2` branch, and `26.1` stays as it was. Version pins follow sanctuary's branch of the same Minecraft version (26.3: loader 0.19.5, fabric-api 0.161.0+26.3; 26.2: loader 0.19.5, fabric-api 0.161.0+26.2; 26.1.2: loader 0.19.5, the loader gmc101 runs, fabric-api 0.155.3+26.1.2).
- **Each jar only loads on its own Minecraft line.** `fabric.mod.json` now takes its `minecraft` range from `minecraft_version` (`~26.3`, `~26.2`, `~26.1.2`), so a port is a `gradle.properties` change only.
- **A broken config can no longer reset or crash the server.** Two problems in `config/pathways.json` handling:
  - If the file failed to parse, pathways silently wrote defaults over it, wiping every knob an admin had set. Now it logs an error, runs on defaults in memory and leaves the file untouched until it is fixed and `/pathways reload` is run.
  - Values were used as read. `"dimensions": null` threw inside the server tick, which crashes the server; out-of-range or NaN numbers (zero cadence, NaN ring width, huge step-up) went straight into the tick. Every value is now pulled into the same range `/pathways set` allows, on every load (`PathwaysConfig.sanitize`, unit tested in `PathwaysConfigTest`).
- **`/pathways at <x> <z>`** (ops, works from the console): the boost a path would give at any spot, with its distance past the nearest sanctuary edge. For planning roads, and what lets CI test the falloff without a player.
- **CI** (`.github/workflows/build.yml`): every push and PR builds, runs the unit tests and boots real Fabric servers (`scripts/server_test.py`), first pathways alone, then with sanctuary built from its branch of the same Minecraft version. README badges per line come from the orphan `badges` branch (`scripts/publish_badges.py`, `scripts/commit_badges.sh`).
- **Releases** (`.github/workflows/release.yml`): pushing `v<mod_version>+<minecraft_version>` on a line's branch checks the tag, builds, runs the server test and publishes the jar (with a `.sha256`) as a GitHub release, with notes from this changelog.

## 0.2.0

- Step-up: paths flow up full-block rises like stairs (transient step-height modifier while path-boosted).

## 0.1.0

- Speed-boosting paths with sanctuary falloff.
