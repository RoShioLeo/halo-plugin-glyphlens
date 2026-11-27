# SPDX-License-Identifier: MIT
"""Low-priority, unique common simplified-target supplements from a variant reference."""
from collections import defaultdict,Counter
import unicodedata

def scalar_han(c):
    return isinstance(c,str) and len(c)==1 and (c=='〇' or 0x3400<=ord(c)<=0x4DBF or 0x4E00<=ord(c)<=0x9FFF or 0x20000<=ord(c)<=0x3347F) and unicodedata.normalize('NFC',c)==c

def select_reference(regular_rows,relations,levels,formal_traditional,edges,pending,to_simple,ts):
    regular={row['character'] for row in regular_rows if scalar_han(row['character'])}
    common={c for c,level in levels.items() if level<=2}
    protected=set(levels)|set(formal_traditional)|regular|{'〇'}
    direct,links=defaultdict(set),defaultdict(set);unresolved=set()
    for row in relations:
        source,target=row['character'],row['regular_character']
        if not scalar_han(source):continue
        if not scalar_han(target):unresolved.add(source);continue
        direct[source].add(target)
        links[source].add(f'https://dict.variants.moe.edu.tw/dictView.jsp?ID={row["regular_id"]}')
    automatic={};reference={};stats=Counter()
    for source in sorted(direct):
        raw=direct[source];unique=len(raw)==1 and source not in unresolved and source not in raw
        simple_targets=set()
        for target in raw:simple_targets.update(to_simple.get(target) or ts.get(target) or [target])
        candidates=(simple_targets & common)-{source}
        if candidates:reference[source]=sorted(candidates)
        if source in protected:stats['protected']+=bool(candidates)
        elif not unique or len(candidates)!=1 or len(simple_targets)!=1:stats['ambiguous_or_uncommon']+=1
        elif edges.get(source):stats['existing_mapping']+=1
        elif pending.get(source,set())-candidates:stats['existing_conflict']+=1
        else:automatic[source]=sorted(candidates)
    return automatic,{'mappings':reference,'links':{c:sorted(links[c]) for c in sorted(reference)}},dict(sorted(stats.items()))
