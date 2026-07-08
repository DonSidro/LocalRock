"""Pin Roborock MQTT username/password derivation.

Run with the local_roborock_server_sidon venv so python-roborock is on path.
"""

from __future__ import annotations

import hashlib
import json
import sys
from urllib.parse import urlparse


def md5hex(s: str) -> str:
    return hashlib.md5(s.encode()).hexdigest()


def derive(u: str, s: str, k: str, m: str) -> dict:
    url = urlparse(m)
    return {
        "u": u, "s": s, "k": k, "m": m,
        "host": url.hostname,
        "port": url.port,
        "tls": url.scheme == "ssl",
        "username": md5hex(u + ":" + k)[2:10],
        "password": md5hex(s + ":" + k)[16:],
    }


def main() -> int:
    cases = [
        derive(
            u="1234567890",
            s="abcdef0123456789abcdef0123456789",
            k="zzzzzzzzzzzzzzzz",
            m="ssl://mqtt-eu-3.roborock.com:8883",
        ),
        derive(
            u="42",
            s="0123456789abcdef",
            k="0123456789abcdef",
            m="mqtt://api.roborock.kodraliu.com:1883",
        ),
        derive(
            u="user-id",
            s="session-secret",
            k="some-key-value",
            m="ssl://broker.local:8884",
        ),
    ]
    json.dump({"cases": cases}, sys.stdout, indent=2)
    return 0


if __name__ == "__main__":
    sys.exit(main())
