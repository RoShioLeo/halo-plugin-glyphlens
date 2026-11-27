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
        if (path.startsWith('lexicon.json')) return {ok: true, json: async () => options.rules || []};
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

test('default mode exports plain converted text and does not show the review entry', async t => {
    const {document,get,convert,copied}=await fixture(t);
    assert.equal(document.querySelector('input[name="mode"]:checked').value,'one2one');
    assert.equal(document.querySelectorAll('.controls select').length,0);
    assert.equal(document.querySelectorAll('.controls fieldset').length,1);
    await convert('乾乾');assert.equal(get('outputText').value,'乾乾');
    assert.equal(get('outputText').hidden,false);assert.equal(get('reviewPanel').hidden,true);assert.equal(get('startReview').hidden,true);
    get('copyOutput').click();await pause(0);assert.deepEqual(copied,['乾乾']);
});
test('candidate markers stay visible before Start and after End while choices survive view changes', async t => {
    const {window,document,get,radio,convert,copied}=await fixture(t);
    radio('modeCandidates');assert.equal(get('startReview').disabled,true);
    await convert('乾乾');assert.equal(get('reviewPanel').hidden,false);assert.equal(get('reviewToolbar').hidden,true);assert.equal(get('candidatePicker').hidden,true);assert.equal(get('outputText').hidden,true);
    assert.equal(document.querySelectorAll('.review-word').length,2);
    assert(get('startReview').classList.contains('primary-button'));assert.deepEqual([...get('startReview').parentElement.children].map(button=>button.id),['copyOutput','downloadOutput','startReview']);
    get('startReview').click();assert.equal(get('reviewPanel').hidden,false);assert.equal(get('reviewStatus').textContent,'待校对 2 / 2 处');
    document.querySelector('.review-word.is-active').dispatchEvent(new window.KeyboardEvent('keydown',{key:'2',bubbles:true,cancelable:true}));
    assert.equal(get('outputText').value,'干乾');get('previousCandidate').click();
    get('candidateChoices').querySelector('[data-candidate="乾"]').click();
    get('candidateChoices').querySelector('[data-candidate="干"]').click();
    assert.equal(get('outputText').value,'乾干');assert.equal(get('reviewStatus').textContent,'已校对 2 处');
    get('startReview').click();assert.equal(get('reviewPanel').hidden,false);assert.equal(get('reviewToolbar').hidden,true);assert.equal(get('candidatePicker').hidden,true);assert.equal(get('outputText').value,'乾干');
    radio('modeDefault');radio('modeCandidates');assert.equal(get('reviewPanel').hidden,false);assert.equal(get('reviewToolbar').hidden,true);
    get('startReview').click();assert.equal(document.querySelectorAll('.review-word.is-reviewed').length,2);
    get('copyOutput').click();await pause(0);assert.deepEqual(copied,['乾干']);
});
test('editing the original invalidates choices and requires explicitly starting the next review', async t => {
    const {get,radio,convert,input}=await fixture(t);radio('modeCandidates');await convert('乾');get('startReview').click();
    get('candidateChoices').querySelector('[data-candidate="干"]').click();assert.equal(get('outputText').value,'干');
    assert.equal(get('targetTraditional'),null);assert.equal(get('feedbackDirection'),null);get('openFeedback').click();
    input('新文本');assert.equal(get('outputText').value,'');assert.equal(get('copyOutput').disabled,true);
    assert.equal(get('downloadOutput').disabled,true);assert.equal(get('reviewPanel').hidden,true);assert.equal(get('startReview').disabled,true);
});
test('candidate files show marked previews before review and download selected text with original line endings', async t => {
    const {get,radio,file,idle,downloads,blobs}=await fixture(t);radio('modeCandidates');file('草稿.txt','乾\r\n');
    get('convertFile').click();await idle();assert.equal(downloads.length,0);assert.equal(get('reviewPanel').hidden,false);assert.equal(get('reviewToolbar').hidden,true);assert.equal(get('candidatePicker').hidden,true);
    assert.equal(get('inputText').value,'乾\r\n');get('startReview').click();
    get('candidateChoices').querySelector('[data-candidate="干"]').click();get('downloadOutput').click();
    assert.equal(downloads[0].name,'草稿-转换结果.txt');assert.equal(await blobs[0].text(),'干\r\n');
});
test('default file conversion keeps its direct download action', async t => {
    const {get,file,idle,downloads,blobs}=await fixture(t);file('正文.txt','發乾');get('convertFile').click();await idle();
    assert.equal(downloads.length,1);assert.equal(await blobs[0].text(),'发乾');
});
test('literal markup stays text and a late request never replaces newer input', async t => {
    let complete;const response=new Promise(resolve=>{complete=resolve;});
    const {get,radio,input,idle,convert}=await fixture(t,{dictionary:()=>response});
    input('旧文本');get('convertText').click();input('<b>乾</b>');
    complete({ok:true,json:async()=>({'乾':['乾','干']})});await idle();
    assert.equal(get('outputText').value,'');assert.equal(get('copyOutput').disabled,true);
    radio('modeCandidates');await convert('<b>乾</b>');get('startReview').click();
    assert.equal(get('outputText').value,'<b>乾</b>');assert.equal(get('reviewText').querySelector('b'),null);assert.equal(get('reviewText').textContent,'<b>乾</b>');
});
test('dictionary matches select the default candidate but wait for user confirmation', async t => {
    const {get,document,radio,convert,copied}=await fixture(t,{rules:[{character:'乾',candidate:'干',contexts:['干燥']}]});
    radio('modeCandidates');await convert('乾燥');assert.equal(get('outputText').value,'干燥');assert.equal(get('reviewPanel').hidden,false);assert.equal(get('reviewToolbar').hidden,true);assert.equal(get('candidatePicker').hidden,true);
    get('startReview').click();assert.equal(get('reviewStatus').textContent,'待校对 1 / 1 处');
    assert.equal(document.querySelectorAll('.review-word.is-reviewed').length,0);assert.equal(get('confirmRemaining').disabled,false);
    assert.equal(get('candidateChoices').querySelector('[data-candidate="干"]').getAttribute('aria-pressed'),'true');
    get('candidateChoices').querySelector('[data-candidate="乾"]').click();assert.equal(get('outputText').value,'乾燥');
    assert.equal(get('reviewStatus').textContent,'已校对 1 处');
    radio('modeDefault');get('copyOutput').click();await pause(0);assert.deepEqual(copied,['乾燥']);
});
test('Confirm remaining preserves displayed defaults and manual choices and allows later changes', async t => {
    const {get,document,radio,convert}=await fixture(t);radio('modeCandidates');await convert('乾乾乾');get('startReview').click();
    get('candidateChoices').querySelector('[data-candidate="干"]').click();const before=get('outputText').value;
    get('confirmRemaining').click();assert.equal(get('outputText').value,before);assert.equal(get('reviewStatus').textContent,'已校对 3 处');
    assert.equal(get('candidatePicker').hidden,true);assert.equal(get('confirmRemaining').disabled,true);
    assert.equal(document.querySelectorAll('.review-word.is-reviewed').length,3);
    document.querySelectorAll('.review-word')[1].click();get('candidateChoices').querySelector('[data-candidate="干"]').click();
    assert.equal(get('outputText').value,'干干乾');
});

