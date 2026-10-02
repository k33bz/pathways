#!/usr/bin/env python3
"""Boot a real Fabric server with the freshly built pathways jar and run scripted checks.

The unit tests cover the falloff math and the config sanitizer; this answers "does the jar
actually load and work on a real server of this Minecraft version". No bots and no client: every
check goes in through the server console and reads the server's own answer. Standard library
only, so it runs on a stock GitHub runner. (Adapted from k33bz/sswaystones' server test.)

Two boots:
  alone     pathways without sanctuary: init line, config file written, /pathways at falls back
            to noSanctuaryLevel, /pathways set|save|reload|toggle answer, `#pathways:paths` holds
            dirt_path, and a malformed config/pathways.json is left untouched on reload (the
            server keeps running on defaults instead of overwriting the admin's file)
  sanctuary only with --sanctuary-jar: pathways + a sanctuary build of the same Minecraft line.
            The reflection bridge resolves, and /pathways at agrees with BoostMath at points from
            inside the spawn sanctuary out to the deep wilds

A player standing on a path (the Speed effect itself, step-up, linger) needs a client; that is
covered by the mineflayer harness in .claude/skills/verify, not here.

Which Minecraft: the newest stable release of this line (minecraft_version 26.1.2 tests 26.1.2).
Writes build/server-test/versions.json for the README badges and a Markdown summary to
$GITHUB_STEP_SUMMARY. Exit code 0 = every check passed.

Usage: python3 scripts/server_test.py [--jar build/libs/x.jar] [--sanctuary-jar path]
                                      [--workdir build/server-test]
"""
import argparse
import glob
import json
import math
import os
import queue
import re
import shutil
import subprocess
import sys
import threading
import time
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UA = {"User-Agent": "k33bz/pathways server-test (github actions)"}

# Log lines that mean the mod (or the server) failed.
FATAL = re.compile(
    r"Mixin apply failed|InvalidInjectionException|InvalidMixinException|MixinApplyError"
    r"|Critical injection failure|Exception ticking world|Encountered an unexpected exception"
    r"|Could not execute entrypoint|Error executing task|Unbound values in registry"
    r"|Failed to load registries|Error generating chunk")

AT = re.compile(r"\[pathways\] at (-?\d+) (-?\d+): (.*?) \| path boost: .*? \| level=(-?\d+) beyond=(\S+)")


# ---------------------------------------------------------------- downloads

def http_json(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return json.load(r)


def download(url, dest):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=300) as r, \
            open(dest, "wb") as f:
        shutil.copyfileobj(r, f)


def props():
    out = {}
    with open(os.path.join(ROOT, "gradle.properties")) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                out[k.strip()] = v.strip()
    return out


def modrinth_file(project, mc, want_version=None):
    """Primary file of a Modrinth project's Fabric build for `mc` (exact version if given)."""
    q = urllib.parse.urlencode({"game_versions": json.dumps([mc]), "loaders": json.dumps(["fabric"])})
    versions = http_json(f"https://api.modrinth.com/v2/project/{project}/version?{q}")
    if not versions:
        return None
    pick = next((v for v in versions if v["version_number"] == want_version), None) if want_version else None
    pick = pick or versions[0]
    f = next((x for x in pick["files"] if x.get("primary")), pick["files"][0])
    return pick["version_number"], f["url"], f["filename"]


# ---------------------------------------------------------------- server process

