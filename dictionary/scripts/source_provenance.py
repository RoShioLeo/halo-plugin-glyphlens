# SPDX-License-Identifier: MIT
"""Attach evidence to existing candidates without creating conversion relations."""
from collections import defaultdict
from pathlib import Path
import json
import re
import unicodedata

ROOT = Path(__file__).resolve().parents[1]
TGH_URL = 'https://www.moe.gov.cn/jyb_xwfb/moe_2082/siju/yywzxxgls/ztzl_yywzgfbz/201306/t20130601_186002.html'


class SourceEvidence:
    def __init__(self, cache):
        read = lambda name: json.loads((cache / name).read_text(encoding='utf-8'))
        manifest = json.loads((ROOT / 'research/source-manifest.json').read_text(encoding='utf-8'))
        self.edges = defaultdict(lambda: defaultdict(list))
        self.normalizers = defaultdict(lambda: defaultdict(list))
        self.formal = defaultdict(lambda: defaultdict(list))
        self.properties = read('properties.json')
        rows = read('sources/TGH-transcription.json')['rows']
        standard = {row['c'] for row in rows}
        for row in rows:
            for kind in ['t', 'v']:
                for item in row.get(kind, []):
                    c, target = item['c'], row['c']
                    step = self.step(c, target, 'mainland', '《通用规范汉字表》附表', TGH_URL,
                                     f'序号 {row["s"]:04} · {"繁体" if kind == "t" else "异体"}')
                    self.add(step)
                    self.formal[c][target].append(step)
        # Follow the same one-step normalization used by the compiler: prefer
        # the official table, otherwise an explicit OpenCC simplified target.
        for name in ['TSCharacters.txt', 'STCharacters.txt']:
            raw_url = manifest[name]['url']
            url = raw_url.replace('https://raw.githubusercontent.com/', 'https://github.com/', 1)
            parts = url.split('/')
            url = '/'.join(parts[:5] + ['blob'] + parts[5:])
            for number, line in enumerate((cache / 'sources' / name).read_text(encoding='utf-8').splitlines(), 1):
                if not line or line.startswith('#'):
                    continue
                c, values = line.split('\t')
                for target in values.split():
                    if len(c) != 1 or len(target) != 1 or c == target:
                        continue
                    step = self.step(c, target, 'opencc', 'OpenCC · ' + name, url + f'#L{number}', f'第 {number} 行')
                    self.add(step, reversible=True)
                    if name == 'TSCharacters.txt' and target in standard:
                        self.normalizers[c][target].append(step)
        self.normalizers.update(self.formal)
        unihan_url = manifest['Unihan-17.0.0.zip']['url']
        for c, properties in self.properties.items():
            for key in ['kSimplifiedVariant', 'kTraditionalVariant', 'kZVariant']:
                for point in re.findall(r'U\+([0-9A-F]+)', properties.get(key, '')):
                    target = chr(int(point, 16))
                    if c != target:
                        self.add(self.step(c, target, 'unicode', 'Unicode Unihan 17.0.0 · ' + key,
                                           unihan_url, f'U+{ord(c):04X} · {key} · U+{ord(target):04X}'), reversible=True)
        for row in read('moe-relations.json'):
            c, target = row.get('character'), row.get('regular_character')
            if not isinstance(c, str) or not isinstance(target, str) or len(c) != 1 or len(target) != 1 or c == target:
                continue
            self.add(self.step(c, target, 'variants', '《異體字字典》',
                               f'https://dict.variants.moe.edu.tw/dictView.jsp?ID={row.get("id", row["regular_id"])}',
                               row.get('code', '') + ' → ' + row.get('regular_code', '')))
        manual = json.loads((ROOT / 'research/manual-sources.json').read_text(encoding='utf-8'))
        assert manual['schema'] == 1
        self.manual_sources = {(item['character'], item['candidate']): item for item in manual['records']}

    @staticmethod
    def step(c, target, source, label, url, locator):
        return {'from': c, 'to': target, 'source': source, 'label': label, 'url': url,
                'locator': locator, 'raw': c + ' → ' + target, 'reversed': False}

    def add(self, step, reversible=False):
        self.edges[step['from']][step['to']].append(step)
        if reversible:
            reverse = dict(step, **{'from': step['to'], 'to': step['from'], 'reversed': True})
            self.edges[reverse['from']][reverse['to']].append(reverse)

    def paths(self, c, target):
        paths = [[step] for step in self.edges.get(c, {}).get(target, [])]
        for middle, first_steps in self.edges.get(c, {}).items():
            if middle in [c, target]:
                continue
            for second in self.normalizers.get(middle, {}).get(target, []):
                for first in first_steps:
                    paths.append([first, second])
        canonical = unicodedata.normalize('NFC', c)
        if canonical != c and len(canonical) == 1:
            step = self.step(c, canonical, 'unicode', 'Unicode · 规范分解',
                             'https://www.unicode.org/Public/17.0.0/ucd/UnicodeData.txt', f'U+{ord(c):04X} · Decomposition_Mapping')
            paths.extend([[step]] if canonical == target else [[step] + path for path in self.paths(canonical, target)])
        decomposition = unicodedata.decomposition(c).split()
        if decomposition and decomposition[0] == '<vertical>':
            base = ''.join(chr(int(point, 16)) for point in decomposition[1:])
            step = self.step(c, base, 'unicode', 'Unicode · 竖排形式分解',
                             'https://www.unicode.org/Public/17.0.0/ucd/UnicodeData.txt', f'U+{ord(c):04X} · Decomposition_Mapping')
            if base == target:
                paths.append([step])
            elif unicodedata.decomposition(target) == '<wide> ' + ' '.join(f'{ord(char):04X}' for char in base):
                width = self.step(base, target, 'unicode', 'Unicode · 全角形式对照', step['url'], f'U+{ord(target):04X} · Decomposition_Mapping')
                width.update(raw=target + ' → ' + base, reversed=True)
                paths.append([step, width])
        unique = {json.dumps(path, ensure_ascii=False, sort_keys=True): path for path in paths}
        return sorted(unique.values(), key=lambda path: (len(path), json.dumps(path, ensure_ascii=False, sort_keys=True)))

    def records(self, c, target):
        if c == target:
            return []
        paths = self.paths(c, target)
        if not paths:
            if (c, target) in self.manual_sources:
                item = self.manual_sources[c, target]
                return [{'candidate': target, 'kind': 'manual', 'label': item['label'], 'steps': []}]
            return [{'candidate': target, 'kind': 'unresolved', 'label': '原始出处待补', 'steps': []}]
        return [{'candidate': target, 'kind': 'documented', 'source': path[0]['source'],
                 'label': '；'.join(dict.fromkeys(step['label'] for step in path)), 'url': path[0]['url'],
                 'path': [c] + [step['to'] for step in path], 'steps': path} for path in paths]


def compile_provenance(cache, mapping):
    evidence = SourceEvidence(cache)
    entries = {c: [record for target in values for record in evidence.records(c, target)]
               for c, values in sorted(mapping.items(), key=lambda item: ord(item[0]))}
    return {c: records for c, records in entries.items() if records}