test('clicking a preview marker starts proofreading at that exact position and keeps reviewed markers visible',async t=>{
    const {get,document,window,radio,convert}=await fixture(t);radio('modeCandidates');await convert('乾，乾，乾');
    const buttons=[...document.querySelectorAll('.review-word')];
    assert.equal(buttons.length,3);assert(buttons.every(button=>button.tabIndex===0));
    assert.equal(get('reviewToolbar').hidden,true);assert.equal(get('candidatePicker').hidden,true);
    buttons[2].dispatchEvent(new window.KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true,cancelable:true}));
    assert.equal(get('candidatePicker').hidden,true);
    buttons[2].click();
    assert.equal(get('startReview').textContent,'结束校对');assert.equal(get('reviewToolbar').hidden,false);
    assert(buttons[2].classList.contains('is-active'));assert(!buttons[0].classList.contains('is-active'));
    assert.equal(get('previousCandidate').getAttribute('aria-label'),'上一处');
    assert.equal(get('nextCandidate').getAttribute('aria-label'),'下一处');
    assert.equal(get('nextCandidate').parentElement.lastElementChild.id,'confirmRemaining');
    get('candidateChoices').querySelector('[data-candidate="干"]').click();
    assert.equal(get('outputText').value,'乾，乾，干');assert(buttons[2].classList.contains('is-reviewed'));
    get('startReview').click();
    assert.equal(get('reviewToolbar').hidden,true);assert.equal(get('candidatePicker').hidden,true);
    assert.equal(document.querySelectorAll('.review-word').length,3);assert(buttons[2].classList.contains('is-reviewed'));
    buttons[2].click();assert(buttons[2].classList.contains('is-active'));
    get('candidateChoices').querySelector('[data-candidate="乾"]').click();assert.equal(get('outputText').value,'乾，乾，乾');
});

test('mode help opens on mouse hover and keyboard focus and can be dismissed with Escape', async t => {
    const {get, window, document} = await fixture(t);
    const help = get('modeHelp'), toggle = get('modeHelpToggle'), content = get('modeHelpContent');
    const pointer = type => help.dispatchEvent(new window.PointerEvent(type, {pointerType: 'mouse'}));
    assert.equal(content.hidden, true);
    pointer('pointerenter');assert.equal(content.hidden, false);assert.equal(toggle.getAttribute('aria-expanded'), 'true');
    pointer('pointerleave');assert.equal(content.hidden, true);
    toggle.focus();assert.equal(content.hidden, false);
    document.dispatchEvent(new window.KeyboardEvent('keydown', {key: 'Escape', bubbles: true}));
    assert.equal(content.hidden, true);assert.equal(toggle.getAttribute('aria-expanded'), 'false');
    toggle.blur();toggle.focus();get('chooseFile').focus();assert.equal(content.hidden, true);
    assert.match(content.textContent, /下划线/);assert.match(content.textContent, /确认剩余/);
});

test('mode help is tap controlled on touch screens and closes on an outside tap', async t => {
    const {get, window} = await fixture(t);
    const help = get('modeHelp'), toggle = get('modeHelpToggle'), content = get('modeHelpContent');
    help.dispatchEvent(new window.PointerEvent('pointerenter', {pointerType: 'touch'}));assert.equal(content.hidden, true);
    toggle.focus();toggle.click();assert.equal(content.hidden, false);
    toggle.click();assert.equal(content.hidden, true);
    toggle.click();assert.equal(content.hidden, false);
    get('inputText').dispatchEvent(new window.PointerEvent('pointerdown', {pointerType: 'touch', bubbles: true}));
    assert.equal(content.hidden, true);assert.equal(toggle.getAttribute('aria-expanded'), 'false');
    toggle.click();assert.equal(content.hidden, false);
});
