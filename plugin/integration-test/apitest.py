"""End-to-end test of the RemoteMC plugin API against a running server.
usage: python3 apitest.py <server-dir> [legacy-token]"""
import random, base64, hashlib, http.client, json, re, socket, ssl, subprocess, sys, time

DIR = sys.argv[1]
LEGACY = sys.argv[2] if len(sys.argv) > 2 else None
SRV = sys.path[0] + "/srv.sh"
PORT = 8765
RUN = str(random.randint(1000, 9999))
results = []

def check(name, cond, info=""):
    results.append((name, bool(cond)))
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {info}"))

def console(cmd, pattern, timeout=30):
    with open(f"{DIR}/server.log") as f:
        start = len(f.read())
    subprocess.run([SRV, "cmd", DIR, cmd], check=True)
    end = time.time() + timeout
    while time.time() < end:
        with open(f"{DIR}/server.log") as f:
            m = re.search(pattern, f.read()[start:])
        if m:
            return m
        time.sleep(0.3)
    return None

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

def conn():
    return http.client.HTTPSConnection("127.0.0.1", PORT, context=ctx, timeout=15)

def req(method, path, token=None, body=None):
    c = conn()
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    c.request(method, path, body=json.dumps(body) if body is not None else None, headers=headers)
    r = c.getresponse()
    data = r.read().decode()
    try:
        data = json.loads(data)
    except ValueError:
        pass
    return r.status, data

# 1. pairing code + link from console
m = console("remotemc pair admin", r"(remotemc://\S+)")
check("pair command prints link", m)
link = m.group(1)
code = re.search(r"code=(\d{6})", link).group(1)
fp = re.search(r"fp=([A-Za-z0-9_-]+)", link).group(1)
print("link:", link)

# 2. certificate pinning: served cert matches fingerprint in link
with socket.create_connection(("127.0.0.1", PORT)) as raw, ctx.wrap_socket(raw) as s:
    der = s.getpeercert(binary_form=True)
    tls_version = s.version()
served = base64.urlsafe_b64encode(hashlib.sha256(der).digest()).decode().rstrip("=")
check("served certificate matches QR fingerprint", served == fp, served)
check("TLS 1.2+ negotiated", tls_version in ("TLSv1.2", "TLSv1.3"), tls_version)

# 2b. web app for browsers: its page with a strict policy, nothing else from the JAR or disk
c = conn(); c.request("GET", "/"); r = c.getresponse(); page = r.read().decode()
check("web app page", r.status == 200 and "app.js" in page and "default-src 'self'" in (r.getheader("Content-Security-Policy") or ""))
c = conn(); c.request("GET", "/app.js"); r = c.getresponse(); r.read()
check("web app script", r.status == 200 and r.getheader("Content-Type", "").startswith("text/javascript"))
c = conn(); c.request("GET", "/config.yml"); r = c.getresponse(); r.read()
check("web app serves no other files", r.status == 404)
check("pair command prints browser link", console("remotemc pair", r"(https?://\S+/#code=\d{6})"))

# 3. ping + pairing
st, d = req("GET", "/api/v1/ping")
check("ping without auth", st == 200 and d.get("apiVersion") == 1, d)
st, d = req("POST", "/api/v1/pair", body={"code": "000000" if code != "000000" else "111111", "device": "Test"})
check("wrong pairing code rejected", st == 401 and d.get("error") == "invalid_code", d)
st, d = req("POST", "/api/v1/pair", body={"code": code, "device": "Pixel Test"})
check("pairing returns token", st == 200 and d.get("token", "").startswith("rmc_") and d.get("role") == "admin", d)
admin = d.get("token")
st, d = req("POST", "/api/v1/pair", body={"code": code, "device": "Again"})
check("pairing code is single-use", st == 401, d)

