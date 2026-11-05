"""Placa simulada: habla el mismo protocolo que arduino/AeroSmartHome/AeroSmartHome.ino.

Sirve para probar la app en el emulador de Android Studio sin la Wemos.
  1. En local.properties:  aerosmart.host=10.0.2.2   (el emulador ve tu PC en esa IP)
  2. En una terminal:      set AEROSMART_LINK_KEY=<la misma clave>   (PowerShell: $env:AEROSMART_LINK_KEY="...")
                           python placa_simulada.py
  3. En la app: Conectar > Placa AeroSmart.
"""
import hashlib
import hmac
import os
import random
import re
import socket
import sys
import threading
import time

KEY_HEX = os.environ.get("AEROSMART_LINK_KEY", "")
if not re.fullmatch(r"[0-9a-fA-F]{32}|[0-9a-fA-F]{64}", KEY_HEX):
    sys.exit("Falta AEROSMART_LINK_KEY (32 o 64 caracteres hexadecimales).")
KEY = bytes.fromhex(KEY_HEX)
BODY = re.compile(r"^[A-Z]+:[A-Za-z0-9,.-]{0,60}$")


def mac(direction, session, seq, body):
    msg = f"{direction}|{session}|{seq}|{body}".encode("ascii")
    return hmac.new(KEY, msg, hashlib.sha256).hexdigest()[:16]


def serve(conn):
    f = conn.makefile("rwb")
    st = {"app": "", "dev": "", "tx": 0, "rx": 0, "sess": False, "vent": 0, "fan": 60, "t": 25, "h": 55, "pir": 0}
    lock = threading.Lock()
    alive = True

    def send(seq, body):
        with lock:
            f.write(f"{seq}|{body}|{mac('D', st['app'] + st['dev'], seq, body)}\n".encode())
            f.flush()

    def nxt(body):
        st["tx"] += 1
        send(st["tx"], body)

    def data():
        nxt(f"DATA:{st['t']},{st['h']},{st['pir']},{st['vent']},{st['fan'] if st['vent'] else 0}")

    def sensors():
        while alive:
            time.sleep(2)
            if not st["sess"]:
                continue
            st["t"] = max(18, min(34, st["t"] + random.choice((-1, 0, 1))))
            st["h"] = max(30, min(90, st["h"] + random.choice((-2, 0, 2))))
            if random.random() < 0.15:
                st["pir"] ^= 1
            try:
                data()
            except OSError:
                return

    threading.Thread(target=sensors, daemon=True).start()
    try:
        for raw in f:
            line = raw.decode("ascii", "replace").strip()
            parts = line.split("|")
            if len(parts) != 3 or not BODY.match(parts[1]) or not parts[0].isdigit():
                print("descartado (formato)")
                continue
            seq, body, m = int(parts[0]), parts[1], parts[2].lower()
            if body.startswith("HELLO:"):
                nonce = body[6:]
                if seq != 0 or not hmac.compare_digest(m, mac("A", nonce, 0, body)):
                    print("HELLO con clave incorrecta")
                    continue
                st.update(app=nonce, dev=os.urandom(8).hex(), tx=0, rx=0, sess=True)
                send(0, "WELCOME:" + st["dev"])
                print("sesión segura iniciada")
                data()
                continue
            if not st["sess"] or not hmac.compare_digest(m, mac("A", st["app"] + st["dev"], seq, body)):
                print("descartado (MAC inválido)")
                continue
            if seq <= st["rx"]:
                print("descartado (repetido)")
                continue
            st["rx"] = seq
            print("orden:", body)
            if body.startswith("PING:"):
                nxt("PONG:" + body[5:])
            elif body in ("VENT:0", "VENT:1"):
                st["vent"] = int(body[5])
                nxt(f"ACK:{seq}")
                data()
            elif body.startswith("FAN:"):
                st["fan"] = max(0, min(100, int(body[4:])))
                nxt(f"ACK:{seq}")
                data()
            elif body.startswith("MODE:"):
                nxt(f"ACK:{seq}")
            else:
                nxt("ERR:CMD")
    except OSError:
        pass
    finally:
        alive = False
        f.close()
        conn.close()
        print("teléfono desconectado")


def main():
    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", 3333))
    srv.listen(1)
    print("Placa simulada escuchando en el puerto 3333")
    while True:
        conn, addr = srv.accept()
        print("conexión desde", addr[0])
        serve(conn)


if __name__ == "__main__":
    main()
