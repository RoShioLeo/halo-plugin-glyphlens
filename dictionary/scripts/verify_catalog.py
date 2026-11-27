# SPDX-License-Identifier: MIT
from pathlib import Path
import argparse,hashlib,json
root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--rebuilt',type=Path,required=True);args=parser.parse_args()
for name in ['dictionary.json','reviews.json','sources.json','reference.json','version.json']:
    actual=(args.rebuilt/name).read_bytes();expected=(root/'data'/name).read_bytes()
    if actual!=expected:raise ValueError('Rebuilt catalog differs: '+name)
    print(name,hashlib.sha256(actual).hexdigest())
manifest=json.loads((root/'research/source-manifest.json').read_text(encoding='utf-8'))
for key in ['maintainer-decisions','default-context-lexicon','manual-sources']:
    item=manifest[key];path=(root/'research'/item['file']).resolve()
    if not path.is_relative_to(root):raise ValueError('Source path escaped repository')
    if hashlib.sha256(path.read_bytes()).hexdigest()!=item['sha256']:raise ValueError('Authored source checksum differs: '+key)
    print(key,item['sha256'])
