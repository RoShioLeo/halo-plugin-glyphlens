import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { gunzipSync } from 'node:zlib';
import { GlyphConverter } from '../web/engine.js';
const read=path=>JSON.parse(fs.readFileSync(new URL('../'+path,import.meta.url),'utf8'));
const dictionary=read('dictionary/data/dictionary.json'),reviews=read('dictionary/data/reviews.json');
const scalar=value=>[...value].length===1 && !(value.codePointAt(0)>=0xD800 && value.codePointAt(0)<=0xDFFF);

test('the one dictionary retains all 249 previously corroborated shared variants',()=>{
    const expected=JSON.parse(fs.readFileSync(new URL('./fixtures/formal-common.json',import.meta.url),'utf8'));
    assert.equal(Object.keys(expected).length,249);
    for(const [source,candidates]of Object.entries(expected)){assert.deepEqual(dictionary[source],candidates,source);assert(!reviews[source]);}
});
test('all explicitly accepted live corrections and candidate order are shipped as defaults',()=>{
    const decisions=read('dictionary/research/maintainer-decisions.json');
    assert.equal(Object.keys(decisions.accepted).length,225);
    for(const [source,values]of Object.entries(decisions.accepted)){assert.deepEqual(dictionary[source],values,source);assert(!reviews[source],source);}
    const converter=new GlyphConverter(dictionary);
    assert.equal(converter.convert('內於為絕閱乾寗竃㐷彷畫箚麼'),'内于为绝阅干宁灶骂彷画劄么');
    for(const source of decisions.rejected){assert(!dictionary[source]);assert(!reviews[source]);assert.equal(converter.convert(source),source);}
    assert.deepEqual(dictionary['彷'],['彷','仿']);assert.deepEqual(dictionary['畫'],['画','划']);
    assert.deepEqual(dictionary['箚'],['劄','札']);assert.deepEqual(dictionary['麼'],['么']);
    assert.deepEqual(dictionary['䌷'],['䌷','绸']);assert.deepEqual(dictionary['硷'],['碱']);
    assert.equal(converter.convert('䌷硷'),'䌷碱');assert.equal(converter.setMode('one2many').convert('䌷硷'),'【䌷|绸】碱');
});
test('compiled mappings are scalars, unknown characters stay unchanged, and no traditional dictionary is shipped',()=>{
    assert.equal(Object.keys(dictionary).length,12718);
    for(const [source,values]of Object.entries(dictionary)) {
        assert(scalar(source));assert(values.length);assert(values.every(scalar));assert.equal(new Set(values).size,values.length);assert.notDeepEqual(values,[source]);
    }
    for(const file of ['common.json','v2s.json','v2t.json'])assert(!fs.existsSync(new URL('../dictionary/data/'+file,import.meta.url)));
    assert.equal(new GlyphConverter(dictionary).convert('㐺㐁〇𪷍'),'㐺㐁〇𪷍');
    assert.equal(dictionary['干'],undefined);
});
test('pending targets are standard simplified glyphs or the original, never inferred traditional targets',()=>{
    const table=JSON.parse(gunzipSync(fs.readFileSync(new URL('../dictionary/research/inputs/TGH-transcription.json.gz',import.meta.url))));
    const standard=new Set(table.rows.map(row=>row.c));
    for(const [source,review]of Object.entries(reviews)) {
        assert(review.candidates.some(c=>!(dictionary[source]||[source]).includes(c)),source);
        for(const target of review.candidates)assert(target===source || standard.has(target),source+' → '+target);
        assert(!review.reasons.some(reason=>reason.includes('繁体补充') || reason.includes('具体繁体归属')));
    }
    assert.equal(Object.keys(reviews).length,0);
});
test('vertical punctuation and source data work through the same dictionary',()=>{
    const converter=new GlyphConverter(dictionary);
    assert.equal(converter.convert('︵︶︻︼﹁﹂﹃﹄︽︾︿﹀'),'（）【】「」『』《》〈〉');
    const sources=read('dictionary/data/sources.json');assert.equal(sources.summary.counts.records,12718);
    assert.deepEqual(Object.keys(sources.links).sort(),['mainland','opencc','unicode','variants']);
    assert(!/moe\.edu\.tw|台湾|異體字字典/.test(JSON.stringify(reviews)));
});
test('multi-candidate mode and established source rules retain their meanings',()=>{
    const converter=new GlyphConverter(dictionary);
    assert.equal(converter.setMode('one2many').convert('乾畫箚'),'【干|乾】【画|划】【劄|札】');
    assert.equal(converter.setMode('one2one').convert('㐀㐅伱'),'丘五你');
    assert.deepEqual(dictionary['仝'],['同','仝']);assert.deepEqual(dictionary['線'],['线','缐']);
    for(const [source,rejected]of [['㵾','𪷍'],['㶆','𫞛'],['㷍','𤆢'],['㷿','𤈷'],['㸇','𤎺']])assert(!(dictionary[source]||[source]).includes(rejected));
});

