#!/bin/bash
# usage: srv.sh start <dir> [java]   – runs server in foreground (use in background task)
#        srv.sh cmd <dir> <command>  – sends a console command
#        srv.sh wait <dir>           – waits for "Done ("
set -u
dir=$2
case $1 in
  start)
    java=${3:-java}
    cd "$dir"; : > server.log; rm -f stdin.fifo; mkfifo stdin.fifo
    (sleep 100000 > stdin.fifo &)
    exec $java -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 -Xmx1500M -jar server.jar --nogui < stdin.fifo > server.log 2>&1 ;;
  cmd) echo "$3" > "$dir/stdin.fifo" ;;
  wait) for i in $(seq 1 120); do grep -q 'Done (' "$dir/server.log" 2>/dev/null && exit 0; grep -q 'Exception in thread "main"' "$dir/server.log" 2>/dev/null && exit 1; sleep 2; done; exit 1 ;;
esac
