# SPDX-License-Identifier: MIT
"""Compile one simplified-glyph dictionary; unreviewed extra relations stay inactive."""
from pathlib import Path
from collections import defaultdict
import argparse,json,re,unicodedata
from reference_policy import select_reference

ROOT=Path(__file__).resolve().parents[1]

def compile_catalog(cache,output):
    def read(name):return json.loads((cache/name).read_text(encoding='utf-8'))
    def han(c):return isinstance(c,str) and len(c)==1 and (c=='〇' or 0x3400<=ord(c)<=0x4DBF or 0x4E00<=ord(c)<=0x9FFF or 0xF900<=ord(c)<=0xFAFF or 0x20000<=ord(c)<=0x3347F)
    def raw_table(name):
        table={};flags=set();flag=False
        for line in (cache/'sources'/name).read_text(encoding='utf-8').splitlines():
            if line.startswith('# @tofu-risk:'):flag=True
            elif line and not line.startswith('#'):
                source,values=line.split('\t');table[source]=values.split()
                if flag:flags.add(source);flag=False
        return table,flags
    properties=read('properties.json')
    standards=read('sources/TGH-transcription.json')['rows']
    standard={c for c,p in properties.items() if 'kTGH' in p}
    assert len(standards)==8105 and {row['c'] for row in standards}==standard
    assert sum(len(row.get('t',[])) for row in standards)==2574
    assert sum(len(row.get('v',[])) for row in standards)==1023
    tiers={row['c']:row['lv'] for row in standards}
    def order(values,source):
        return sorted(set(values),key=lambda c:((0 if source=='〇' else tiers.get(c,4)),c!=source,ord(c)))
    references=defaultdict(list);to_simple=defaultdict(set);formal_traditional=set()
    for row in standards:
        for kind in ['t','v']:
            for item in row.get(kind,[]):
                c=item['c']
                if han(c):
                    to_simple[c].add(row['c'])
                    references[c].append({'label':f'通用规范汉字表 {row["s"]:04}','url':'https://www.moe.gov.cn/jyb_xwfb/moe_2082/siju/yywzxxgls/ztzl_yywzgfbz/201306/t20130601_186002.html'})
                    if kind=='t':formal_traditional.add(c)
    def simple(c):return (to_simple[c]|({c} if c in standard else set())) or {c}
    def expand(values):return set().union(*(simple(c) for c in values)) if values else set()
    edges=defaultdict(set);pending=defaultdict(set);reasons=defaultdict(set);proof=defaultdict(set)
    def add(source,values,why):
        if not han(source) or unicodedata.normalize('NFC',source)!=source:return
        edges[source].update(t for t in values if han(t) and unicodedata.normalize('NFC',t)==t);proof[source].add(why)
    def hold(source,values,why):
        if not han(source) or unicodedata.normalize('NFC',source)!=source:return
        pending[source].update(t for t in values if han(t) and unicodedata.normalize('NFC',t)==t);reasons[source].add(why)
    for row in standards:
        c=row['c'];add(c,{c},'规范字：保留原字')
        if c in standard and to_simple[c]-{c}:hold(c,simple(c),'两端均为规范字，需确认繁简兼用关系')
    for c,targets in to_simple.items():
        add(c,targets,'规范字表附表：繁体、异体到规范字')
        if c in standard and targets-{c}:hold(c,targets|{c},'两端均为规范字，需确认')
    ts,risk=raw_table('TSCharacters.txt');st,_=raw_table('STCharacters.txt')
    inferred=defaultdict(set)
    for c,values in ts.items():
        for target in values:
            if han(target) and target!=c and target not in standard and (c in risk or c in st.get(target,[])):inferred[target].add(c)
        targets={t for t in values if t in standard}
        if targets and (not edges[c] or not targets<=edges[c]):hold(c,targets,'OpenCC 补充关系尚待核查')
    for c,origins in inferred.items():hold(c,expand(set(st.get(c,[]))|origins),'OpenCC 反向关系尚待独立核查')
    for c,props in properties.items():
        targets={chr(int(v[2:],16)) for v in re.findall(r'U\+[0-9A-F]+',props.get('kZVariant',''))}
        for target in targets:
            if target in standard or target in formal_traditional:
                if not expand({target})<=edges[c]:hold(c,expand({target}),'Unicode 字形变体补充，待核规范字归属')
    supplements,reference,reference_stats=select_reference(read('moe-regular.json'),read('moe-relations.json'),tiers,formal_traditional,edges,pending,to_simple,ts)
    for c,values in supplements.items():
        add(c,values,'中国台湾地区教育主管部门异体资料：唯一常用对应，低优先级补充')
        for url in reference['links'][c]:
            item={'label':'中国台湾地区教育主管部门《異體字字典》：补充参考','url':url}
            if item not in references[c]:references[c].append(item)
    mapping={};reviews={}
    for c in sorted(set(edges)|set(pending),key=ord):
        # Filter after supplement selection: removing a questionable proposal
        # must not silently activate a formerly conflicting reference rule.
        targets={t for t in pending[c] if t in standard or t==c}
        if targets-(edges[c] or {c}):
            reviews[c]={'candidates':order(edges[c]|targets,c),'reasons':sorted(reasons[c]),'evidence':sorted(proof[c]),
                        'links':list(dict.fromkeys(item['url'] for item in references[c] if 'dict.variants.moe.edu.tw' not in item['url']))[:8]}
        if edges[c]:mapping[c]=order(edges[c],c)
    # Manual decisions target the explicit character, matching the previous
    # live dictionary; no implicit replacement of its compatibility aliases.
    for start,end in [(0xF900,0xFAFF),(0x2F800,0x2FA1F)]:
        for point in range(start,end+1):
            alias=chr(point);canonical=unicodedata.normalize('NFC',alias)
            if canonical!=alias and len(canonical)==1 and canonical in mapping and alias not in mapping:
                mapping[alias]=mapping[canonical]
                references[alias].append({'label':f'Unicode 规范等价 U+{point:04X}','url':'https://www.unicode.org/reports/tr15/'})
    mapping={c:values for c,values in mapping.items() if values!=[c]}
    punctuation={'︵':'（','︶':'）','︻':'【','︼':'】','﹁':'「','﹂':'」','﹃':'『','﹄':'』','︽':'《','︾':'》','︿':'〈','﹀':'〉'}
    for c,target in punctuation.items():
        mapping[c]=[target]
        references[c]=[{'label':'竖排标点：维护者确认的横排对应','url':'https://www.unicode.org/charts/PDF/UFE30.pdf'}]
    decisions=json.loads((ROOT/'research/maintainer-decisions.json').read_text(encoding='utf-8'))
    assert decisions['schema']==1
    assert not set(decisions['accepted']) & set(decisions['rejected'])
    for c,values in decisions['accepted'].items():
        assert len(c)==1 and values and len(set(values))==len(values) and all(len(t)==1 for t in values)
        mapping[c]=values;reviews.pop(c,None)
    for c in decisions['rejected']:
        mapping.pop(c,None);reviews.pop(c,None)
    counts={'records':len(mapping),'pending':len(reviews),'multi':sum(len(v)>1 for v in mapping.values())}
    summary={'scope':'繁体、异体字到常用简体；单一字库；维护者确认优先','mainland_standard':8105,'formal_traditional':2574,'formal_variants':1023,
             'reference_policy':{'simple_max_level':2,'automatic':len(supplements),'excluded':reference_stats},
             'maintainer_decisions':{'accepted':len(decisions['accepted']),'rejected':len(decisions['rejected']),'updated':decisions['updated']},'counts':counts}
    sources={'summary':summary,'links':{'mainland':'https://www.moe.gov.cn/jyb_xwfb/moe_2082/siju/yywzxxgls/ztzl_yywzgfbz/201306/t20130601_186002.html','unicode':'https://www.unicode.org/reports/tr38/','opencc':'https://github.com/BYVoid/OpenCC/tree/fae2a0a911c30764e669a7d8b0591cd8e852934a/data/dictionary','variants':'https://dict.variants.moe.edu.tw/'},
             'entries':{c:list({(item['label'],item['url']):item for item in references[c]}.values()) for c in sorted(set(mapping)|set(reviews),key=ord) if references[c]}}
    output.mkdir(parents=True,exist_ok=True)
    body=',\n'.join(json.dumps(c,ensure_ascii=False)+': '+json.dumps(v,ensure_ascii=False) for c,v in sorted(mapping.items(),key=lambda item:ord(item[0])))
    (output/'dictionary.json').write_text('{\n'+body+'\n}\n',encoding='utf-8',newline='\n')
    for name,value in [('reviews',reviews),('sources',sources),('reference',reference)]:
        (output/f'{name}.json').write_text(json.dumps(value,ensure_ascii=False,separators=(',',':')),encoding='utf-8',newline='\n')
    print(json.dumps(summary,ensure_ascii=False,indent=2));return summary

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--cache',type=Path,required=True);parser.add_argument('--output',type=Path,default=ROOT/'data')
    args=parser.parse_args();compile_catalog(args.cache.resolve(),args.output.resolve())
