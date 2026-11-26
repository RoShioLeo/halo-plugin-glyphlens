# SPDX-License-Identifier: MIT
from pathlib import Path
import argparse,hashlib
root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--rebuilt',type=Path,required=True);args=parser.parse_args()
for name in ['common.json','v2s.json','v2t.json','reviews.json','sources.json','reference.json']:
    actual=(args.rebuilt/name).read_bytes();expected=(root/'data'/name).read_bytes()
    if actual!=expected:raise ValueError('Rebuilt catalog differs: '+name)
    print(name,hashlib.sha256(actual).hexdigest())
