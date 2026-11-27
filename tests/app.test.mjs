import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { readFile } from 'node:fs/promises';
import { setTimeout as pause } from 'node:timers/promises';
const { Window } = createRequire(new URL('../ui/package.json', import.meta.url))('happy-dom');
const html = await readFile(new URL('../web/index.html', import.meta.url), 'utf8');
let fixtureId = 0;

async function fixture(t, options = {}) {
    const window = new Window({url: 'https://glyphlens.example.test/tools/glyphlens/',
        settings: {disableJavaScriptFileLoading: true, disableCSSFileLoading: true}});
    const { document } = window;
    document.write(html);
    const saved = new Map(), copied = [], downloads = [], blobs = [];
    for (const [name, value] of Object.entries({window, document, navigator: window.navigator, fetch: async path => {
        path = String(path);
        if (path === 'access') return {ok: true, json: async () => ({canManage: false})};
        if (path.startsWith('lexicon.json')) return {ok: true, json: async () => []};
        if (path === 'dictionary.json') {
            if (options.dictionary) return options.dictionary(path);
            const value = {'乾': ['乾', '干'], '發': ['发']};
            return {ok: true, json: async () => value};
        }
        throw new Error('Unexpected request: ' + path);
    }})) {
        saved.set(name, Object.getOwnPropertyDescriptor(globalThis, name));
        Object.defineProperty(globalThis, name, {value, configurable: true, writable: true});
    }
    Object.defineProperty(window.navigator, 'clipboard', {value: {writeText: async text => copied.push(text)}, configurable: true});
    const originalCreate = URL.createObjectURL, originalRevoke = URL.revokeObjectURL;
    URL.createObjectURL = blob => { blobs.push(blob); return 'blob:review-test-' + blobs.length; };
    URL.revokeObjectURL = () => {};
    window.HTMLAnchorElement.prototype.click = function () { downloads.push({name: this.download, url: this.href}); };
    t.after(async () => {
        await window.happyDOM.close();
        URL.createObjectURL = originalCreate;
        URL.revokeObjectURL = originalRevoke;
        for (const [name, descriptor] of saved) {
            if (descriptor) Object.defineProperty(globalThis, name, descriptor);
            else delete globalThis[name];
        }
    });
    await import(new URL(`../web/app.js?fixture=${++fixtureId}`, import.meta.url));
    const get = id => document.getElementById(id);
    const radio = id => {
        const input = get(id);
        input.checked = true;
        input.dispatchEvent(new window.Event('change', {bubbles: true}));
    };
    const input = text => { get('inputText').value = text; get('inputText').dispatchEvent(new window.Event('input', {bubbles: true})); };
    const idle = async () => {
        for (let attempt = 0; attempt < 100 && get('convertText').disabled; attempt++) await pause(2);
        assert.equal(get('convertText').disabled, false, 'conversion completed');
    };
    const convert = async text => { input(text); get('convertText').click(); await idle(); };
    const file = (name, text) => {
        Object.defineProperty(get('fileInput'), 'files', {value: [{name, arrayBuffer: async () => new TextEncoder().encode(text).buffer}], configurable: true});
        get('fileInput').dispatchEvent(new window.Event('change', {bubbles: true}));
    };
    return {window, document, get, radio, input, idle, convert, file, copied, downloads, blobs};
}

test('default mode uses native capsule radio groups and exports plain converted text', async t => {
    const {document, get, convert, copied} = await fixture(t);
    assert.equal(document.querySelector('input[name="mode"]:checked').value, 'one2one');
    assert.equal(document.querySelectorAll('.controls select').length, 0);
    assert.equal(document.querySelectorAll('.controls fieldset').length, 1);
    await convert('乾乾');
    assert.equal(get('outputText').value, '乾乾');
    assert.equal(get('outputText').hidden, false);
    assert.equal(get('reviewPanel').hidden, true);
    get('copyOutput').click();
    await pause(0);
    assert.deepEqual(copied, ['乾乾']);
});

test('per-occurrence choices auto-advance, support keyboard, and survive view changes', async t => {
    const {window, document, get, radio, convert, copied} = await fixture(t);
    radio('modeCandidates');
    await convert('乾乾');
    assert.equal(get('reviewPanel').hidden, false);
    assert.equal(get('outputText').hidden, true);
    assert.equal(get('reviewStatus').textContent, '待校对 2 / 2 处');
    document.querySelector('.review-word.is-active').dispatchEvent(new window.KeyboardEvent('keydown', {key: '2', bubbles: true, cancelable: true}));
    assert.equal(get('outputText').value, '干乾');
    get('previousCandidate').click();
    get('candidateChoices').querySelector('[data-candidate="乾"]').click();
    get('candidateChoices').querySelector('[data-candidate="干"]').click();
    assert.equal(get('outputText').value, '乾干');
    assert.equal(get('reviewStatus').textContent, '已校对 2 处');
    radio('modeDefault');
    assert.equal(get('outputText').hidden, false);
    assert.equal(get('outputText').value, '乾干');
    radio('modeCandidates');
    assert.equal(document.querySelectorAll('.review-word.is-reviewed').length, 2);
    get('copyOutput').click();
    await pause(0);
    assert.deepEqual(copied, ['乾干']);
});

test('changing the original invalidates choices and the removed direction controls never return', async t => {
    const {get, radio, convert, input, idle} = await fixture(t);
    radio('modeCandidates');
    await convert('乾');
    get('candidateChoices').querySelector('[data-candidate="干"]').click();
    assert.equal(get('outputText').value, '干');
    assert.equal(get('targetTraditional'), null);
    assert.equal(get('feedbackDirection'), null);
    get('openFeedback').click();
    input('新文本');
    assert.equal(get('outputText').value, '');
    assert.equal(get('copyOutput').disabled, true);
    assert.equal(get('downloadOutput').disabled, true);
    assert.equal(get('reviewPanel').hidden, true);
});

test('file candidate mode previews before download and exports only chosen text with exact line endings', async t => {
    const {get, radio, file, idle, downloads, blobs} = await fixture(t);
    radio('modeCandidates');
    file('草稿.txt', '乾\r\n');
    get('convertFile').click();
    await idle();
    assert.equal(downloads.length, 0);
    assert.equal(get('inputText').value, '乾\r\n');
    get('candidateChoices').querySelector('[data-candidate="干"]').click();
    get('downloadOutput').click();
    assert.equal(downloads[0].name, '草稿-转换结果.txt');
    assert.equal(await blobs[0].text(), '干\r\n');
});

test('default file conversion keeps the direct download action', async t => {
    const {get, file, idle, downloads, blobs} = await fixture(t);
    file('正文.txt', '發乾');
    get('convertFile').click();
    await idle();
    assert.equal(downloads.length, 1);
    assert.equal(await blobs[0].text(), '发乾');
});

test('literal markup is rendered as text and cancelling an in-flight input never replaces new work', async t => {
    let complete;
    const response = new Promise(resolve => { complete = resolve; });
    const {get, radio, input, idle, convert} = await fixture(t, {dictionary: () => response});
    input('旧文本');
    get('convertText').click();
    input('<b>乾</b>');
    complete({ok: true, json: async () => ({'乾': ['乾', '干']})});
    await idle();
    assert.equal(get('outputText').value, '');
    assert.equal(get('copyOutput').disabled, true);
    radio('modeCandidates');
    await convert('<b>乾</b>');
    assert.equal(get('outputText').value, '<b>乾</b>', get('conversionStatus').textContent);
    assert.equal(get('reviewText').querySelector('b'), null);
    assert.equal(get('reviewText').textContent, '<b>乾</b>');
});
