#!/usr/bin/env python3
"""Encrypt CI-only suspect DEX IPv4 entries; never publish raw values or keys."""
import base64
from pathlib import Path
import re
import struct
import subprocess
import sys
import tempfile
import zipfile
from privacy_guard import IP

PUBLIC_KEY = """-----BEGIN PUBLIC KEY-----
MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAlrsphkFqziwiijIldynd
D8nvu5C6OzYLLRIkqocyBKCzDvWZrSxEWrnPjaWW7o7AV53wj3xMYQkmXRtZ8AmG
ictEXl82J6zbqrf2CvDhE/PxmlObwXz7bb9LgfNm4VKek/khhpwLOndKd787X880
PhNH9s1TuwfYEWOAsVJPv6fZcl3fAbE+A1YMMe8KjSHVIy7FrojCrMnVvbqyrXSY
kxxYfI8RKZBV5dlNNtnSo0sNY0QeW6HZ931lymRlS1u9z6yaWkk82uF9JuwPVIv3
nhUlrvhdtXwoFzAN1XvLfWVf0GO0coW0c65uUGuTvLcA+8NmTxyyRROQh82lhZby
ZIXy5fEs6ORJUOiVUBcFXIkF6ivoCqlZftmPwbaLJkk8ls/3rgpwo1wOI7+VTfpp
LJ96yJ2BZ0Hmbn8BBILlLRjnIHGm0Gl//H+NDNYQ+PJtEdxKD/ftV07XB+8v6Cf4
/h7K7q/gO0k4WgjgfSnLqDeAOxq+f8erGP+Tebtz0LWdAgMBAAE=
-----END PUBLIC KEY-----
"""

def read_uleb(data, position):
    for i in range(5):
        byte = data[position + i]
        if not (byte & 128):
            return position + i + 1
    raise ValueError("Invalid DEX string length")

def candidates(apk):
    results = set()
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if not re.fullmatch(r"classes\d*\.dex", name):
                continue
            dex = archive.read(name)
            count, table = struct.unpack_from("<II", dex, 56)
            for index in range(count):
                position, = struct.unpack_from("<I", dex, table + index * 4)
                position = read_uleb(dex, position)
                end = dex.index(b"\0", position)
                results.update(IP.findall(dex[position:end].decode("utf-8", errors="replace")))
    return sorted(results)

def main(apk, output):
    if not Path(apk).is_file():
        print("Encrypted diagnostic skipped: APK was not generated")
        return
    with tempfile.TemporaryDirectory() as directory:
        key = Path(directory) / "public.pem"
        key.write_text(PUBLIC_KEY, encoding="ascii")
        encrypted = []
        for candidate in candidates(apk):
            result = subprocess.run([
                "openssl", "pkeyutl", "-encrypt", "-pubin", "-inkey", str(key),
                "-pkeyopt", "rsa_padding_mode:oaep",
                "-pkeyopt", "rsa_oaep_md:sha256",
                "-pkeyopt", "rsa_mgf1_md:sha256",
            ], input=candidate.encode("ascii"), capture_output=True, check=True)
            encrypted.append(base64.b64encode(result.stdout).decode("ascii"))
    Path(output).write_text("\n".join(encrypted) + "\n", encoding="ascii")
    print("Encrypted forensic entries created; no IPv4 values disclosed")

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
