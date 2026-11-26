import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { GlyphConverter } from '../web/engine.js';
const read=name=>JSON.parse(fs.readFileSync(new URL('../data/'+name,import.meta.url),'utf8'));
const common=read('common.json'),simple=read('v2s.json'),traditional=read('v2t.json');
const reviews=read('reviews.json');
const scalar=value=>[...value].length===1 && !(value.codePointAt(0)>=0xD800&&value.codePointAt(0)<=0xDFFF);
test('all compiled records are single Unicode scalars and directional tables do not duplicate common',()=>{
    for(const dictionary of [common,simple,traditional])for(const [source,candidates]of Object.entries(dictionary)) {
        assert(scalar(source));assert(candidates.length>0);assert(candidates.every(scalar));assert.equal(new Set(candidates).size,candidates.length);
        assert.notDeepEqual(candidates,[source],`identity-only entry: ${source}`);
    }
    for(const direction of [simple,traditional])for(const source of Object.keys(direction))assert(!Object.hasOwn(common,source));
    const commonKeys=new Set([...Object.keys(common),...Object.keys(reviews.common)]);
    for(const [direction,table] of [['v2s',simple],['v2t',traditional]]) {
        for(const source of [...Object.keys(table),...Object.keys(reviews[direction])])assert(!commonKeys.has(source),`common source repeated: ${source}`);
    }
    for(const [direction,table] of [['common',common],['v2s',simple],['v2t',traditional]]) {
        for(const [source,review] of Object.entries(reviews[direction]))assert(review.candidates.some(c=>!(table[source]||[source]).includes(c)),`review has no extra candidate: ${source}`);
    }
});
test('the twelve explicitly requested vertical punctuation pairs work in both directions',()=>{
    for(const [direction,table]of [['v2s',simple],['v2t',traditional]]) {
        const converter=new GlyphConverter(direction,{...common,...table});
        assert.equal(converter.convert('︵︶︻︼﹁﹂﹃﹄︽︾︿﹀'),'（）【】「」『』《》〈〉');
    }
});
test('documented multiple forms remain usable with original-first coexisting characters',()=>{
    const sc=new GlyphConverter('v2s',{...common,...simple});
    const tc=new GlyphConverter('v2t',{...common,...traditional});
    assert.equal(sc.convert('乾'),'【乾|干】');assert.equal(tc.convert('干'),'【干|乾|幹】');assert.equal(tc.convert('发'),'【發|髮】');
    sc.setMode('one2one');tc.setMode('one2one');assert.equal(sc.convert('乾'),'乾');assert.equal(tc.convert('干'),'干');
});
test('screenshot sources no longer produce the rare inferred simplified targets',()=>{
    const table={...common,...simple};
    for(const [source,rejected]of [['㵾','𪷍'],['㶆','𫞛'],['㷍','𤆢'],['㷿','𤈷'],['㸇','𤎺']])assert(!(table[source]||[source]).includes(rejected));
    assert.equal(table['𪷍'],undefined); // Uncorroborated reverse relation remains inactive.
});

test('remaining formal usage tiers rank candidates without the withdrawn grading table',()=>{
    const sc={...common,...simple},tc={...common,...traditional};
    assert.deepEqual(sc['仝'],['同','仝']);
    assert.deepEqual(sc['線'],['线','缐']);
    assert.deepEqual(tc['台'],['台','檯','臺','颱']);
});

test('protected original characters stay unchanged and reference never enters the review queue',()=>{
    for(const [direction,table] of [['v2s',simple],['v2t',traditional]]) {
        const converter=new GlyphConverter(direction,{...common,...table});
        assert.equal(converter.convert('㐺㐁〇𪷍'),'㐺㐁〇𪷍');
        for(const mode of ['common',direction])for(const source of ['㐺','㐁'])assert.equal(reviews[mode][source],undefined);
    }
    const sources=read('sources.json');
    assert.deepEqual(Object.keys(sources.links).sort(),['mainland','opencc','unicode','variants']);
    assert(!/moe\.edu\.tw|台湾|異體字字典/.test(JSON.stringify(reviews)));
    const manifest=JSON.parse(fs.readFileSync(new URL('../research/inputs-manifest.json',import.meta.url),'utf8'));
    assert.deepEqual(Object.keys(manifest.files).sort(),['moe-regular.json','moe-relations.json','properties.json','sources/STCharacters.txt','sources/TGH-transcription.json','sources/TSCharacters.txt']);
});

test('unique common-target variants are active without approval while rare reverse targets stay excluded',()=>{
    for(const [direction,table] of [['v2s',simple],['v2t',traditional]]) {
        const converter=new GlyphConverter(direction,{...common,...table});
        assert.equal(converter.convert('㐀㐅伱'),'丘五你');
        for(const source of ['㐀','㐅','伱'])assert(!reviews.common[source] && !reviews[direction][source]);
    }
});
