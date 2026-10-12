"""Player-facing checks with a mineflayer bot. usage: playertest.py <server-dir> <admin-token> [mc-version]"""
import json, os, subprocess, sys, time, http.client, ssl
DIR, TOKEN = sys.argv[1], sys.argv[2]
VERSION = sys.argv[3] if len(sys.argv) > 3 else ""
BOT = os.path.join(sys.path[0], "bot", "bot.js")
ctx = ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE
results = []
def check(name, cond, info=""):
    results.append(bool(cond)); print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {info}"))
def req(method, path, body=None):
    c = http.client.HTTPSConnection("127.0.0.1", 8765, context=ctx, timeout=15)
    c.request(method, path, body=json.dumps(body) if body is not None else None, headers={"Authorization": "Bearer " + TOKEN})
    r = c.getresponse(); return r.status, json.loads(r.read() or "{}")

import tempfile
out = tempfile.NamedTemporaryFile("w+", suffix=".log", delete=False)
bot = subprocess.Popen(["node", BOT, "Steve"] + ([VERSION] if VERSION else []), stdin=subprocess.PIPE, stdout=out, stderr=subprocess.STDOUT, text=True)
def bot_log():
    out.flush(); return open(out.name).read()
def wait_for(text, timeout=20):
    end = time.time() + timeout
    while time.time() < end:
        if text in bot_log(): return True
        time.sleep(0.3)
    return False

check("bot joined", wait_for("SPAWNED", 40), bot_log()[-500:])
time.sleep(2.5)
st, d = req("GET", "/api/v1/players")
p = (d.get("players") or [{}])[0]
check("player listed with details", st == 200 and p.get("name") == "Steve" and "maxHealth" in p and "ping" in p, d)
st, d = req("GET", "/api/v1/chat?since=0")
types = [(m["type"], m["player"], m["message"]) for m in d.get("messages", [])]
check("join + chat captured", ("join", "Steve", "") in types and ("chat", "Steve", "hello from bot") in types, types)
st, d = req("POST", "/api/v1/players/Steve/message", {"message": "psst"})
check("private message delivered", st == 200 and wait_for("psst", 5), (st, d))
st, d = req("POST", "/api/v1/players/Steve/gamemode", {"mode": "creative"})
check("game mode changed", st == 200 and wait_for("GAMEMODE creative", 5), (st, d))
check("playtime reported", isinstance(p.get("playtime"), int) and p["playtime"] >= 0, p)
req("POST", "/api/v1/command", {"command": "tp Steve 120 100 120"})
time.sleep(1.5)
st, d = req("POST", "/api/v1/players/Steve/teleport", {"to": "spawn"})
time.sleep(1.5)
_, d2 = req("GET", "/api/v1/players")
q = (d2.get("players") or [{}])[0]
sp = req("GET", "/api/v1/worlds/" + q.get("world", "world"))[1].get("spawn", {})
check("teleport to spawn", st == 200 and abs(q.get("x", 999) - sp.get("x", 0)) < 16 and abs(q.get("z", 999) - sp.get("z", 0)) < 16, (st, d, q, sp))
st, d = req("POST", "/api/v1/players/Steve/teleport", {"to": "Nobody"})
check("teleport to offline player refused", st == 404, d)
st, d = req("POST", "/api/v1/players/Steve/heal", {})
check("heal", st == 200, d)
st, d = req("POST", "/api/v1/players/Steve/op", {})
time.sleep(1)
st2, d2 = req("GET", "/api/v1/players")
check("op", st == 200 and d2["players"][0]["op"], (d, d2))
bot.stdin.write("/rmc pair viewer\n"); bot.stdin.flush()
time.sleep(3)
bot.stdin.write("inv\n"); bot.stdin.flush()
check("pairing map in inventory", wait_for("filled_map", 5), bot_log()[-400:])
check("pair messages shown to player", "Pairing code" in bot_log() and "remotemc://" in bot_log(), bot_log()[-600:])
st, d = req("POST", "/api/v1/players/Steve/kick", {"reason": "Bye from test"})
check("kick with reason", st == 200 and wait_for("Bye from test", 8), (st, d, bot_log()[-300:]))
time.sleep(1.5)
st, d = req("GET", "/api/v1/chat?since=0")
check("quit captured", any(m["type"] == "quit" for m in d.get("messages", [])), d)
bot.kill()
print(f"\n{sum(results)}/{len(results)} passed"); sys.exit(0 if all(results) else 1)
