"""Attribute IPv4 literals in compiled DEX to the classes that reference them.

This uses only public DEX metadata; raw addresses are never logged. Unattributed
strings fail closed in the caller. No third-party disassembler is required.
"""
import struct


def _uleb128(data, offset):
    value = 0
    for shift in range(0, 35, 7):
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7F) << shift
        if not (byte & 0x80):
            return value, offset
    raise ValueError("Invalid DEX LEB128")


def literal_owners(dex, is_unreviewed):
    """Return {string_index: set(class_descriptors)} for rejected literals.

    Any string without a proved owner remains an empty set and must be blocked.
    """
    count, string_off = struct.unpack_from("<II", dex, 56)
    type_count, type_off = struct.unpack_from("<II", dex, 64)
    class_count, class_off = struct.unpack_from("<II", dex, 96)
    strings = []
    for index in range(count):
        position, = struct.unpack_from("<I", dex, string_off + 4 * index)
        _, position = _uleb128(dex, position)
        end = dex.index(b"\0", position)
        strings.append(dex[position:end].decode("utf-8", errors="replace"))
    suspects = {i for i, string in enumerate(strings) if is_unreviewed(string)}
    owners = {index: set() for index in suspects}
    if not suspects:
        return owners
    for class_index in range(class_count):
        base = class_off + class_index * 32
        type_index, = struct.unpack_from("<I", dex, base)
        if type_index >= type_count:
            raise ValueError("Invalid DEX class type index")
        descriptor_index, = struct.unpack_from("<I", dex, type_off + type_index * 4)
        class_name = strings[descriptor_index]
        class_data_off, = struct.unpack_from("<I", dex, base + 24)
        if not class_data_off:
            continue
        counts = []
        position = class_data_off
        for _ in range(4):
            amount, position = _uleb128(dex, position)
            counts.append(amount)
        for _ in range(counts[0] + counts[1]):
            _, position = _uleb128(dex, position)
            _, position = _uleb128(dex, position)
        for _ in range(counts[2] + counts[3]):
            _, position = _uleb128(dex, position)
            _, position = _uleb128(dex, position)
            code_off, position = _uleb128(dex, position)
            if not code_off:
                continue
            insn_size, = struct.unpack_from("<I", dex, code_off + 12)
            start = code_off + 16
            if start + insn_size * 2 > len(dex):
                raise ValueError("DEX instruction buffer out of bounds")
            insns = struct.unpack_from("<" + str(insn_size) + "H", dex, start)
            for offset, word in enumerate(insns):
                opcode = word & 0xFF
                if opcode == 0x1A and offset + 1 < insn_size:
                    ref = insns[offset + 1]
                elif opcode == 0x1B and offset + 2 < insn_size:
                    ref = insns[offset + 1] | (insns[offset + 2] << 16)
                else:
                    continue
                if ref in suspects:
                    owners[ref].add(class_name)
    return owners
