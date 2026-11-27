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
    if (options.savedMode !== undefined) window.localStorage.setItem('glyphlens.mode', options.savedMode);
    if (options.blockedStorage) Object.defineProperty(window, 'localStorage', {get() { throw new Error('Storage unavailable'); }});
    const saved = new Map(), copied = [], downloads = [], blobs = [];
    for (const [name, value] of Object.entries({window, document, navigator: window.navigator, fetch: async path => {
        path = String(path);
        if (path === 'access') return {ok: true, json: async () => ({canManage: false})};
        if (path === 'version.json') return {ok: true, json: async () => ({version: options.dictionaryVersion || '1.0.0'})};
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
        Object.defineProperty(get('fileInput'), 'files', {value: name ? [{name, arrayBuffer: typeof text === 'function' ? text : async () => new TextEncoder().encode(text).buffer}] : [], configurable: true});
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
    const startClass=get('startReview').className;
    assert(get('startReview').classList.contains('primary-button'));assert.deepEqual([...get('startReview').parentElement.children].map(button=>button.id),['copyOutput','downloadOutput','startReview']);
    get('startReview').click();assert.equal(get('reviewPanel').hidden,false);assert.equal(get('reviewStatus').textContent,'待校对 2 / 2 处');
    assert.equal(get('startReview').className,startClass);assert.equal(get('outputLabel').textContent,'转换结果');
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
test('files import into the original first and share the normal conversion and proofreading actions', async t => {
    const {get,radio,file,idle,downloads,blobs}=await fixture(t);radio('modeCandidates');file('草稿.txt','乾\r\n');
    await idle();assert.equal(get('convertFile'),null);assert.equal(get('chooseFile').textContent,'从文件导入文本');assert.equal(get('outputText').value,'');assert.equal(get('startReview').hidden,true);
    get('convertText').click();await idle();assert.equal(downloads.length,0);assert.equal(get('reviewPanel').hidden,false);assert.equal(get('reviewToolbar').hidden,true);assert.equal(get('candidatePicker').hidden,true);
    assert.equal(get('inputText').value,'乾\r\n');get('startReview').click();
    get('candidateChoices').querySelector('[data-candidate="干"]').click();get('downloadOutput').click();
    assert.equal(downloads[0].name,'草稿-转换结果.txt');assert.equal(await blobs[0].text(),'干\r\n');
});
test('default mode also imports without converting or downloading until explicitly requested', async t => {
    const {get,file,idle,downloads,blobs}=await fixture(t);file('正文.txt','發乾');await idle();
    assert.equal(get('inputText').value,'發乾');assert.equal(get('outputText').value,'');assert.equal(downloads.length,0);
    get('convertText').click();await idle();assert.equal(get('outputText').value,'发乾');assert.equal(downloads.length,0);get('downloadOutput').click();
    assert.equal(downloads.length,1);assert.equal(await blobs[0].text(),'发乾');
});

test('a slow import never replaces newer typing and invalid UTF-8 leaves existing text intact', async t => {
    const {get,file,idle,input,convert}=await fixture(t);let complete;
    file('旧稿.txt',()=>new Promise(resolve=>{complete=resolve;}));input('新输入');
    complete(new TextEncoder().encode('旧稿').buffer);await idle();assert.equal(get('inputText').value,'新输入');
    await convert('乾');file('损坏.txt',async()=>new Uint8Array([0xff]).buffer);await idle();
    assert.equal(get('inputText').value,'乾');assert.equal(get('outputText').value,'乾');assert.equal(get('conversionStatus').dataset.error,'true');
    file(null);assert.equal(get('inputText').value,'乾');assert.equal(get('outputText').value,'乾');
});

test('the last used mode is restored and output keeps its fixed label', async t => {
    const {document,window,get,radio,convert}=await fixture(t,{savedMode:'one2many'});
    assert.equal(document.querySelector('input[name="mode"]:checked').value,'one2many');assert.equal(get('startReview').hidden,true);
    await convert('發');assert.equal(get('outputLabel').textContent,'转换结果');assert.equal(get('startReview').hidden,true);
    await convert('乾');assert.equal(get('startReview').hidden,false);get('startReview').click();
    assert.equal(get('outputLabel').textContent,'转换结果');radio('modeDefault');
    assert.equal(window.localStorage.getItem('glyphlens.mode'),'one2one');assert.equal(get('outputLabel').textContent,'转换结果');
});

test('invalid saved modes fall back to default conversion', async t => {
    const {document}=await fixture(t,{savedMode:'unknown'});
    assert.equal(document.querySelector('input[name="mode"]:checked').value,'one2one');
});

test('the footer shows independent installed dictionary and plugin versions', async t => {
    const {get}=await fixture(t,{dictionaryVersion:'1.2.3'});await pause(0);
    assert.equal(get('dictionaryVersion').textContent,'v1.2.3');assert.equal(get('appVersion').textContent,'v0.2.3');
});

test('unavailable storage does not prevent conversion or mode switching', async t => {
    const {get,radio,convert}=await fixture(t,{blockedStorage:true});
    radio('modeCandidates');await convert('乾');assert.equal(get('startReview').hidden,false);
});

test('the example demonstrates variant glyphs and contextual candidates without changing the chosen mode', async t => {
    const table=JSON.parse(await readFile(new URL('../dictionary/data/dictionary.json',import.meta.url),'utf8'));
    const {get,document,radio,idle,input}=await fixture(t,{
        dictionary:async()=>({ok:true,json:async()=>table})
    });
    get('loadExample').click();await idle();
    assert(get('loadExample').classList.contains('quiet-button'));
    assert.match(get('inputText').value,/畢昇/);assert.match(get('inputText').value,/纔/);assert(!get('inputText').value.includes('\n'));
    assert.match(get('outputText').value,/毕升/);assert.match(get('outputText').value,/才毕/);assert.equal(get('exampleSource').hidden,false);
    assert.match(get('exampleSource').href,/oldid=7260838/);
    assert.equal(document.querySelector('input[name="mode"]:checked').value,'one2one');
    radio('modeCandidates');assert.equal(document.querySelectorAll('.review-word').length,1);assert.equal(get('startReview').hidden,false);
    assert.equal(document.querySelectorAll('.review-word.is-reviewed').length,0);
    document.querySelector('.review-word').click();get('candidateChoices').querySelector('[data-candidate="昇"]').click();assert.match(get('outputText').value,/毕昇/);
    input('其他原文');assert.equal(get('exampleSource').hidden,true);
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
