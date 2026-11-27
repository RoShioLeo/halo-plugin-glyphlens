import test from 'node:test';
import assert from 'node:assert/strict';
import { GlyphConverter } from '../web/engine.js';
const rule=(character,candidate,...contexts)=>({character,candidate,contexts});
test('default results use the first candidate and manual choices belong to individual occurrences',()=>{
    const converter=new GlyphConverter({'乾':['干','乾'],A:['B'],B:['C']});
    const doc=converter.review('乾乾A'),[first,second]=doc.positions;
    assert.equal(doc.text(),'干干B');doc.choose(second,'乾');assert.equal(doc.text(),'干乾B');assert.equal(doc.pending,1);
    assert.equal(doc.reviewed(first),false);doc.choose(first,'干');assert.equal(doc.pending,0);
    assert.equal(doc.next(second,1,true),null);assert.equal(doc.next(second),first);assert.equal(doc.next(first,-1),second);
    assert.throws(()=>doc.choose(first,'幹'),/合法候选/);assert.equal(converter.review('乾乾A').text(),'干干B');
});
test('review preserves exact unknown glyph sequences, line endings and literal markup',()=>{
    const doc=new GlyphConverter({'乾':['干','乾'],'𠀀':['一','壹']}).review('乾\r\n𠀀 乾\u{E0100} e\u0301 👩‍💻 <script>【原文】</script>');
    assert.equal(doc.positions.length,2);doc.choose(doc.positions[0],'乾');doc.choose(doc.positions[1],'壹');
    assert.equal(doc.text(),'乾\r\n壹 乾\u{E0100} e\u0301 👩‍💻 <script>【原文】</script>');
});
test('dictionary suggestions remain unreviewed and preserve original candidates for manual overrides',()=>{
    const converter=new GlyphConverter({'乾':['干','乾']},[rule('乾','乾','乾坤')]);
    const doc=converter.review('乾坤 乾'),[matched,pending]=doc.positions;
    assert.equal(doc.text(),'乾坤 干');assert.equal(doc.pending,2);assert.equal(doc.reviewed(matched),false);assert.equal(doc.matched(matched),true);
    assert.deepEqual(doc.parts[matched].candidates,['干','乾']);assert.equal(doc.next(null,1,true),matched);
    doc.choose(matched,'干');assert.equal(doc.text(),'干坤 干');assert.equal(doc.reviewed(matched),true);assert.equal(doc.matched(matched),true);
    assert.equal(doc.pending,1);assert.equal(doc.matchedCount,1);
});
test('conflicts retain candidate access and cannot silently mark a position reviewed',()=>{
    const converter=new GlyphConverter({'乾':['干','乾','幹']},[rule('乾','幹','幹事'),rule('乾','乾','乾事')]);
    const doc=converter.review('乾事');assert.equal(doc.text(),'干事');assert.equal(doc.pending,1);assert.equal(doc.reviewed(doc.positions[0]),false);
    doc.choose(doc.positions[0],'幹');assert.equal(doc.text(),'幹事');assert.equal(doc.pending,0);
});
test('confirm remaining accepts currently displayed results without changing manual or dictionary choices',()=>{
    const doc=new GlyphConverter({'乾':['干','乾']},[rule('乾','乾','乾坤')]).review('乾坤 乾 乾');
    const [automatic,manual,pending]=doc.positions;doc.choose(manual,'乾');const before=doc.text();
    assert.equal(doc.pending,2);assert.equal(doc.confirmRemaining(),2);assert.equal(doc.text(),before);assert.equal(doc.pending,0);
    assert.equal(doc.matched(automatic),true);assert.equal(doc.reviewed(automatic),true);assert.equal(doc.value(manual),'乾');assert.equal(doc.value(pending),'干');assert.equal(doc.confirmRemaining(),0);
    doc.choose(pending,'乾');assert.equal(doc.text(),'乾坤 乾 乾');
});
test('pending navigation skips only user-confirmed positions and wraps once',()=>{
    const doc=new GlyphConverter({'乾':['干','乾']},[rule('乾','乾','乾坤')]).review('乾 乾坤 乾 乾');
    const [first,automatic,second,third]=doc.positions;doc.choose(second,'乾');
    assert.equal(doc.next(first,1,true),automatic);assert.equal(doc.next(automatic,1,true),third);assert.equal(doc.next(third,1,true),first);assert.equal(doc.next(first,-1,true),third);
    assert.equal(doc.next(first),automatic);assert.equal(doc.matched(automatic),true);assert.equal(doc.reviewed(automatic),false);assert.equal(new GlyphConverter({}).review('').next(null),null);
});
