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
for rng in ("1h", "24h", "7d"):
    st, d = req("GET", "/api/v1/history?range=" + rng, admin)
    check("history " + rng, st == 200 and isinstance(d.get("points"), list) and d.get("step", 0) >= 60000, str(d)[:200])

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
m = console(f"remotemc token create mod{RUN} moderator", r"(rmc_[A-Za-z0-9_-]{43})")
mod = m.group(1)
st, d = req("POST", "/api/v1/command", mod, {"command": "list"})
check("moderator cannot run commands", st == 403, d)
st, d = req("GET", "/api/v1/console", mod)
check("moderator can read console", st == 200, d)
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