class Server:
    def __init__(self, workdir):
        self.workdir = workdir
        self.lines = queue.Queue()
        self.log = []
        self.fatal = []
        self.proc = None

    def start(self):
        self.proc = subprocess.Popen(
            ["java", "-Xms1G", "-Xmx2G", "-jar", "fabric-server-launch.jar", "nogui"],
            cwd=self.workdir, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, bufsize=1)
        threading.Thread(target=self._pump, daemon=True).start()

    def _pump(self):
        for line in self.proc.stdout:
            line = line.rstrip("\n")
            self.log.append(line)
            if FATAL.search(line):
                self.fatal.append(line)
            self.lines.put(line)
        self.lines.put(None)  # process ended

    def send(self, cmd):
        self.proc.stdin.write(cmd + "\n")
        self.proc.stdin.flush()

    def wait_for(self, pattern, timeout):
        """Next line matching `pattern` (regex) within `timeout` s, else None."""
        rx = re.compile(pattern)
        end = time.time() + timeout
        while time.time() < end:
            try:
                line = self.lines.get(timeout=max(0.05, end - time.time()))
            except queue.Empty:
                break
            if line is None:
                return None
            if rx.search(line):
                return line
        return None

    def drain(self):
        while True:
            try:
                self.lines.get_nowait()
            except queue.Empty:
                return

    def test(self, cmd):
        """Run an `execute if ...` command; True = passed, False = failed, None = no answer."""
        self.drain()
        self.send(cmd)
        line = self.wait_for(r"Test (passed|failed)", 10)
        if line is None:
            return None
        return "Test passed" in line

    def poll(self, cmd, want, timeout):
        """Repeat `cmd` until it answers `want` or `timeout` s pass. Returns the last answer."""
        end = time.time() + timeout
        got = None
        while time.time() < end:
            got = self.test(cmd)
            if got == want:
                return got
            time.sleep(1)
        return got

    def alive(self):
        self.drain()
        self.send("list")
        return self.wait_for(r"There are \d+ of a max", 15) is not None

    def stop(self):
        if self.proc and self.proc.poll() is None:
            try:
                self.send("stop")
                self.proc.wait(timeout=60)
            except Exception:
                self.proc.kill()



def test_minecraft(line):
    """Newest stable release of this Minecraft line: '26.1' -> '26.1.2' when that exists."""
    stable = [v["version"] for v in http_json("https://meta.fabricmc.net/v2/versions/game") if v["stable"]]
    same = [v for v in stable if v == line or v.startswith(line + ".")]
    return same[0] if same else line   # meta lists newest first


# What the server test actually booted with, for scripts/publish_badges.py (and humans).
TESTED = {}


def setup(workdir, jars, p, mc):
    """Fresh server dir with fabric loader, the newest fabric-api for `mc`, and `jars`."""
    shutil.rmtree(workdir, ignore_errors=True)
    os.makedirs(os.path.join(workdir, "mods"), exist_ok=True)
    installer = next(i["version"] for i in http_json("https://meta.fabricmc.net/v2/versions/installer") if i["stable"])
    download(f"https://meta.fabricmc.net/v2/versions/loader/{mc}/{p['loader_version']}/{installer}/server/jar",
             os.path.join(workdir, "fabric-server-launch.jar"))
    # The newest fabric-api for this Minecraft version, as a real server would run, not the pin.
    api = modrinth_file("fabric-api", mc)
    if api is None:
        raise SystemExit(f"no fabric-api build on Modrinth for {mc}")
    download(api[1], os.path.join(workdir, "mods", api[2]))
    TESTED["fabric_api_tested"] = api[0]
    for jar in jars:
        shutil.copy(jar, os.path.join(workdir, "mods", os.path.basename(jar)))
    with open(os.path.join(workdir, "eula.txt"), "w") as f:
        f.write("eula=true\n")
    with open(os.path.join(workdir, "server.properties"), "w") as f:
        f.write("\n".join([
            "online-mode=false", "level-type=minecraft\\:flat", "spawn-protection=0",
            "view-distance=4", "simulation-distance=4",
            # Default 60: an empty server stops ticking, and nothing here ever joins.
            "pause-when-empty-seconds=-1",
            "enable-command-block=false", "sync-chunk-writes=false", "server-port=25599", ""]))
    return installer, api


