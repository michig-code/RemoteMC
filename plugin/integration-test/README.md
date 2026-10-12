# Integration tests

End-to-end tests against a real server. They were used to verify every release on
Paper 1.20.6, Paper 1.21.4, Paper 26.3, Folia 1.21.4 and Spigot 1.21.4 (60/60 API checks on each).

```bash
cd plugin && mvn package                         # build the plugin
cd integration-test
./mkserver.sh paper 1.21.4 /tmp/rmc-paper        # download server + install plugin
echo "online-mode=false" >> /tmp/rmc-paper/server.properties
./srv.sh start /tmp/rmc-paper &                  # start it (java on PATH; Java 25 for 26.x)
./srv.sh wait /tmp/rmc-paper

python3 apitest.py /tmp/rmc-paper                # 60 API checks (TLS pinning, pairing, roles, SSE, audit, lockout)
(cd bot && npm install)
./srv.sh cmd /tmp/rmc-paper "rmc token create tester admin"   # copy the token from the console
python3 playertest.py /tmp/rmc-paper <token> 1.21.4            # 11 checks with a bot player (map QR, kick, heal, chat…)
```

`apitest.py` ends with the lockout test, which locks out 127.0.0.1 for the configured time;
set `SKIP_LOCKOUT=1` to keep using the same server afterwards.

The Android app has a matching test: `./gradlew testDebugUnitTest -Prmc.it.link="remotemc://…"`
pairs the app's network code against a running server.
