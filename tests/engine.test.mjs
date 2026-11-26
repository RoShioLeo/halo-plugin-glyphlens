import test from 'node:test';
import assert from 'node:assert/strict';
import { GlyphConverter } from '../web/engine.js';

test('unmapped glyph sequences preserve their base, selectors and combining marks in both modes', () => {
    const converter = new GlyphConverter('v2s', {'乾':['干'], '𠀀':['一'], e:['E'], '❤':['心'], '👩':['女']});
    const sequences = ['乾\uFE00', '乾\u{E0100}', '𠀀\u{E01EF}', 'e\u0301', '❤️', '👩‍💻'];
    for (const mode of ['one2one', 'one2many']) {
        converter.setMode(mode);
        for (const sequence of sequences) assert.equal(converter.convert(sequence + '乾'), sequence + '干');
    }
});

test('lexicon cannot match through a protected glyph sequence or apply its choices to that glyph', () => {
    const converter = new GlyphConverter('v2s', {'乾':['乾','干']}, [
        {phrase:'乾燥', choices:[{offset:0,source:'乾',target:'干'}]},
        {phrase:'水乾', choices:[{offset:1,source:'乾',target:'干'}]},
    ]);
    assert.equal(converter.convert('水乾\u{E0100}燥 乾燥'), '水乾\u{E0100}燥 干燥');
    assert.equal(converter.convert('水\uFE00乾'), '水\uFE00【乾|干】');
    converter.setMode('one2one');
    assert.equal(converter.convert('水乾\u{E0100}燥 乾燥'), '水乾\u{E0100}燥 干燥');
});

test('both modes preserve candidate order and replace each original character only once', async () => {
    const converter = new GlyphConverter('v2t', {'发':['發','髮','發'], A:['B'], B:['C'], '𠀀':['一']});
    await converter.loaded;
    assert.equal(converter.convert('发A𠀀🙂'), '【發|髮】B一🙂');
    converter.setMode('one2one');
    assert.equal(converter.convert('发A𠀀🙂'), '發B一🙂');
    assert.throws(() => converter.setMode('automatic'), /未知/);
});

test('a supplementary source is one character and unknown text is unchanged', () => {
    const converter = new GlyphConverter('v2s', {'𪷍':['㵾']});
    assert.equal(converter.convert('𪷍，㵾\nabc'), '㵾，㵾\nabc');
    assert.throws(() => new GlyphConverter('v2s', {'词语':['字']}), /单字/);
    assert.throws(() => new GlyphConverter('v2s', {'字':['词语']}), /单字/);
    assert.throws(() => new GlyphConverter('v2s', {'\ud800':['一']}), /单字/);
});

test('compatibility aliases must be explicit and unmatched codepoints remain unchanged', () => {
    const converter = new GlyphConverter('v2s', {'豈':['岂'],'\uf900':['岂']});
    assert.equal(converter.convert('\uf900豈'), '岂岂');
    const unknown = new GlyphConverter('v2s', {});
    assert.equal(unknown.convert('\uf900e\u0301'), '\uf900e\u0301');
});

test('lexicon rules choose existing candidates without replacing words', () => {
    const rules = [
        {phrase:'乾隆', choices:[{offset:0, source:'乾', target:'乾'}]},
        {phrase:'乾燥', choices:[{offset:0, source:'乾', target:'干'}]},
        {phrase:'鼠标', choices:[{offset:0, source:'鼠', target:'滑'}, {offset:1, source:'标', target:'鼠'}]},
    ];
    const converter = new GlyphConverter('v2s', {'乾':['乾','干']}, rules);
    assert.equal(converter.convert('乾隆与乾燥，鼠标'), '乾隆与干燥，鼠标');
    converter.setMode('one2one');
    assert.equal(converter.convert('乾隆与乾燥，鼠标'), '乾隆与干燥，鼠标');
});

test('longest phrase wins per position; equal-length conflicting rules retain candidates', () => {
    const converter = new GlyphConverter('v2t', {'干':['干','乾','幹']}, [
        {phrase:'干货', choices:[{offset:0, source:'干', target:'乾'}]},
        {phrase:'干货架', choices:[{offset:0, source:'干', target:'干'}]},
        {phrase:'干事', choices:[{offset:0, source:'干', target:'干'}]},
        {phrase:'干事', choices:[{offset:0, source:'干', target:'幹'}]},
    ]);
    assert.equal(converter.convert('干货，干货架，干事'), '乾货，干货架，【干|幹】事');
});

test('lexicon offsets use code points and cannot bypass withdrawn character relations', () => {
    const rules=[{phrase:'𠀀干', choices:[{offset:1,source:'干',target:'幹'}]}];
    const converter=new GlyphConverter('v2t', {'干':['干','幹']}, rules);
    assert.equal(converter.convert('𠀀干'), '𠀀幹');
    converter.install({}, rules);
    assert.equal(converter.convert('𠀀干'), '𠀀干');
});
