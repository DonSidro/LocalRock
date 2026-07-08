"""Generate golden cfgwifi frame + packet vectors for Phase 0 tests.

Uses a FIXED RSA keypair so the output is deterministic. The fixed PEM is
also embedded as a Kotlin test fixture so the matching parser test can
decrypt the same bytes without needing platform RSA.
"""

from __future__ import annotations

import binascii
import json
import sys
import zlib

from Crypto.Cipher import AES, PKCS1_v1_5
from Crypto.PublicKey import RSA
from Crypto.Util.Padding import pad


PRE_KEY = "6433df70f5a3a42e"
# Deterministic 1024-bit RSA keypair. Generated once, checked in so test vectors are stable.
FIXED_PRIV_PEM = b"""-----BEGIN RSA PRIVATE KEY-----
MIICWwIBAAKBgQCN8BT5/dAwonKT4LR3dRVedVyjcNmXh28d7N8cE5j9UN84Y5Ih
1DyFVcRCbXKzbT0ccoaxbxCeuiELNbmPNes9SviHa1tmHv4O4fSXNFoedXQGLbmt
yuNu1/ZjuFqTcFvzQFINChSigJXQ6Fg7sTQA1yKrEVzWS4eunp6qD/l56wIDAQAB
AoGAA22sJAm0cKhL2jSQR15loL12+l+scdOMpI1nKjdy95NvgN4u1nBY8eOLdQtN
vPXDhx9hD4IeRac7SxkGHWZxAyXJNG50YqipXgCEU7k2OptGb1KNSIl+SpYnghw4
fAxwgo0GxIP/qoQBuM3eSaUcM4I9E3HyNgSiv2st7xtz4LkCQQC5t8TemLp0zaJK
+TaTmPn2EHuF7g8RXcyz9IC/THaFIt4wQ+j8IUhT33fELvaCcVozZ3OgaDz99NCF
EFSFiyxtAkEAw6buRNNPJ4Q/pp2Kv8/jZ8VyPPafaF3/3XueH+7bmNFsJGFWjGKH
/mwmbZECpvEgLjqzoyTL8hQ7vxk0KgyYtwJAGKpPe0DNgrXM2ChBF1k52XRW83Kq
jaPni/7tGnsNQqFe4Qoj51iEQp9rSKL0Gl7zm+Aheyxc7V2WVBXXI+VYAQJAf6eC
/tUAaEAZZ5SS2uPA9AwXpT8kcuwh7ViiqyMllGfPr0zzM1V2dcIKsKJUoKfi30bd
vixcBO6+rMi9CFJ/sQJATkbYZfyW+6QP0l7C45BFaL3dqmmJKEPA/rxNqxkGb37f
HJ6Wfbwy5vfBQYwLIS+Oi5Tu9AlTvCsLYiQUQ9oFVA==
-----END RSA PRIVATE KEY-----
"""


def crc32(data: bytes) -> int:
    return zlib.crc32(data) & 0xFFFFFFFF


def build_frame(payload: bytes, cmd_id: int) -> bytes:
    buf = b"1.0" + b"\x00\x00\x00\x01" + bytes([0, cmd_id])
    buf += bytes([(len(payload) >> 8) & 0xFF, len(payload) & 0xFF]) + payload
    csum = crc32(buf)
    buf += bytes([(csum >> 24) & 0xFF, (csum >> 16) & 0xFF, (csum >> 8) & 0xFF, csum & 0xFF])
    return buf


def aes_encrypt_json(data: dict, key16: str) -> bytes:
    plaintext = json.dumps(data, separators=(",", ":")).encode()
    return AES.new(key16.encode(), AES.MODE_ECB).encrypt(pad(plaintext, AES.block_size))


def main() -> int:
    key = RSA.import_key(FIXED_PRIV_PEM)
    pub_pem = key.publickey().export_key().decode()
    # PKCS#8 private-key PEM is what JVM's KeyFactory parses without extra ASN.1 work.
    priv_pem_pkcs8 = key.export_key(pkcs=8).decode()

    # Frame-only golden vectors (no encryption involved, raw payload).
    frame_cmd16_empty = build_frame(b"", 16)
    frame_cmd1_hello = build_frame(b"hello world!", 1)
    # A larger payload exercising multi-byte length encoding (> 256 bytes).
    big_payload = bytes(((i * 31) & 0xff) for i in range(512))
    frame_cmd16_big = build_frame(big_payload, 16)

    # Hello packet: AES-ECB(pre_key, json({"id":1,"method":"hello","params":{"app_ver":1,"key":"<pem>"}})) → frame cmd=16
    hello_body = {"id": 1, "method": "hello", "params": {"app_ver": 1, "key": pub_pem}}
    hello_packet = build_frame(aes_encrypt_json(hello_body, PRE_KEY), 16)

    # WiFi config: AES-ECB(session_key, json(body)) → frame cmd=1
    session_key = "abcdef0123456789"
    wifi_body = {
        "u": "1234567890",
        "ssid": "my-home-wifi",
        "token": {
            "r": "api-roborock.example.com/",
            "tz": "America/New_York",
            "s": "S_TOKEN_aaaa",
            "cst": "EST5EDT,M3.2.0,M11.1.0",
            "t": "T_TOKEN_bbbb",
        },
        "passwd": "hunter2!",
        "country_domain": "us",
    }
    wifi_packet = build_frame(aes_encrypt_json(wifi_body, session_key), 1)

    # Synthetic hello response: AES uses pre_key on the request, but the vacuum encrypts its
    # response with RSA-PKCS1v15 (one block per key_size_in_bytes plaintext chunk). Pretend
    # the vacuum returns {"id":1,"method":"hello","params":{"key":"<session_key>"}}.
    response_inner = {"id": 1, "method": "hello", "params": {"key": session_key}}
    response_plaintext = json.dumps(response_inner, separators=(",", ":")).encode()
    # RSA-1024 has 128 byte blocks; PKCS1_v1_5 reserves 11 bytes so each block carries up to 117 plaintext bytes.
    cipher = PKCS1_v1_5.new(key.publickey())
    blocks = []
    block_plaintext_size = key.size_in_bytes() - 11
    for i in range(0, len(response_plaintext), block_plaintext_size):
        chunk = response_plaintext[i : i + block_plaintext_size]
        blocks.append(cipher.encrypt(chunk))
    response_encrypted = b"".join(blocks)
    hello_response_packet = build_frame(response_encrypted, 16)

    out = {
        "pre_key": PRE_KEY,
        "session_key": session_key,
        "fixed_priv_pem_pkcs1": FIXED_PRIV_PEM.decode(),
        "fixed_priv_pem_pkcs8": priv_pem_pkcs8,
        "fixed_pub_pem": pub_pem,
        "frames": {
            "cmd16_empty_hex": frame_cmd16_empty.hex(),
            "cmd1_hello_hex": frame_cmd1_hello.hex(),
            "cmd16_big_hex": frame_cmd16_big.hex(),
        },
        "hello_packet_hex": hello_packet.hex(),
        "wifi_packet_hex": wifi_packet.hex(),
        "wifi_body_json": json.dumps(wifi_body, separators=(",", ":")),
        "hello_response_packet_hex": hello_response_packet.hex(),
        "hello_response_session_key": session_key,
    }
    json.dump(out, sys.stdout, indent=2)
    return 0


if __name__ == "__main__":
    sys.exit(main())