test('bundled simplified context words choose reviewed glyphs and leave room for manual overrides',()=>{
    const rules=read('dictionary/data/lexicon.json');assert.equal(rules.length,18);
    assert.equal(rules.reduce((count,rule)=>count+rule.contexts.length,0),75);
    const ids=new Set();
    for(const rule of rules) {
        assert(!ids.has(rule.id));ids.add(rule.id);
        assert(dictionary[rule.character]?.length>1);assert(dictionary[rule.character].includes(rule.candidate));
        assert(rule.sources.length);
        for(const word of rule.contexts) {
            assert([...word].length>=2 && [...word].length<=64);assert([...word].includes(rule.candidate));
            for(const c of word)if(c!==rule.candidate && dictionary[c]?.length===1)assert.equal(dictionary[c][0],c,'not a simplified context: '+word);
        }
    }
    const converter=new GlyphConverter(dictionary,rules);
    const input='乾坤乾燥彷彿彷徨沈沒沈陽瞭解瞭望藉口慰藉答覆覆蓋哪吒叱吒宮商角徵羽計畫繪畫';
    const output='乾坤干燥仿佛彷徨沉没沈阳了解瞭望借口慰藉答复覆盖哪吒叱咤宫商角徵羽计划绘画';
    assert.equal(converter.convert(input),output);
    assert.equal(converter.setMode('one2many').convert(input),output);
    const document=converter.review(input);assert.equal(document.pending,document.positions.length);assert.equal(document.matchedCount,document.positions.length);assert(document.positions.every(index=>!document.reviewed(index)));
    document.choose(document.positions[0],'干');assert(document.text().startsWith('干坤'));
});

test('default source counts account for every candidate relation without conflating source characters',()=>{
    const summary=read('dictionary/data/sources.json').summary;
    assert.equal(summary.version,fs.readFileSync(new URL('../dictionary/VERSION',import.meta.url),'utf8').trim());
    assert.equal(summary.counts.records,Object.keys(dictionary).length);
    assert.equal(summary.counts.relations,Object.values(dictionary).reduce((n,values)=>n+values.length,0));
    assert.equal(Object.values(summary.source_counts).reduce((n,source)=>n+source.relations,0),summary.counts.relations);
    for(const source of Object.values(summary.source_counts))assert(source.characters<=source.relations);
    assert.equal(summary.lexicon.rules,read('dictionary/data/lexicon.json').length);
});

test('every candidate retains checkable evidence or an explicit original-character policy independently of approval',()=>{
    const {entries,confirmations,summary}=read('dictionary/data/sources.json');
    const decisions=read('dictionary/research/maintainer-decisions.json');
    for(const [source,candidates] of Object.entries(dictionary)) {
        assert.deepEqual([...new Set(entries[source].map(record=>record.candidate))],candidates,source);
        for(const record of entries[source]) {
            if(record.kind==='retained'){assert.equal(record.candidate,source);assert.deepEqual(record.steps,[]);continue;}
            if(record.kind==='manual'){assert.equal(source,'竃');assert.equal(record.candidate,'灶');assert.deepEqual(record.steps,[]);assert(!record.url);continue;}
            assert.equal(record.kind,'documented',source+' → '+record.candidate);
            assert.equal(record.path[0],source);assert.equal(record.path.at(-1),record.candidate);
            assert.equal(record.path.length,record.steps.length+1);
            record.steps.forEach((step,index)=>{
                assert.equal(step.from,record.path[index]);assert.equal(step.to,record.path[index+1]);
                assert(step.url.startsWith('https://'));assert(step.locator);assert(step.raw);
                assert(!step.url.includes('maintainer-decisions'));
            });
        }
    }
    for(const [source,candidates] of Object.entries(decisions.accepted)) {
        assert.deepEqual(confirmations[source].candidates,candidates);
        assert.equal(confirmations[source].status,'confirmed');
    }
    assert.equal(summary.source_counts.retained.relations,45);assert.equal(summary.source_counts.unresolved.relations,0);
    const added=entries['竃'].find(record=>record.candidate==='灶');assert.equal(added.kind,'manual');assert(!added.url);
    assert.equal(confirmations['竃'].label,'维护者手工补充并确认');
});
