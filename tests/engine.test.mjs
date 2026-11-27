import test from 'node:test';
import assert from 'node:assert/strict';
import { GlyphConverter } from '../web/engine.js';
import { DisambiguationIndex } from '../web/lexicon.js';
const rule=(character,candidate,...contexts)=>({character,candidate,contexts});

test('unmapped glyph sequences preserve their base, selectors and combining marks in both modes',()=>{
    const converter=new GlyphConverter({'乾':['干'],'𠀀':['一'],e:['E'],'❤':['心'],'👩':['女']});
    for(const mode of ['one2one','one2many'])for(const sequence of ['乾\uFE00','乾\u{E0100}','𠀀\u{E01EF}','e\u0301','❤️','👩‍💻'])
        assert.equal(converter.setMode(mode).convert(sequence+'乾'),sequence+'干');
});
test('context matching never crosses protected glyph sequences',()=>{
    const converter=new GlyphConverter({'乾':['干','乾']},[rule('乾','干','干燥','水干')]);
    assert.equal(converter.setMode('one2many').convert('水乾\u{E0100}燥 乾燥'),'水乾\u{E0100}燥 干燥');
    assert.equal(converter.convert('水\uFE00乾'),'水\uFE00【干|乾】');
    assert.equal(converter.review('水\uFE00乾').pending,1);
});
test('candidate order is stable and every original character is replaced exactly once',()=>{
    const converter=new GlyphConverter({A:['B'],B:['C'],'𠀀':['一'],'乾':['干','乾','干']});
    assert.equal(converter.convert('A𠀀乾🙂'),'B一干🙂');
    assert.equal(converter.setMode('one2many').convert('A乾'),'B【干|乾】');
    assert.throws(()=>converter.setMode('automatic'),/未知/);
});
test('supplementary scalars work and dictionary entries cannot become word replacements',()=>{
    assert.equal(new GlyphConverter({'𪷍':['㵾']}).convert('𪷍，㵾\nabc'),'㵾，㵾\nabc');
    for(const dictionary of [{'词语':['字']},{'字':['词语']},{'\ud800':['一']}])assert.throws(()=>new GlyphConverter(dictionary),/单字/);
});
test('compatibility aliases remain explicit and unknown codepoints are untouched',()=>{
    assert.equal(new GlyphConverter({'豈':['岂'],'\uf900':['岂']}).convert('\uf900豈'),'岂岂');
    assert.equal(new GlyphConverter({}).convert('\uf900e\u0301'),'\uf900e\u0301');
});
test('only ambiguous source characters consult simplified contexts across approved neighbouring glyphs',()=>{
    const converter=new GlyphConverter({'乾':['干','乾'],'沈':['沈','沉'],'沒':['没'],'陽':['阳'],'彷':['彷','仿'],'彿':['佛']},[
        rule('乾','干','干燥'),rule('乾','乾','乾坤'),rule('沈','沉','沉没'),rule('沈','沈','沈阳'),rule('彷','仿','仿佛')]);
    for(const mode of ['one2one','one2many'])assert.equal(converter.setMode(mode).convert('乾燥乾坤沈沒沈陽彷彿'),'干燥乾坤沉没沈阳仿佛');
    const document=converter.review('乾燥乾坤沈沒沈陽彷彿');
    assert.equal(document.pending,5);assert.equal(document.matchedCount,5);
});
test('longer context wins while equal-length conflicting meanings remain unresolved',()=>{
    const converter=new GlyphConverter({'乾':['干','乾']},[
        rule('乾','干','干果','干物'),rule('乾','乾','乾果酒','乾物')]);
    assert.equal(converter.setMode('one2many').convert('乾果，乾果酒，乾物，乾'),'干果，乾果酒，【干|乾】物，【干|乾】');
    assert.equal(converter.review('乾物').pending,1);
});
test('a context cannot add a candidate or revive a withdrawn character relation',()=>{
    const rules=[rule('乾','干','干燥'),rule('乾','字','字隆')];
    const converter=new GlyphConverter({'乾':['干','乾']},rules);
    assert.equal(converter.setMode('one2many').convert('乾隆'),'【干|乾】隆');
    converter.install({'乾':['干']},rules);assert.equal(converter.review('乾燥').positions.length,0);
    converter.install({},rules);assert.equal(converter.convert('乾燥'),'乾燥');assert.equal(converter.review('乾燥').positions.length,0);
});
test('adjacent ambiguous neighbours match their possible glyphs without cascading decisions',()=>{
    const converter=new GlyphConverter({'乾':['干','乾'],'彷':['彷','仿']},[rule('乾','干','干仿'),rule('彷','仿','干仿')]);
    assert.equal(converter.convert('乾彷'),'干仿');
    assert.equal(converter.review('乾彷').pending,2);
});
test('repeated candidate positions and supplementary context characters use scalar offsets',()=>{
    const converter=new GlyphConverter({'乾':['干','乾']},[rule('乾','干','干干净净','𠀀干')]);
    assert.equal(converter.convert('乾乾净净𠀀乾'),'干干净净𠀀干');
    assert.equal(converter.review('乾乾净净𠀀乾').pending,3);
});
test('unrelated text never probes the per-character context index',()=>{
    const index=new DisambiguationIndex(Array.from({length:1000},(_,i)=>rule('乾','干','干'+i)));
    let lookups=0;
    assert.equal(index.resolve([...'普通文本abc'.repeat(5000)],()=>{lookups++;return [];}).size,0);
    assert.equal(lookups,0);
});