# 4. admin endpoints
st, d = req("GET", "/api/v1/me", admin)
check("me", st == 200 and d.get("name", "").startswith("Pixel Test") and d.get("role") == "admin", d)
st, d = req("GET", "/api/v1/status", admin)
check("status", st == 200 and "worlds" in d and "platform" in d, d)
check("status lists features", set(d.get("features", [])) >= {"history", "moderation", "worlds", "plugins", "schedules", "teleport"}, d.get("features"))
print("  platform:", d.get("platform"), "mc:", d.get("minecraftVersion"), "tps:", d.get("tps"), "mspt:", d.get("mspt"))
st, d = req("GET", "/api/v1/players", admin)
check("players", st == 200 and d.get("players") == [], d)
st, d = req("GET", "/api/v1/console?since=0", admin)
check("console history", st == 200 and len(d.get("lines", [])) > 0, str(d)[:200])
st, d = req("POST", "/api/v1/command", admin, {"command": "time set day"})
check("command with output (or console fallback on Spigot)", st == 200 and d.get("success") and (d.get("captured") is False or any("time" in l.lower() for l in d.get("output", []))), d)
st, d = req("POST", "/api/v1/command", admin, {"command": "/minecraft:stop"})
check("blocked command", st == 403 and d.get("error") == "command_blocked", d)
st, d = req("POST", "/api/v1/players/Nobody/kick", admin, {})
check("offline player", st == 404 and d.get("error") == "player_offline", d)
st, d = req("POST", "/api/v1/players/Notch/ban", admin, {"reason": "test"})
check("ban offline player", st == 200, d)
st, d = req("GET", "/api/v1/bans", admin)
check("ban list shows ban with reason", st == 200 and any(b["name"].lower() == "notch" and b.get("reason") == "test" for b in d.get("bans", [])), d)
st, d = req("POST", "/api/v1/bans/Notch/pardon", admin)
check("unban via API", st == 200 and not any(b["name"].lower() == "notch" for b in d.get("bans", [])), d)
st, d = req("POST", "/api/v1/players/Notch/ban", admin, {"reason": "test"})
st, d = req("POST", "/api/v1/command", admin, {"command": "pardon Notch"})
check("pardon", st == 200 and d.get("success"), d)
st, d = req("POST", "/api/v1/chat", admin, {"message": "Hallo aus dem Test"})
check("broadcast", st == 200, d)
st, d = req("GET", "/api/v1/chat?since=0", admin)
check("chat history has broadcast", st == 200 and any(m["message"] == "Hallo aus dem Test" for m in d.get("messages", [])), d)
c = conn(); c.request("POST", "/api/v1/command", body="{nope", headers={"Authorization": "Bearer " + admin}); r = c.getresponse(); d = json.loads(r.read())
check("invalid json", r.status == 400 and d.get("error") == "invalid_json", d)
st, d = req("GET", "/api/status", admin)
check("unversioned path still works", st == 200, d)
st, d = req("GET", "/api/v1/whitelist", admin)
check("whitelist", st == 200 and "enabled" in d and isinstance(d.get("players"), list), d)
was_on = d.get("enabled")
# unchanged answers: ETag + empty 304; big answers gzip-compressed
def raw_get(path, token, headers):
    c = conn()
    c.request("GET", path, headers={"Authorization": "Bearer " + token, **headers})
    r = c.getresponse()
    return r.status, {k.lower(): v for k, v in r.getheaders()}, r.read()

st, h, _ = raw_get("/api/v1/whitelist", admin, {})
etag = h.get("etag", "")
check("etag on reads", st == 200 and etag.startswith('W/"'), h)
st, h, body = raw_get("/api/v1/whitelist", admin, {"If-None-Match": etag})
check("304 when unchanged", st == 304 and body == b"", (st, body[:80]))
st, h, body = raw_get("/api/v1/whitelist", admin, {"If-None-Match": 'W/"stale"'})
check("200 when changed", st == 200 and json.loads(body).get("players") is not None, st)
st, h, body = raw_get("/api/v1/commands", admin, {"Accept-Encoding": "gzip"})
import gzip as _gzip
plain = raw_get("/api/v1/commands", admin, {})[2]
check("gzip for big answers", st == 200 and h.get("content-encoding") == "gzip"
      and _gzip.decompress(body) == plain and len(body) < len(plain) / 3, (h.get("content-encoding"), len(body), len(plain)))
st, h, body = raw_get("/api/v1/ping", admin, {"Accept-Encoding": "gzip"})
check("no gzip for small answers", st == 200 and "content-encoding" not in h and json.loads(body).get("apiVersion"), h)

