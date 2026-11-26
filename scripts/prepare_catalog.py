# SPDX-License-Identifier: MIT
"""Compile independently sourced character relations; unresolved edges stay inactive."""
from pathlib import Path
from collections import defaultdict
import argparse,hashlib,json,re,unicodedata
from reference_policy import select_reference

def compile_catalog(cache,output):
    def read(name):return json.loads((cache/name).read_text(encoding='utf-8'))
    def han(c):return len(c)==1 and (c=='〇' or 0x3400<=ord(c)<=0x4DBF or 0x4E00<=ord(c)<=0x9FFF or 0xF900<=ord(c)<=0xFAFF or 0x20000<=ord(c)<=0x3347F)
    def order(values,source=None):return sorted(set(values),key=lambda c:(c!=source,tuple(map(ord,c))))
    def raw_table(name):
        table={};flags=set();flag=False
        for line in (cache/'sources'/name).read_text(encoding='utf-8').splitlines():
            if line.startswith('# @tofu-risk:'):flag=True
            elif line and not line.startswith('#'):
                s,values=line.split('\t');table[s]=values.split()
                if flag:flags.add(s);flag=False
        return table,flags
    properties=read('properties.json')
    standards=read('sources/TGH-transcription.json')['rows']
    standard={c for c,p in properties.items() if 'kTGH' in p}
    assert len(standards)==8105 and {row['c'] for row in standards}==standard
    assert sum(len(row.get('t',[])) for row in standards)==2574
    assert sum(len(row.get('v',[])) for row in standards)==1023
    # Table serials and Unicode values are not frequency ranks. Use the
    # published usage tiers; preserve the original only within the same tier.
    simple_tiers={row['c']:row['lv'] for row in standards}
    traditional_tiers=dict(simple_tiers)
    for row in standards:
        for item in row.get('t',[]):
            c=item['c']
            traditional_tiers[c]=min(row['lv'],traditional_tiers.get(c,4))
    def candidate_order(direction,values,source):
        tiers=simple_tiers if direction=='v2s' else traditional_tiers
        # Ideographic zero is used independently as a numeral, outside these
        # Han usage tables. An absent tier must not demote it to historical 星.
        if source=='〇':return order(values,source)
        return sorted(set(values),key=lambda c:(tiers.get(c,4),c!=source,ord(c)))
    references=defaultdict(list)
    to_simple=defaultdict(set);to_traditional=defaultdict(set)
    for row in standards:
        for kind in ['t','v']:
            for item in row.get(kind,[]):
                c=item['c']
                if han(c):
                    to_simple[c].add(row['c'])
                    references[c].append({'label':f'通用规范汉字表 {row["s"]:04}','url':'https://www.moe.gov.cn/jyb_xwfb/moe_2082/siju/yywzxxgls/ztzl_yywzgfbz/201306/t20130601_186002.html'})
                    if kind=='t':to_traditional[row['c']].add(c)
    def simple(c):return (to_simple[c]|({c} if c in standard else set())) or {c}
    def expand(direction,values):
        result=set()
        for c in values:result.update(simple(c) if direction=='v2s' else {c})
        return result
    def coexist(c):return c in standard and bool(to_simple[c]-{c})
    edges={o:defaultdict(set) for o in ['v2s','v2t']}
    pending={o:defaultdict(set) for o in edges};reasons={o:defaultdict(set) for o in edges};proof={o:defaultdict(set) for o in edges}
    def add(o,s,values,why):
        if not han(s) or unicodedata.normalize('NFC',s)!=s:return
        edges[o][s].update(t for t in values if han(t) and unicodedata.normalize('NFC',t)==t);proof[o][s].add(why)
    def hold(o,s,values,why):
        if not han(s) or unicodedata.normalize('NFC',s)!=s:return
        pending[o][s].update(t for t in values if han(t) and unicodedata.normalize('NFC',t)==t);reasons[o][s].add(why)
    for row in standards:
        c=row['c'];add('v2s',c,{c},'规范字：保留原字')
        if coexist(c):hold('v2s',c,simple(c),'两端均为规范字，需确认繁简兼用关系')
        targets=expand('v2t',to_traditional[c]) if to_traditional[c] else {c}
        if f'U+{ord(c):04X}' in properties.get(c,{}).get('kTraditionalVariant',''):targets.add(c)
        add('v2t',c,targets,'规范字表繁体对应')
        if len(targets)>1 and c in targets:hold('v2t',c,targets,'原字在繁体中独立使用，需确认')
        if any(t in standard and t!=c for t in targets):hold('v2t',c,targets,'对应繁体本身另为规范字，需确认')
    formal_traditional=set().union(*to_traditional.values())
    for c,targets in to_simple.items():
        add('v2s',c,targets,'规范字表附表：繁体、异体到规范字')
        if coexist(c):hold('v2s',c,targets|{c},'两端均为规范字，需确认')
        if c not in formal_traditional:
            possible=expand('v2t',set().union(*(to_traditional[t] or {t} for t in targets)))
            hold('v2t',c,possible,'具体繁体归属未核实，未机械展开候选')
    ts,risk=raw_table('TSCharacters.txt');st,_=raw_table('STCharacters.txt')
    inferred=defaultdict(set)
    for c,values in ts.items():
        for t in values:
            if han(t) and t!=c and t not in standard and (c in risk or c in st.get(t,[])):inferred[t].add(c)
        targets={t for t in values if t in standard}
        if targets and (not edges['v2s'][c] or not targets<=edges['v2s'][c]):hold('v2s',c,targets,'OpenCC 补充关系尚待核查')
    for c,values in st.items():
        targets=expand('v2t',values)
        corroborated=targets & to_traditional[c]
        if corroborated:add('v2t',c,corroborated,'OpenCC 与规范字表正式繁体对应相符')
        if not edges['v2t'][c] or not targets<=edges['v2t'][c]:hold('v2t',c,targets,'OpenCC 繁体补充或来源间差异，需确认')
    for c,origins in inferred.items():
        targets=set(st.get(c,[]))
        for o in edges:hold(o,c,expand(o,targets|origins),'OpenCC 反向关系尚待独立核查')
    for c,properties_for_character in properties.items():
        targets={chr(int(v[2:],16)) for v in re.findall(r'U\+[0-9A-F]+',properties_for_character.get('kZVariant',''))}
        for t in targets:
            if t in standard or t in formal_traditional:
                for o in edges:
                    if not expand(o,{t})<=edges[o][c]:hold(o,c,expand(o,{t}),'Unicode 字形变体补充，待核规范字归属')
    # Existing sources own their mappings and candidate order. The reference
    # may fill gaps only; excluded relations do not swell the review queue.
    supplements,reference,reference_stats=select_reference(
        read('moe-regular.json'),read('moe-relations.json'),simple_tiers,
        formal_traditional,edges,pending,to_simple,ts)
    for direction,entries in supplements.items():
        for c,values in entries.items():
            add(direction,c,values,'中国台湾地区教育主管部门异体资料：唯一常用对应，低优先级补充')
            for url in reference['links'][c]:
                item={'label':'中国台湾地区教育主管部门《異體字字典》：补充参考','url':url}
                if item not in references[c]:references[c].append(item)
    # A direct formal variant can be shared when its sole standard target has
    # no different traditional spelling. Do not promote inferred, ambiguous or
    # conflicting proposals, and never replace an existing traditional rule.
    formal_variants=defaultdict(set)
    for row in standards:
        for item in row.get('v',[]):formal_variants[item['c']].add(row['c'])
    for c,targets in formal_variants.items():
        if len(targets)!=1 or edges['v2s'].get(c)!=targets or edges['v2t'].get(c):continue
        if pending['v2s'].get(c,set())-targets or pending['v2t'].get(c,set())!=targets:continue
        target=next(iter(targets))
        unicode_traditional={chr(int(v[2:],16)) for v in re.findall(
            r'U\+[0-9A-F]+',properties.get(target,{}).get('kTraditionalVariant',''))}
        if to_traditional[target] or set(st.get(target,[target]))!={target} or unicode_traditional-{target}:continue
        add('v2t',c,targets,'规范字表附表：异体对应唯一，目标繁简同形')
    output.mkdir(parents=True,exist_ok=True)
    reviews={};counts={};source_index={};mappings={}
    for o in edges:
        mapping={};reviews[o]={};source_index[o]={}
        for c in order(set(edges[o])|set(pending[o])):
            if not edges[o][c] and not pending[o][c]:continue
            if pending[o][c] - (edges[o][c] or {c}):
                candidates=candidate_order(o,edges[o][c]|pending[o][c],c)
                reviews[o][c]={'candidates':candidates,'reasons':sorted(reasons[o][c]),'evidence':sorted(proof[o][c]),'links':list(dict.fromkeys(item['url'] for item in references[c] if 'dict.variants.moe.edu.tw' not in item['url']))[:8]}
            # Known character relations remain usable; only unproved extra
            # candidates are withheld. Coexisting forms retain the input.
            if edges[o][c]:mapping[c]=candidate_order(o,edges[o][c],c)
            if references[c]:source_index[o][c]=references[c]
        mappings[o]=mapping
    # Canonical CJK compatibility aliases are explicit lookup entries. No
    # normalization is applied to unmatched user text at runtime.
    compatibility_sources={}
    for start,end in [(0xF900,0xFAFF),(0x2F800,0x2FA1F)]:
        for point in range(start,end+1):
            alias=chr(point);canonical=unicodedata.normalize('NFC',alias)
            if canonical==alias or len(canonical)!=1:continue
            for direction in ['v2s','v2t']:
                if canonical in mappings[direction] and alias not in mappings[direction]:
                    mappings[direction][alias]=mappings[direction][canonical]
                    references[alias].append({'label':f'Unicode 规范等价 U+{point:04X}','url':'https://www.unicode.org/reports/tr15/'})
                    compatibility_sources[alias]=canonical
    # Identity-only entries have no effect: unmatched input is already kept.
    # Remove them after compatibility aliases inherit their explicit targets.
    for direction in mappings:
        mappings[direction]={c:values for c,values in mappings[direction].items() if values!=[c]}
    # Keep a source in common only when both its active mapping and proposed
    # extra candidates are direction independent. This also partitions reviews.
    common={};common_reviews={}
    keys=set().union(*(set(mappings[o])|set(reviews[o]) for o in mappings))
    common_keys=set()
    for c in order(keys):
        left,right=(mappings[o].get(c,[c]) for o in ['v2s','v2t'])
        sr,tr=(reviews[o].get(c) for o in ['v2s','v2t'])
        if left!=right or (sr and sr['candidates'])!=(tr and tr['candidates']):continue
        common_keys.add(c)
        if left!=[c]:common[c]=left
        if sr:
            common_reviews[c]={field:sorted(set(sr[field])|set(tr[field])) for field in ['reasons','evidence','links']}
            common_reviews[c]['candidates']=sr['candidates']
        for direction in mappings:
            mappings[direction].pop(c,None);reviews[direction].pop(c,None)
    punctuation={'︵':'（','︶':'）','︻':'【','︼':'】','﹁':'「','﹂':'」','﹃':'『','﹄':'』','︽':'《','︾':'》','︿':'〈','﹀':'〉'}
    for c,t in punctuation.items():common[c]=[t]
    mappings['common']=common;reviews['common']=common_reviews;source_index['common']={}
    for c in order(common_keys|set(punctuation)):
        refs=list({(item['label'],item['url']):item for item in references[c]}.values())
        if c in punctuation:refs=[{'label':'竖排标点：维护者确认的横排对应','url':'https://www.unicode.org/charts/PDF/UFE30.pdf'}]
        if refs:source_index['common'][c]=refs
        for direction in ['v2s','v2t']:source_index[direction].pop(c,None)
    for alias in compatibility_sources:
        if alias not in common:
            for direction in ['v2s','v2t']:
                if alias in mappings[direction]:source_index[direction][alias]=references[alias]
    for direction in mappings:
        source_index[direction]={c:refs for c,refs in source_index[direction].items() if c in mappings[direction] or c in reviews[direction]}
    for o,mapping in mappings.items():
        body=',\n'.join(json.dumps(c,ensure_ascii=False)+': '+json.dumps(v,ensure_ascii=False) for c,v in sorted(mapping.items(),key=lambda item:tuple(map(ord,item[0]))))
        (output/f'{o}.json').write_text('{\n'+body+'\n}\n',encoding='utf-8',newline='\n')
        counts[o]={'records':len(mapping),'pending':len(reviews[o]),'multi':sum(len(v)>1 for v in mapping.values())}
        if o!='common':counts[o]['effective_records']=len(mapping)+len(common)
    summary={'scope':'非常用异体字到常用字；现有来源优先；唯一常用参考对应自动补充','mainland_standard':8105,'formal_traditional':2574,'formal_variants':1023,'reference_policy':{'simple_max_level':2,'traditional_grades':['A','B'],'automatic':{o:len(v) for o,v in supplements.items()},'excluded':reference_stats},'counts':counts}
    (output/'reviews.json').write_text(json.dumps(reviews,ensure_ascii=False,separators=(',',':')),encoding='utf-8',newline='\n')
    sources={'summary':summary,'links':{'mainland':'https://www.moe.gov.cn/jyb_xwfb/moe_2082/siju/yywzxxgls/ztzl_yywzgfbz/201306/t20130601_186002.html','unicode':'https://www.unicode.org/reports/tr38/','opencc':'https://github.com/BYVoid/OpenCC/tree/fae2a0a911c30764e669a7d8b0591cd8e852934a/data/dictionary','variants':'https://dict.variants.moe.edu.tw/'},'entries':source_index}
    (output/'sources.json').write_text(json.dumps(sources,ensure_ascii=False,separators=(',',':')),encoding='utf-8',newline='\n')
    (output/'reference.json').write_text(json.dumps(reference,ensure_ascii=False,separators=(',',':')),encoding='utf-8',newline='\n')
    print(json.dumps(summary,ensure_ascii=False,indent=2))
    return summary

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--cache',type=Path,required=True);parser.add_argument('--output',type=Path,default=Path(__file__).resolve().parents[1]/'data')
    args=parser.parse_args();compile_catalog(args.cache.resolve(),args.output.resolve())
