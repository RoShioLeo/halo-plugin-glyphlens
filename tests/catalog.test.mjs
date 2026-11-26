import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { GlyphConverter } from '../web/engine.js';
const read=name=>JSON.parse(fs.readFileSync(new URL('../data/'+name,import.meta.url),'utf8'));
const common=read('common.json'),simple=read('v2s.json'),traditional=read('v2t.json');
const scalar=value=>[...value].length===1 && !(value.codePointAt(0)>=0xD800&&value.codePointAt(0)<=0xDFFF);
test('all compiled records are single Unicode scalars and directional tables do not duplicate common',()=>{
    for(const dictionary of [common,simple,traditional])for(const [source,candidates]of Object.entries(dictionary)) {
        assert(scalar(source));assert(candidates.length>0);assert(candidates.every(scalar));assert.equal(new Set(candidates).size,candidates.length);
    }
    for(const direction of [simple,traditional])for(const source of Object.keys(direction))assert(!Object.hasOwn(common,source));
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
    assert.deepEqual(table['𪷍'],['㵾']);
});

test('usage tiers put common candidates before rare forms without adding candidates',()=>{
    const sc={...common,...simple},tc={...common,...traditional};
    assert.deepEqual(sc['㐺'],['众','乑']);
    assert.deepEqual(sc['㐁'],['西','㐁']);
    assert.deepEqual(tc['㐁'],['西','㐁']);
    assert.equal(sc['〇'][0],'〇');assert.equal(tc['〇'][0],'〇');
    assert.deepEqual(tc['台'],['台','臺','颱','檯']);
});
