---
name: verify
description: Runtime-verify pathways against a live Fabric 26.1.2 server with a mineflayer bot and RCON oracle
---

# Verifying pathways

Build, then run the ready-made end-to-end script in the mc-test-harness repo:

```bash
cd /c/Users/k33bz/pathways && ./gradlew build -q
cp build/libs/pathways-*+26.1.2.jar ../mc-test-harness/server/mods/
cd ../mc-test-harness && node scripts/verify-pathways.js
```

`scripts/verify-pathways.js` boots the harness server (sanctuary + pathways in
`server/mods/`), bridges a mineflayer bot via ViaProxy, and asserts through RCON
`data get entity <bot> active_effects`: Speed II on paths inside the sanctuary
zone (config anchor at 0,0 r=128), Speed I in falloff ring 1 (x≈310), nothing in
deep wilds (x≈1000), the 2s linger, live `/pathways set` knobs, and
no-downgrade vs stronger potions. Exit 0 = all checks passed.

Gotchas:

- The harness needs the sanctuary jar in `server/mods/` (bridge test) — grab it
  from `../sanctuary/build/libs/`.
- A trailing `read ECONNRESET` after the checks is teardown noise (bot socket vs
  server stop), not a failure — trust the `=== verify-pathways ===` summary line.
- Server boot is ~60-90s; full run ~3 min.
