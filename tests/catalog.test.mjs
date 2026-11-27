import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { gunzipSync } from 'node:zlib';
import { GlyphConverter } from '../web/engine.js';
const read=path=>JSON.parse(fs.readFileSync(new URL('../'+path,import.meta.url),'utf8'));
const dictionary=read('data/dictionary.json'),reviews=read('data/reviews.json');
const scalar=value=>[...value].length===1 && !(value.codePointAt(0)>=0xD800 && value.codePointAt(0)<=0xDFFF);

test('the one dictionary retains all 249 previously corroborated shared variants',()=>{
    const expected=JSON.parse(fs.readFileSync(new URL('./fixtures/formal-common.json',import.meta.url),'utf8'));
    assert.equal(Object.keys(expected).length,249);
    for(const [source,candidates]of Object.entries(expected)){assert.deepEqual(dictionary[source],candidates,source);assert(!reviews[source]);}
});
test('all explicitly accepted live corrections and candidate order are shipped as defaults',()=>{
    const decisions=read('research/maintainer-decisions.json');
    assert.equal(Object.keys(decisions.accepted).length,163);
    for(const [source,values]of Object.entries(decisions.accepted)){assert.deepEqual(dictionary[source],values,source);assert(!reviews[source],source);}
    const converter=new GlyphConverter(dictionary);
    assert.equal(converter.convert('內於為絕閱乾寗竃㐷彷畫箚麼'),'内于为绝阅干宁灶骂彷画劄么');
    for(const source of decisions.rejected){assert(!dictionary[source]);assert(!reviews[source]);assert.equal(converter.convert(source),source);}
    assert.deepEqual(dictionary['彷'],['彷','仿']);assert.deepEqual(dictionary['畫'],['画','划']);
    assert.deepEqual(dictionary['箚'],['劄','札']);assert.deepEqual(dictionary['麼'],['么']);
});
test('compiled mappings are scalars, unknown characters stay unchanged, and no traditional dictionary is shipped',()=>{
    assert.equal(Object.keys(dictionary).length,12656);
    for(const [source,values]of Object.entries(dictionary)) {
        assert(scalar(source));assert(values.length);assert(values.every(scalar));assert.equal(new Set(values).size,values.length);assert.notDeepEqual(values,[source]);
    }
    for(const file of ['common.json','v2s.json','v2t.json'])assert(!fs.existsSync(new URL('../data/'+file,import.meta.url)));
    assert.equal(new GlyphConverter(dictionary).convert('㐺㐁〇𪷍'),'㐺㐁〇𪷍');
    assert.equal(dictionary['干'],undefined);
});
test('pending targets are standard simplified glyphs or the original, never inferred traditional targets',()=>{
    const table=JSON.parse(gunzipSync(fs.readFileSync(new URL('../research/inputs/TGH-transcription.json.gz',import.meta.url))));
    const standard=new Set(table.rows.map(row=>row.c));
    for(const [source,review]of Object.entries(reviews)) {
        assert(review.candidates.some(c=>!(dictionary[source]||[source]).includes(c)),source);
        for(const target of review.candidates)assert(target===source || standard.has(target),source+' → '+target);
        assert(!review.reasons.some(reason=>reason.includes('繁体补充') || reason.includes('具体繁体归属')));
    }
    assert.equal(Object.keys(reviews).length,62);
});
test('vertical punctuation and source data work through the same dictionary',()=>{
    const converter=new GlyphConverter(dictionary);
    assert.equal(converter.convert('︵︶︻︼﹁﹂﹃﹄︽︾︿﹀'),'（）【】「」『』《》〈〉');
    const sources=read('data/sources.json');assert.equal(sources.summary.counts.records,12656);
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
