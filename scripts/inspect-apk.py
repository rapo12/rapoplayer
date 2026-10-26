"""Inspect a built APK's checksum, native ELF load alignment and uncompressed ZIP alignment."""
import hashlib, struct, sys, zipfile
from pathlib import Path
path = Path(sys.argv[1] if len(sys.argv) > 1 else 'app/build/outputs/apk/debug/app-debug.apk')
print('APK:', path)
print('Bytes:', path.stat().st_size)
print('SHA-256:', hashlib.sha256(path.read_bytes()).hexdigest())
problems = []
with zipfile.ZipFile(path) as archive, path.open('rb') as raw:
    libraries = [i for i in archive.infolist() if i.filename.endswith('.so')]
    print('Native libraries:', len(libraries))
    for info in libraries:
        data = archive.read(info)
        is64 = data[4] == 2
        endian = '<' if data[5] == 1 else '>'
        if is64:
            offset = struct.unpack_from(endian+'Q', data, 32)[0]
            size, count = struct.unpack_from(endian+'HH', data, 54)
            alignments = [struct.unpack_from(endian+'Q', data, offset+i*size+48)[0] for i in range(count)
                          if struct.unpack_from(endian+'I', data, offset+i*size)[0] == 1]
            elf_ok = bool(alignments) and min(alignments) >= 16384
        else:
            alignments = []; elf_ok = True
        raw.seek(info.header_offset)
        header = raw.read(30)
        name_len, extra_len = struct.unpack_from('<HH', header, 26)
        payload = info.header_offset + 30 + name_len + extra_len
        zip_ok = info.compress_type != zipfile.ZIP_STORED or payload % 16384 == 0
        print(info.filename, 'ELF64='+str(is64), 'LOAD-align='+str(alignments), 'ZIP-16KiB='+str(zip_ok))
        if not elf_ok or not zip_ok: problems.append(info.filename)
if problems: raise SystemExit('Review 16 KiB alignment: '+', '.join(problems))
print('Native alignment inspection passed; a 16 KiB device/emulator runtime test remains required.')
