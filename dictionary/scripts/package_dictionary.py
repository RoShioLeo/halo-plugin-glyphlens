# SPDX-License-Identifier: MIT
"""Build the independently versioned default dictionary archive."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
FILES = ('dictionary.json', 'lexicon.json', 'sources.json', 'reviews.json', 'reference.json', 'version.json')


def build(output, write_manifest=False, check_only=False):
    version = (ROOT / 'VERSION').read_text(encoding='utf-8').strip()
    if not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)', version):
        raise ValueError('Dictionary VERSION must be a semantic version')
    payload = {name: (ROOT / 'data' / name).read_bytes() for name in FILES}
    metadata = json.loads(payload['version.json'])
    sources = json.loads(payload['sources.json'])
    mapping = json.loads(payload['dictionary.json'])
    if metadata['version'] != version or sources['summary']['version'] != version:
        raise ValueError('Rebuild data before packaging this dictionary version')
    counts = {'records': len(mapping), 'relations': sum(map(len, mapping.values())),
              'multi': sum(len(values) > 1 for values in mapping.values()), 'pending': len(json.loads(payload['reviews.json']))}
    if metadata['counts'] != counts or sources['summary']['counts'] != counts:
        raise ValueError('Dictionary counts differ from actual mapping data')
    manifest = {'format': 'glyphlens-dictionary', 'formatVersion': 1, 'dictionaryVersion': version,
                'files': {name: hashlib.sha256(payload[name]).hexdigest() for name in FILES}}
    encoded = (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    release = ROOT / 'releases' / (version + '.json')
    if write_manifest and not release.exists():
        release.parent.mkdir(parents=True, exist_ok=True)
        release.write_bytes(encoded)
    if not release.exists() or json.loads(release.read_text(encoding='utf-8')) != manifest:
        raise ValueError('Release contents differ. Assign a new VERSION and freeze it with --write-manifest')
    if check_only:
        print('Dictionary ' + version + ' release manifest verified')
        return
    payload['manifest.json'] = encoded
    for name in ['README.md', 'NOTICE.md', 'LICENSE', 'licenses/Unicode-LICENSE.txt', 'licenses/OpenCC-LICENSE.txt']:
        payload[name] = (ROOT / name).read_bytes()
    payload['FORMAT.md'] = (ROOT.parent / 'docs' / 'DICTIONARY-IMPORT.md').read_bytes()
    output.mkdir(parents=True, exist_ok=True)
    archive_path = output / ('GlyphLens-Dictionary-' + version + '.zip')
    with zipfile.ZipFile(archive_path, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in sorted(payload):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, payload[name])
    digest = hashlib.sha256(archive_path.read_bytes()).hexdigest()
    (output / 'SHA256SUMS').write_text(digest + '  ' + archive_path.name + '\n', encoding='utf-8', newline='\n')
    print(json.dumps({'version': version, 'counts': counts, 'archive': str(archive_path), 'sha256': digest}, ensure_ascii=True))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, default=ROOT / 'dist')
    parser.add_argument('--write-manifest', action='store_true')
    parser.add_argument('--check-only', action='store_true')
    args = parser.parse_args()
    build(args.output.resolve(), args.write_manifest, args.check_only)
