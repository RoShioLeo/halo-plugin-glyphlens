# SPDX-License-Identifier: MIT
"""Low-priority, common-target-only supplements from a variant reference."""
from collections import defaultdict, Counter
import unicodedata


def scalar_han(c):
    return isinstance(c, str) and len(c) == 1 and (
        c == '〇' or 0x3400 <= ord(c) <= 0x4DBF or 0x4E00 <= ord(c) <= 0x9FFF
        or 0x20000 <= ord(c) <= 0x3347F
    ) and unicodedata.normalize('NFC', c) == c


def select_reference(regular_rows, relations, levels, formal_traditional, edges, pending, to_simple, ts):
    regular = {r['character'] for r in regular_rows if scalar_han(r['character'])}
    common_traditional = {r['character'] for r in regular_rows
                          if r['code'].startswith(('A', 'B')) and scalar_han(r['character'])}
    common_simple = {c for c, level in levels.items() if level <= 2}
    protected = set(levels) | set(formal_traditional) | regular | {'〇'}
    direct, links = defaultdict(set), defaultdict(set)
    unresolved = set()
    for row in relations:
        source, target = row['character'], row['regular_character']
        if not scalar_han(source):
            continue
        if not scalar_han(target):
            unresolved.add(source)
            continue
        direct[source].add(target)
        links[source].add(f'https://dict.variants.moe.edu.tw/dictView.jsp?ID={row["regular_id"]}')
    automatic = {'v2s': {}, 'v2t': {}}
    reference = {'v2s': {}, 'v2t': {}}
    stats = Counter()
    for source in sorted(direct):
        raw = direct[source]
        # Do not turn a genuinely ambiguous relation into a unique one merely
        # by throwing away its rare targets first.
        unique = len(raw) == 1 and source not in unresolved and source not in raw
        simple_targets = set()
        for target in raw:
            # Prefer the formal annex. OpenCC is only a fallback for resolving
            # the target's simplified spelling, never for replacing old rules.
            simple_targets.update((to_simple.get(target) or ts.get(target) or [target]))
        options = {'v2s': simple_targets & common_simple,
                   'v2t': raw & common_traditional}
        for direction, candidates in options.items():
            candidates = candidates - {source}
            if candidates:
                reference[direction][source] = sorted(candidates)
            if source in protected:
                stats['protected'] += bool(candidates)
            elif not unique or len(candidates) != 1 or (direction == 'v2s' and len(simple_targets) != 1):
                stats['ambiguous_or_uncommon'] += 1
            elif edges[direction].get(source):
                stats['existing_mapping'] += 1
            elif pending[direction].get(source, set()) - candidates:
                stats['existing_conflict'] += 1
            else:
                automatic[direction][source] = sorted(candidates)
    # These facts are consulted only after an administrator explicitly saves a
    # relation. Ambiguous/protected sources never become automatic mappings.
    used = set(reference['v2s']) | set(reference['v2t'])
    return automatic, {'mappings': reference,
                       'links': {c: sorted(links[c]) for c in sorted(used)}}, dict(sorted(stats.items()))
