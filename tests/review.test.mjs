import test from 'node:test';
import assert from 'node:assert/strict';
import { GlyphConverter } from '../web/engine.js';

test('default conversion is plain text; review choices are independent occurrences', () => {
    const converter = new GlyphConverter('v2t', {'发': ['發', '髮'], A: ['B'], B: ['C']});
    assert.equal(converter.convert('发发A'), '發發B');
    const document = converter.review('发发A');
    const [first, second] = document.positions;
    assert.equal(document.text(), '發發B');
    document.choose(second, '髮');
    assert.equal(document.text(), '發髮B');
    assert.equal(document.pending, 1);
    assert.equal(document.reviewed(first), false);
    document.choose(first, '發');
    assert.equal(document.pending, 0);
    assert.equal(document.next(second, 1, true), null);
    assert.equal(document.next(second), first);
    assert.equal(document.next(first, -1), second);
    assert.throws(() => document.choose(first, '发'), /合法候选/);
    assert.equal(converter.review('发发A').text(), '發發B');
});

test('review preserves exact unknown glyph sequences, line endings and literal markup', () => {
    const converter = new GlyphConverter('v2t', {'干': ['干', '幹'], '𠀀': ['一', '壹']});
    const input = '干\r\n𠀀 干\u{E0100} e\u0301 👩‍💻 <script>【原文】</script>';
    const document = converter.review(input);
    assert.equal(document.positions.length, 2);
    document.choose(document.positions[0], '幹');
    document.choose(document.positions[1], '壹');
    assert.equal(document.text(), '幹\r\n壹 干\u{E0100} e\u0301 👩‍💻 <script>【原文】</script>');
});

test('lexicon-resolved positions do not require review; conflicts only expose approved candidates', () => {
    const converter = new GlyphConverter('v2t', {'干': ['干', '乾', '幹']}, [
        {phrase: '干燥', choices: [{offset: 0, source: '干', target: '乾'}]},
        {phrase: '干事', choices: [{offset: 0, source: '干', target: '乾'}]},
        {phrase: '干事', choices: [{offset: 0, source: '干', target: '幹'}]},
    ]);
    const document = converter.review('干燥 干事 干');
    assert.equal(document.positions.length, 2);
    assert.equal(document.text(), converter.convert('干燥 干事 干'));
    assert.deepEqual(document.parts[document.positions[0]].candidates, ['乾', '幹']);
    assert.throws(() => document.choose(document.positions[0], '干'), /合法候选/);
    converter.install({}, converter.lexicon.rules);
    assert.equal(converter.review('干燥').positions.length, 0);
    assert.equal(converter.review('干燥').text(), '干燥');
});

test('next pending occurrence skips confirmed choices and wraps once', () => {
    const document = new GlyphConverter('v2s', {'乾': ['乾', '干']}).review('乾，乾，乾');
    const [first, second, third] = document.positions;
    document.choose(second, '干');
    assert.equal(document.next(first, 1, true), third);
    assert.equal(document.next(third, 1, true), first);
    assert.equal(document.next(first, -1, true), third);
    assert.equal(new GlyphConverter('v2s', {}).review('').next(null), null);
});
