# Does adbd on this Fire Stick accept an ADB connection from the Fire Stick itself?
p() { printf 'CNXN\0\0\0\1\0\20\0\0\7\0\0\0\62\2\0\0\274\261\247\261host::\0' | toybox nc -w 3 "$1" 5555 2>&1 | head -c 4; echo; }
echo "adb_enabled=$(settings get global adb_enabled) tcp_port=$(getprop service.adb.tcp.port)/$(getprop persist.adb.tcp.port)"
echo "listening on 5555 (hex 15B3):"; grep -i ':15B3 ' /proc/net/tcp /proc/net/tcp6 | awk '{print $2, $4}'
echo "probe 127.0.0.1 -> [$(p 127.0.0.1)]"
for ip in $(ip -4 addr show wlan0 2>/dev/null | grep -o 'inet [0-9.]*' | cut -d' ' -f2) $(ip -4 addr show eth0 2>/dev/null | grep -o 'inet [0-9.]*' | cut -d' ' -f2); do
  echo "probe $ip -> [$(p $ip)]"
done
