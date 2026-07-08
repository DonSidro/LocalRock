"""Generate golden Roborock-protocol byte vectors for VacLocal commonTest.

Run with the local_roborock_server_sidon venv's python so python-roborock is on path.

Prints a small JSON document with deterministic encode outputs for several
fixed (localKey, ts, seq, random, protocol, payload) tuples. The Kotlin
codec is pinned against these.
"""

from __future__ import annotations

import binascii
import hashlib
import json
import sys

from roborock.protocol import (
    SALT,
    Utils,
    create_local_decoder,
    create_local_encoder,
    create_mqtt_decoder,
    create_mqtt_encoder,
)
from roborock.roborock_message import RoborockMessage, RoborockMessageProtocol


def hexs(b: bytes) -> str:
    return binascii.hexlify(b).decode()


def round_trip_mqtt(local_key: str, ts: int, seq: int, random: int, payload: bytes) -> dict:
    msg = RoborockMessage(
        protocol=RoborockMessageProtocol.RPC_REQUEST,
        payload=payload,
        version=b"1.0",
        seq=seq,
        random=random,
        timestamp=ts,
    )
    enc = create_mqtt_encoder(local_key)
    dec = create_mqtt_decoder(local_key)
    encoded = enc(msg)
    decoded = dec(encoded)
    assert len(decoded) == 1
    assert decoded[0].payload == payload
    # token = md5(encoded_ts + local_key + SALT)
    token = Utils.md5(Utils.encode_timestamp(ts) + local_key.encode() + SALT)
    return {
        "kind": "mqtt",
        "local_key": local_key,
        "ts": ts,
        "seq": seq,
        "random": random,
        "protocol": int(RoborockMessageProtocol.RPC_REQUEST),
        "payload_hex": hexs(payload),
        "encoded_hex": hexs(encoded),
        "token_hex": hexs(token),
        "encoded_ts": Utils.encode_timestamp(ts).decode(),
    }


def round_trip_local(local_key: str, ts: int, seq: int, random: int, payload: bytes) -> dict:
    msg = RoborockMessage(
        protocol=RoborockMessageProtocol.RPC_REQUEST,
        payload=payload,
        version=b"1.0",
        seq=seq,
        random=random,
        timestamp=ts,
    )
    enc = create_local_encoder(local_key)
    dec = create_local_decoder(local_key)
    encoded = enc(msg)
    decoded = dec(encoded)
    assert len(decoded) == 1
    assert decoded[0].payload == payload
    token = Utils.md5(Utils.encode_timestamp(ts) + local_key.encode() + SALT)
    return {
        "kind": "local",
        "local_key": local_key,
        "ts": ts,
        "seq": seq,
        "random": random,
        "protocol": int(RoborockMessageProtocol.RPC_REQUEST),
        "payload_hex": hexs(payload),
        "encoded_hex": hexs(encoded),
        "token_hex": hexs(token),
        "encoded_ts": Utils.encode_timestamp(ts).decode(),
    }


def encode_ts_table() -> list[dict]:
    # Pin the hex-permutation function on a handful of inputs.
    return [
        {"ts": ts, "encoded": Utils.encode_timestamp(ts).decode()}
        for ts in (0x00000000, 0x00000001, 0x01234567, 0x89abcdef, 0xdeadbeef, 0x65f3a200)
    ]


def token_table() -> list[dict]:
    cases = [
        ("0123456789abcdef", 0x65F3A200),
        ("local_key", 0x00000001),
        ("zzzzzzzzzzzzzzzz", 0x10203040),
    ]
    out = []
    for lk, ts in cases:
        tok = Utils.md5(Utils.encode_timestamp(ts) + lk.encode() + SALT)
        out.append({"local_key": lk, "ts": ts, "token_hex": hexs(tok)})
    return out


def main() -> int:
    out = {
        "salt_hex": hexs(SALT),
        "encode_timestamp": encode_ts_table(),
        "tokens": token_table(),
        "messages": [
            round_trip_mqtt(
                local_key="0123456789abcdef",
                ts=0x65F3A200,
                seq=42,
                random=12345,
                payload=b'{"id":1,"method":"get_status","params":[]}',
            ),
            round_trip_local(
                local_key="local_key",
                ts=0x65F3A300,
                seq=1,
                random=123,
                payload=b"test_payload",
            ),
            round_trip_local(
                local_key="0123456789abcdef",
                ts=0x65F3A400,
                seq=7,
                random=99,
                payload=b'{"id":2,"method":"app_start","params":[]}',
            ),
        ],
    }
    json.dump(out, sys.stdout, indent=2)
    return 0


if __name__ == "__main__":
    sys.exit(main())
