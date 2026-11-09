"""Synthetic DEX fixtures: verify provenance without logging an IPv4 address."""
import struct
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from dex_ip_audit import literal_owners
from privacy_guard import IP


def synthetic_dex(descriptor, address):
    dex = bytearray(512)
    dex[0:8] = b"dex\n039\x00"
    struct.pack_into("<II", dex, 56, 2, 112)
    struct.pack_into("<II", dex, 64, 1, 120)
    struct.pack_into("<II", dex, 96, 1, 124)
    struct.pack_into("<II", dex, 112, 260, 400)
    struct.pack_into("<I", dex, 120, 0)
    struct.pack_into("<I", dex, 124, 0)
    struct.pack_into("<I", dex, 124 + 24, 160)
    dex[160:168] = b"\x00\x00\x01\x00\x00\x01\xc8\x01"
    struct.pack_into("<HHHHIIHH", dex, 200, 1, 1, 0, 0, 0, 2, 0x001A, 1)
    for offset, string in ((260, descriptor), (400, address)):
        encoded = string.encode("utf-8")
        assert len(encoded) < 127
        dex[offset] = len(encoded)
        dex[offset + 1:offset + 1 + len(encoded)] = encoded
        dex[offset + len(encoded) + 1] = 0
    return bytes(dex)


class DexLiteralOwnershipTests(unittest.TestCase):
    def test_app_owned_ipv4_cannot_masquerade_as_vendor_constant(self):
        address = ".".join(("192", "168", "77", "12"))
        owner = "Lio/github/josxtm/mangossh/core/Example;"
        references = literal_owners(
            synthetic_dex(owner, address), lambda value: bool(IP.search(value))
        )
        self.assertEqual(references, {1: {owner}})
        self.assertNotIn(address, str(references))

    def test_dependency_literal_is_attributed_to_its_own_class(self):
        address = ".".join(("198", "51", "100", "5"))
        owner = "Lorg/bouncycastle/example/Example;"
        references = literal_owners(
            synthetic_dex(owner, address), lambda value: bool(IP.search(value))
        )
        self.assertEqual(references, {1: {owner}})

    def test_non_sensitive_literals_return_no_findings(self):
        dex = synthetic_dex("Lorg/example/Example;", "ordinary-text")
        self.assertEqual(literal_owners(dex, lambda value: bool(IP.search(value))), {})


if __name__ == "__main__":
    unittest.main()