def boot(workdir, results, label, init_pattern, timeout):
    """Start a server and wait for pathways' init line and Done. Returns (Server, ready).

    The Server comes back even when the boot failed, so its log (the crash) is kept."""
    s = Server(workdir)
    s.start()
    init = s.wait_for(init_pattern, timeout)
    results.append((f"{label}: pathways initialized", init is not None, init or _last_error(s)))
    done = s.wait_for(r"Done \(\d", timeout) if init else None
    results.append((f"{label}: server reached Done", done is not None, done or ("" if not init else _last_error(s))))
    return s, done is not None


def _last_error(s):
    """The most telling line of a failed boot, for the summary table."""
    for line in reversed(s.log):
        if re.search(r"ERROR|Exception|Incompatible|requires|Caused by", line):
            return line[-200:]
    return s.log[-1][-200:] if s.log else "server produced no output"


def at(s, x, z):
    """(level, beyond-or-None, zone text) from /pathways at, or None if it did not answer."""
    ok, line = answer(s, f"pathways at {x} {z}", r"\[pathways\] at ")
    m = AT.search(line) if ok else None
    if not m:
        return None
    beyond = None if m.group(5) == "none" else float(m.group(5))
    return int(m.group(4)), beyond, m.group(3)


def boost_level(beyond, inside, ring, min_level, max_beyond):
    """BoostMath.level, mirrored, so the real server's answer can be checked against it."""
    if beyond <= 0:
        return inside
    if beyond > max_beyond:
        return 0
    rings = math.ceil(beyond / max(1.0, ring))
    return max(inside - rings, max(min_level, 0))


# ---------------------------------------------------------------- scenarios

# Only the server's own command errors count as a bad answer: pathways logs "Could not read" on
# purpose when a reload meets a broken config, and sanctuary logs plenty while it boots.
def answer(s, cmd, ok_pattern, bad_pattern=r"Unknown or incomplete command|Incorrect argument|Unknown command", timeout=20):
    """Send a console command; (True|False|None, line): matched ok, matched bad, or no answer."""
    s.drain()
    s.send(cmd)
    rx_ok, rx_bad = re.compile(ok_pattern), re.compile(bad_pattern)
    end = time.time() + timeout
    while time.time() < end:
        line = s.wait_for(r".", max(0.1, end - time.time()))
        if line is None:
            break
        if rx_ok.search(line):
            return True, line
        if rx_bad.search(line):
            return False, line
    return None, ""