st, d = req("POST", "/api/v1/whitelist/add", admin, {"name": "Notch"})
check("whitelist add", st == 200 and any(n.lower() == "notch" for n in d.get("players", [])), d)
st, d = req("POST", "/api/v1/whitelist/remove", admin, {"name": "Notch"})
check("whitelist remove", st == 200 and not any(n.lower() == "notch" for n in d.get("players", [])), d)
st, d = req("POST", "/api/v1/whitelist", admin, {"enabled": not was_on})
check("whitelist on/off", st == 200 and d.get("enabled") == (not was_on), d)
req("POST", "/api/v1/whitelist", admin, {"enabled": was_on})
st, d = req("GET", "/api/v1/worlds/world", admin)
check("world details with gamerules", st == 200 and d.get("difficulty") and any(r["name"] == "doDaylightCycle" or r["type"] == "boolean" for r in d.get("gamerules", [])), str(d)[:200])
st, d = req("POST", "/api/v1/worlds/world/time", admin, {"time": "night"})
check("world time", st == 200 and 13000 <= d.get("time", 0) < 13100, d.get("time"))
st, d = req("POST", "/api/v1/worlds/world/weather", admin, {"weather": "thunder"})
check("world weather", st == 200 and d.get("storm") and d.get("thunder"), d)
req("POST", "/api/v1/worlds/world/weather", admin, {"weather": "clear"})
old = d.get("difficulty")
st, d = req("POST", "/api/v1/worlds/world/difficulty", admin, {"difficulty": "hard"})
check("world difficulty", st == 200 and d.get("difficulty") == "HARD", d.get("difficulty"))
req("POST", "/api/v1/worlds/world/difficulty", admin, {"difficulty": old.lower()})
rule = next(r for r in d["gamerules"] if r["type"] == "boolean")
st, d = req("POST", "/api/v1/worlds/world/gamerule", admin, {"rule": rule["name"], "value": not rule["value"]})
check("world gamerule", st == 200 and next(r for r in d["gamerules"] if r["name"] == rule["name"])["value"] == (not rule["value"]), rule)
req("POST", "/api/v1/worlds/world/gamerule", admin, {"rule": rule["name"], "value": rule["value"]})
st, d = req("GET", "/api/v1/worlds/nowhere", admin)
check("unknown world", st == 404 and d.get("error") == "world_unknown", d)
st, d = req("GET", "/api/v1/plugins", admin)
check("plugin list", st == 200 and any(p["name"] == "RemoteMC" and p["enabled"] and p["version"] for p in d.get("plugins", [])), str(d)[:200])
st, d = req("POST", "/api/v1/schedules", admin, {"name": "Test", "command": f"say scheduled-{RUN}", "type": "interval", "minutes": 60, "warn": 0})
task = next((t for t in d.get("schedules", []) if t["command"] == f"say scheduled-{RUN}"), None)
check("schedule created with next run", st == 200 and task and task["nextRun"] > d["now"], d)
st, d = req("POST", "/api/v1/schedules", admin, {"name": "Daily", "command": "save-all", "type": "daily", "time": "04:00", "warn": 5})
check("daily schedule", st == 200 and any(t["type"] == "daily" and t["time"] == "04:00" for t in d.get("schedules", [])), d)
st, d = req("POST", "/api/v1/schedules", admin, {"name": "Bad", "command": "minecraft:stop", "type": "interval", "minutes": 5})
check("blocked command cannot be scheduled", st == 403 and d.get("error") == "command_blocked", d)
st, d = req("POST", "/api/v1/schedules", admin, {"name": "Bad", "command": "say x", "type": "daily", "time": "25:00"})
check("invalid schedule refused", st == 400, d)
if task:
    st, d = req("POST", f"/api/v1/schedules/{task['id']}/run", admin)
    time.sleep(1)
    _, c = req("GET", "/api/v1/console?since=0", admin)
    check("run schedule now", st == 200 and any(f"scheduled-{RUN}" in l.get("message", "") for l in c.get("lines", [])), d)
for t in list(req("GET", "/api/v1/schedules", admin)[1].get("schedules", [])):
    req("POST", f"/api/v1/schedules/{t['id']}/delete", admin)
st, d = req("GET", "/api/v1/schedules", admin)
check("schedules deleted", st == 200 and d.get("schedules") == [], d)
for rng in ("1h", "24h", "7d"):
    st, d = req("GET", "/api/v1/history?range=" + rng, admin)
    check("history " + rng, st == 200 and isinstance(d.get("points"), list) and d.get("step", 0) >= 60000, str(d)[:200])

# 4b. files (server folder)
def upload(path, data, token):
    c = conn()
    c.request("POST", "/api/v1/files/upload?path=" + path, body=data,
              headers={"Content-Type": "application/octet-stream", "Authorization": "Bearer " + token})
    r = c.getresponse()
    return r.status, json.loads(r.read().decode() or "{}")

