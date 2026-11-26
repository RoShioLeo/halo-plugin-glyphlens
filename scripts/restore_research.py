# SPDX-License-Identifier: MIT
"""Restore portable, fact-only research inputs without accessing a blog server."""
from pathlib import Path
import argparse,gzip,hashlib,json
repo=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--cache',type=Path,default=repo/'.research')
args=parser.parse_args();cache=args.cache.resolve()
manifest=json.loads((repo/'research/inputs-manifest.json').read_text(encoding='utf-8'))
for name,item in manifest['files'].items():
    target=(cache/name).resolve()
    if not target.is_relative_to(cache):raise ValueError('Research path escaped cache')
    data=gzip.decompress((repo/'research/inputs'/item['file']).read_bytes())
    if hashlib.sha256(data).hexdigest()!=item['sha256']:raise ValueError('Research input checksum mismatch: '+name)
    target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
print(f'Restored {len(manifest["files"])} verified input files to {cache}')