def run_alone(s, results, workdir):
    def check(name, ok, detail=""):
        results.append((f"alone: {name}", bool(ok), detail))
        print(f"[{'PASS' if ok else 'FAIL'}] alone: {name} {detail}", flush=True)

    cfg_path = os.path.join(workdir, "config", "pathways.json")
    try:
        with open(cfg_path) as f:
            cfg = json.load(f)
        check("config/pathways.json written with defaults",
              cfg.get("insideLevel") == 2 and cfg.get("ringBlocks") == 384.0
              and cfg.get("dimensions") == ["minecraft:overworld"], json.dumps(cfg)[:120])
    except (OSError, ValueError) as e:
        check("config/pathways.json written with defaults", False, str(e))

    # The path tag ships dirt_path (data pack loads, tag id is right)
    s.send("forceload add 0 0")
    time.sleep(2)
    s.send("setblock 0 -60 0 minecraft:dirt_path")
    time.sleep(1)
    check("#pathways:paths includes dirt_path", s.test("execute if block 0 -60 0 #pathways:paths"))

    # Without sanctuary the bridge reports nothing and the fallback level applies
    r = at(s, 0, 0)
    check("/pathways at falls back to noSanctuaryLevel (2)",
          r is not None and r[0] == 2 and r[1] is None, str(r))

    # Knobs: set, save, reload
    ok, line = answer(s, "pathways set ringBlocks 200", r"ringBlocks = 200")
    check("/pathways set ringBlocks 200", ok, line[-100:])
    ok, line = answer(s, "pathways save", r"config saved")
    try:
        saved = json.load(open(cfg_path)).get("ringBlocks")
    except (OSError, ValueError):
        saved = None
    check("/pathways save persists the knob", ok and saved == 200.0, f"ringBlocks in file: {saved}")
    ok, line = answer(s, "pathways reload", r"config reloaded")
    check("/pathways reload", ok, line[-100:])
    ok, line = answer(s, "pathways toggle", r"\[pathways\] (enabled|disabled)")
    ok2, line2 = answer(s, "pathways toggle", r"\[pathways\] (enabled|disabled)")
    check("/pathways toggle off and back on", ok and ok2 and "disabled" in line and "enabled" in line2,
          f"{line[-30:]} / {line2[-30:]}")

    # A malformed file is never overwritten: reload keeps defaults in memory and leaves it alone
    broken = '{"insideLevel": 3, "ringBlocks": oops'
    with open(cfg_path, "w") as f:
        f.write(broken)
    s.drain()
    ok, line = answer(s, "pathways reload", r"config reloaded")
    time.sleep(1)
    with open(cfg_path) as f:
        after = f.read()
    complained = any("Could not read" in l for l in s.log[-200:])
    check("malformed config is left untouched on reload", ok and after == broken and complained,
          f"file kept: {after == broken}, logged: {complained}")
    r = at(s, 0, 0)
    check("server keeps running on defaults after a bad config", r is not None and r[0] == 2 and s.alive(), str(r))
    with open(cfg_path, "w") as f:
        json.dump({"insideLevel": 2}, f)
    ok, _ = answer(s, "pathways reload", r"config reloaded")
    try:
        repaired = json.load(open(cfg_path))
    except (OSError, ValueError):
        repaired = {}
    check("a fixed config reloads and gains its missing keys", ok and "ringBlocks" in repaired, str(list(repaired))[:120])
    time.sleep(3)
    check("ticks run cleanly", s.alive() and not s.fatal, "; ".join(s.fatal[:2]))