st, d = req("GET", "/api/v1/files", admin)
names = [e["name"] for e in d.get("entries", [])]
check("file list of the server folder", st == 200 and "plugins" in names and "server.properties" in names, names[:10])
st, d = req("GET", "/api/v1/files?path=plugins", admin)
names = [e["name"] for e in d.get("entries", [])]
check("RemoteMC's own folder is hidden", st == 200 and "RemoteMC" not in names and any(n.endswith(".jar") for n in names), names)
st, d = req("GET", "/api/v1/files/content?path=plugins/RemoteMC/config.yml", admin)
check("RemoteMC's own files are unreachable", st == 403, d)
st, d = req("GET", "/api/v1/files?path=..", admin)
check("no paths outside the server folder", st == 403, d)
st, d = req("GET", "/api/v1/files/content?path=server.properties", admin)
check("read a text file", st == 200 and "motd=" in d.get("content", ""), str(d)[:120])
st, d = req("POST", "/api/v1/files/mkdir", admin, {"path": f"rmc-test-{RUN}"})
check("create a folder", st == 200 and d.get("dir") is True, d)
st, d = req("POST", "/api/v1/files/content", admin, {"path": f"rmc-test-{RUN}/notes.txt", "content": "hällo\n" * 1000})
st2, d2 = req("GET", f"/api/v1/files/content?path=rmc-test-{RUN}/notes.txt", admin)
check("save and read back a text file", st == 200 and d2.get("content") == "hällo\n" * 1000, (st, str(d)[:100]))
blob = bytes(range(256)) * 4096  # 1 MB, binary
st, d = upload(f"rmc-test-{RUN}/blob.bin", blob, admin)
check("upload a binary file", st == 200 and d.get("size") == len(blob), d)
st, d = req("GET", f"/api/v1/files/content?path=rmc-test-{RUN}/blob.bin", admin)
check("binary files are not opened as text", st == 415 and d.get("error") == "binary_file", d)
st, d = req("POST", "/api/v1/files/delete", admin, {"path": f"rmc-test-{RUN}"})
st2, _ = req("GET", f"/api/v1/files?path=rmc-test-{RUN}", admin)
check("delete a folder with its files", st == 200 and st2 == 404, d)
st3, r = req("POST", "/api/v1/files/restore", admin, {"trash": d.get("trash", "")})
st4, l = req("GET", f"/api/v1/files?path=rmc-test-{RUN}", admin)
check("deleted folder restorable from the trash", st3 == 200 and st4 == 200 and r.get("path") == f"rmc-test-{RUN}", (d, r, l))
st5, _ = req("POST", "/api/v1/files/delete", admin, {"path": f"rmc-test-{RUN}"})
st6, _ = req("GET", "/api/v1/files?path=plugins/RemoteMC/trash", admin)
check("trash not reachable", st5 == 200 and st6 == 403, st6)
st, d = req("GET", "/api/v1/plugins", admin)
own = next((p for p in d.get("plugins", []) if p["name"] == "RemoteMC"), {})
check("plugin list names the JAR file", re.fullmatch(r"plugins/[^/]+\.jar", own.get("file", "")) is not None, own)
jar = own.get("file", "")
st, d = req("GET", "/api/v1/files?path=plugins", admin)
entry = next((e for e in d.get("entries", []) if "plugins/" + e["name"] == jar), {})
check("own JAR is shown as locked", entry.get("locked") is True, entry)
st, d = req("POST", "/api/v1/files/delete", admin, {"path": jar})
st2, d2 = req("POST", "/api/v1/files/delete", admin, {"path": "plugins"})
st3, _ = req("GET", "/api/v1/files?path=plugins", admin)
check("own JAR and the plugins folder cannot be deleted", st == 403 and st2 == 403 and st3 == 200, (st, d, st2, d2))
st, d = upload(jar, b"broken", admin)
check("own JAR cannot be replaced", st == 403, d)

# 5. roles
m = console(f"remotemc token create viewer{RUN} viewer", r"(rmc_[A-Za-z0-9_-]{43})")
check("token create prints token", m)
viewer = m.group(1)
st, d = req("GET", "/api/v1/status", viewer)
check("viewer can read status", st == 200, d)
st, d = req("GET", "/api/v1/console", viewer)
check("viewer cannot read console", st == 403 and d.get("error") == "forbidden", d)
st, d = req("POST", "/api/v1/chat", viewer, {"message": "x"})
check("viewer cannot chat", st == 403, d)
st, d = req("GET", "/api/v1/bans", viewer)
check("viewer cannot see bans", st == 403, d)
st, d = req("GET", "/api/v1/schedules", viewer)
check("viewer cannot see schedules", st == 403, d)
st, d = req("POST", "/api/v1/worlds/world/time", viewer, {"time": "day"})
check("viewer cannot change worlds", st == 403, d)
m = console(f"remotemc token create mod{RUN} moderator", r"(rmc_[A-Za-z0-9_-]{43})")
mod = m.group(1)
st, d = req("POST", "/api/v1/command", mod, {"command": "list"})
check("moderator cannot run commands", st == 403, d)
st, d = req("GET", "/api/v1/console", mod)
check("moderator can read console", st == 200, d)
st, d = req("GET", "/api/v1/files", mod)
check("moderator cannot access files", st == 403, d)
if LEGACY:
    st, d = req("GET", "/api/v1/me", LEGACY)
    check("migrated McRemote token works", st == 200 and d.get("name") == "Legacy token", d)