def run_sanctuary(s, results, workdir):
    def check(name, ok, detail=""):
        results.append((f"sanctuary: {name}", bool(ok), detail))
        print(f"[{'PASS' if ok else 'FAIL'}] sanctuary: {name} {detail}", flush=True)

    with open(os.path.join(workdir, "config", "pathways.json")) as f:
        cfg = json.load(f)
    params = (cfg["insideLevel"], cfg["ringBlocks"], cfg["minLevel"], cfg["maxBeyondBlocks"])
    # From the spawn sanctuary out to the deep wilds. The bridge answers `beyond` (blocks past the
    # nearest zone edge); the level must be exactly what BoostMath gives for that distance.
    points = [(0, 0), (300, 0), (700, 0), (1200, 0), (3000, 0), (20000, 0), (0, -20000)]
    got = [(p, at(s, *p)) for p in points]
    resolved = any("sanctuary bridge resolved" in l for l in s.log)
    answered = [g for g in got if g[1] is not None]
    check("bridge resolves against this sanctuary build", resolved and answered
          and all(g[1][1] is not None for g in answered),
          f"resolved log: {resolved}; " + ", ".join(f"{p}->{r and r[1]}" for p, r in got)[:150])
    wrong = [(p, r) for p, r in answered if r[1] is not None and r[0] != boost_level(r[1], *params)]
    check("/pathways at matches BoostMath at every distance", len(answered) == len(points) and not wrong,
          "; ".join(f"{p}: level {r[0]} at beyond {r[1]}" for p, r in (wrong or answered))[:200])
    levels = {r[0] for _, r in answered}
    check("boost falls off from the zone to the wilds", max(levels, default=0) >= 1 and 0 in levels,
          f"levels seen: {sorted(levels)}")
    time.sleep(3)
    check("ticks run cleanly with sanctuary", s.alive() and not s.fatal, "; ".join(s.fatal[:2]))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar")
    ap.add_argument("--sanctuary-jar", help="sanctuary build of the same Minecraft line; omit to skip that boot")
    ap.add_argument("--workdir", default=os.path.join(ROOT, "build", "server-test"))
    ap.add_argument("--boot-timeout", type=int, default=600)
    a = ap.parse_args()
    jar = a.jar or next((j for j in sorted(glob.glob(os.path.join(ROOT, "build", "libs", "*.jar")))
                         if not j.endswith(("-sources.jar", "-dev.jar"))), None)
    if not jar:
        raise SystemExit("no jar: run ./gradlew build first or pass --jar")
    p = props()
    mc = test_minecraft(p["minecraft_version"])
    TESTED["minecraft_tested"] = mc
    results, logs, notes = [], [], []
    fatal = []

    installer, api = setup(a.workdir, [jar], p, mc)
    notes.append(f"Minecraft {mc} (newest release of the {p['minecraft_version']} line), "
                 f"Fabric loader {p['loader_version']} (installer {installer})")
    notes.append(f"fabric-api {api[0]} (newest for {mc}; compiled against {p.get('fabric_api_version')})")
    notes.append(f"under test: {os.path.basename(jar)}")
    for n in notes:
        print("  " + n, flush=True)

    s, ready = boot(a.workdir, results, "alone", r"\[pathways\] initialized \(sanctuary detected: false\)",
                    a.boot_timeout)
    try:
        if ready:
            run_alone(s, results, a.workdir)
    finally:
        s.stop()
        logs += ["==== alone ===="] + s.log
        fatal += s.fatal

    if a.sanctuary_jar:
        sdir = a.workdir + "-sanctuary"
        setup(sdir, [jar, a.sanctuary_jar], p, mc)
        TESTED["sanctuary"] = re.sub(r"^sanctuary-|\.jar$", "", os.path.basename(a.sanctuary_jar))
        notes.append(f"bridge tested against {os.path.basename(a.sanctuary_jar)}")
        s, ready = boot(sdir, results, "sanctuary", r"\[pathways\] initialized \(sanctuary detected: true\)",
                        a.boot_timeout)
        try:
            if ready:
                run_sanctuary(s, results, sdir)
        finally:
            s.stop()
            logs += ["==== with sanctuary ===="] + s.log
            fatal += s.fatal
    else:
        notes.append("sanctuary boot skipped (no --sanctuary-jar)")

    results.append(("no mixin / tick / entrypoint errors in the logs", len(fatal) == 0, "; ".join(fatal[:3])))
    os.makedirs(a.workdir, exist_ok=True)
    with open(os.path.join(a.workdir, "console.log"), "w") as f:
        f.write("\n".join(logs))
    failed = [r for r in results if r[1] is False]
    ran = [r for r in results if r[1] is not None]
    # Machine-readable record of this run for README badges (scripts/publish_badges.py).
    with open(os.path.join(a.workdir, "versions.json"), "w") as f:
        json.dump({
            "mod": p.get("mod_version"), "minecraft": p.get("minecraft_version"),
            "minecraft_tested": TESTED.get("minecraft_tested"),
            "loader": p.get("loader_version"), "fabric_api_compiled": p.get("fabric_api_version"),
            "fabric_api_tested": TESTED.get("fabric_api_tested"), "sanctuary": TESTED.get("sanctuary"),
            "passed": len(ran) - len(failed), "total": len(ran),
            "sha": os.environ.get("GITHUB_SHA"), "branch": os.environ.get("GITHUB_REF_NAME"),
        }, f, indent=2)
    if failed:
        print("---- last 80 console lines ----")
        print("\n".join(logs[-80:]))
        print("---- end ----", flush=True)
    md = [f"### Server test: Minecraft {TESTED.get('minecraft_tested')}, pathways {p.get('mod_version')}", ""]
    md += [f"- {n}" for n in notes] + ["", "| Check | Result |", "|---|---|"]
    md += [f"| {n} | {'✅' if ok else '❌ ' + d.replace('|', '/')[:200]} |" for n, ok, d in results]
    md += ["", f"**{len(ran) - len(failed)}/{len(ran)} passed**"]
    print("\n".join(md))
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as f:
            f.write("\n".join(md) + "\n")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