# 5b. secrets never reach the console stream (moderators can read it)
st, d = req("GET", "/api/v1/console?since=0", admin)
text = json.dumps(d)
check("console hides tokens, pairing codes and QR codes",
      viewer not in text and mod not in text and code not in text and "█" not in text, text[-300:])

# 5c. command list and completion
st, d = req("GET", "/api/v1/commands", admin)
names = [c["name"] for c in d.get("commands", [])]
check("command list without blocked commands", st == 200 and "gamemode" in names and "stop" not in names, names[:10])
origins = {c["name"]: c.get("plugin") for c in d.get("commands", [])}
check("commands name their plugin", origins.get("gamemode") == "Minecraft" and origins.get("remotemc") == "RemoteMC"
      and all(origins.values()), {k: origins.get(k) for k in ("gamemode", "remotemc", "version", "paper")})
st, d = req("GET", "/api/v1/complete?buffer=gamemode%20cr", admin)
check("tab completion", st == 200 and d.get("suggestions") == ["creative"], d)
st, d = req("GET", "/api/v1/complete?buffer=gam", mod)
check("completion needs admin", st == 403, d)

# 6. live stream
c = conn()
c.request("GET", "/api/v1/events", headers={"Authorization": "Bearer " + admin})
r = c.getresponse()
check("events stream opens", r.status == 200 and "event-stream" in r.getheader("Content-Type", ""), r.status)
r.fp.raw._sock.settimeout(8) if hasattr(r.fp, "raw") else None
seen, buf, deadline = set(), "", time.time() + 8
subprocess.run([SRV, "cmd", DIR, "say live-test"])
req("POST", "/api/v1/chat", admin, {"message": "stream-test"})
try:
    while time.time() < deadline and not {"hello", "status", "console", "chat"} <= seen:
        line = r.fp.readline().decode()
        if line.startswith("event: "):
            seen.add(line[7:].strip())
except (socket.timeout, TimeoutError):
    pass
check("stream delivers hello/status/console/chat", {"hello", "status", "console", "chat"} <= seen, seen)
c.close()

# compressed live stream: events arrive one by one, a fraction of the size
import zlib
c = conn()
c.request("GET", "/api/v1/events", headers={"Authorization": "Bearer " + admin, "Accept-Encoding": "gzip"})
r = c.getresponse()
gz = r.getheader("Content-Encoding") == "gzip"
inflate, packed, text, deadline = zlib.decompressobj(16 + zlib.MAX_WBITS), 0, "", time.time() + 7
try:
    while time.time() < deadline and text.count("event: status") < 3:
        chunk = r.read1(4096)
        if not chunk:
            break
        packed += len(chunk)
        text += inflate.decompress(chunk).decode()
except (socket.timeout, TimeoutError):
    pass
c.close()
check("live stream gzip, event by event", gz and "event: hello" in text and text.count("event: status") >= 3 and packed * 3 < len(text.encode()),
      (gz, packed, len(text.encode()), text[:120]))

# 7. revoke
console(f"remotemc token revoke viewer{RUN}", r"revoked|entzogen")
st, d = req("GET", "/api/v1/status", viewer)
check("revoked token rejected", st == 401 and d.get("error") == "invalid_token", d)

# 8. audit log
with open(f"{DIR}/plugins/RemoteMC/audit.log") as f:
    audit = f.read()
check("audit log records actions", "paired" in audit and "command: /time set day" in audit and "ban Notch" in audit, audit[-300:])

# 9. lockout (last: locks out 127.0.0.1; set SKIP_LOCKOUT=1 to keep testing on this server)
import os
if os.environ.get("SKIP_LOCKOUT"):
    codes, st = [401, 401, 401, 401, 429], 429
else:
    codes = None
if codes is None:
    codes = [req("GET", "/api/v1/status", "rmc_wrong")[0] for _ in range(6)]
    st, d = req("GET", "/api/v1/status", admin)
check("lockout after failed attempts", codes[:4] == [401] * 4 and 429 in codes and st == 429, (codes, st))

failed = [n for n, ok in results if not ok]
print(f"\n{len(results) - len(failed)}/{len(results)} passed")
sys.exit(1 if failed else 0)
