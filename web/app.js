// SPDX-License-Identifier: MIT
import { GlyphConverter } from './engine.js';
import { ReviewView } from './review-view.js';
import { example } from './example.js';
let converterCache = null;
const element = id => document.getElementById(id);
const modeStorageKey = 'glyphlens.mode';
try {
    const savedMode = window.localStorage.getItem(modeStorageKey);
    if (savedMode === 'one2one' || savedMode === 'one2many') {
        element(savedMode === 'one2many' ? 'modeCandidates' : 'modeDefault').checked = true;
    }
} catch (_) { /* Storage may be unavailable in private or restricted browsers. */ }
const selected = name => [...document.getElementsByName(name)].find(input => input.checked).value;
const isReviewMode = () => selected('mode') === 'one2many';
let result = null, reviewing = false, revision = 0, operations = 0, importedText = null, outputFilename = '字镜-转换结果.txt';
const status = (message, error = false) => {
    element('conversionStatus').textContent = message;
    element('conversionStatus').dataset.error = String(error);
};

fetch('access', {credentials: 'same-origin', cache: 'no-store'})
    .then(response => response.ok ? response.json() : {canManage: false})
    .then(access => { element('dictionaryAdmin').hidden = access.canManage !== true; })
    .catch(() => { element('dictionaryAdmin').hidden = true; });
function refreshDictionaryVersion() {
    fetch('version.json', {credentials: 'same-origin', cache: 'no-store'})
        .then(response => response.ok ? response.json() : null)
        .then(metadata => {if (/^\d+\.\d+\.\d+$/.test(metadata?.version)) element('dictionaryVersion').textContent = 'v' + metadata.version;})
        .catch(() => {});
}
refreshDictionaryVersion();
window.addEventListener('focus', () => { converterCache = null;refreshDictionaryVersion(); });

const modeHelp = element('modeHelp'), modeHelpToggle = element('modeHelpToggle'), modeHelpContent = element('modeHelpContent');
let helpPinned = false;
function showModeHelp(open) {
    modeHelpContent.hidden = !open;
    modeHelpToggle.setAttribute('aria-expanded', String(open));
}
modeHelpToggle.addEventListener('click', () => { helpPinned = !helpPinned; showModeHelp(helpPinned); });
modeHelp.addEventListener('pointerenter', event => { if (event.pointerType === 'mouse') showModeHelp(true); });
modeHelp.addEventListener('pointerleave', event => {
    if (event.pointerType === 'mouse' && !helpPinned && !modeHelp.contains(document.activeElement)) showModeHelp(false);
});
modeHelpToggle.addEventListener('focus', () => showModeHelp(true));
modeHelp.addEventListener('focusout', event => { if (!helpPinned && !modeHelp.contains(event.relatedTarget)) showModeHelp(false); });
document.addEventListener('pointerdown', event => { if (!modeHelp.contains(event.target)) { helpPinned = false; showModeHelp(false); } });
document.addEventListener('keydown', event => { if (event.key === 'Escape') { helpPinned = false; showModeHelp(false); } });

const review = new ReviewView({
    panel: element('reviewPanel'), toolbar: element('reviewToolbar'), text: element('reviewText'), status: element('reviewStatus'),
    previous: element('previousCandidate'), next: element('nextCandidate'), picker: element('candidatePicker'),
    confirmRemaining: element('confirmRemaining'),
    source: element('candidateSource'), choices: element('candidateChoices'),
}, document => { if (result === document) { renderOutput(); status(''); } },()=>{
    if(!result || !isReviewMode())return;
    reviewing=true;renderOutput();status('');
});

function renderOutput() {
    const text = result?.text() ?? '';
    const preview = !!result && isReviewMode();
    const active = preview && reviewing;
    element('outputText').value = text;
    element('copyOutput').disabled = element('downloadOutput').disabled = !text;
    element('outputText').hidden = preview;
    element('startReview').hidden = !isReviewMode() || !result?.positions.length;
    element('startReview').disabled = !result;
    element('startReview').textContent = active ? '结束校对' : '开始校对';
    if (preview) review.show(result,active);
    else element('reviewPanel').hidden = true;
}

function invalidate() {
    revision++;
    result = null;
    reviewing = false;
    importedText = null;
    outputFilename = '字镜-转换结果.txt';
    element('exampleSource').hidden = true;
    review.clear();
    renderOutput();
    status('');
}

function updateFileControls() {
    element('convertText').disabled = operations > 0;
    element('chooseFile').disabled = operations > 0;
    element('loadExample').disabled = operations > 0;
}

async function currentConverter() {
    if (!converterCache) converterCache = new GlyphConverter();
    const converter = converterCache;
    try {
        await converter.loaded;
    } catch (error) {
        if (converterCache === converter) converterCache = null;
        throw error;
    }
    return converter;
}

async function runConversion(action, failureMessage = '转换失败，请检查网络或文本文件后重试。') {
    const token = ++revision;
    operations++;
    updateFileControls();
    status('');
    try { await action(token); }
    catch (_) { if (token === revision) status(failureMessage, true); }
    finally { operations--; updateFileControls(); }
}

async function convertInput(token) {
    const displayedInput = element('inputText').value;
    const input = importedText?.displayed === displayedInput ? importedText.original : displayedInput;
    const converter = await currentConverter();
    if (token !== revision || displayedInput !== element('inputText').value) return;
    result = converter.review(input);
    reviewing = false;
    renderOutput();
}

element('convertText').addEventListener('click', () => runConversion(convertInput));
element('loadExample').addEventListener('click', () => {
    element('inputText').value = example.text;
    invalidate();
    const source = element('exampleSource');
    source.href = example.url;source.textContent = '示例出处：' + example.title;source.hidden = false;
    runConversion(convertInput);
});
element('inputText').addEventListener('input', invalidate);
document.querySelectorAll('input[name="mode"]').forEach(input => input.addEventListener('change', () => {
    try { window.localStorage.setItem(modeStorageKey, selected('mode')); } catch (_) { /* Conversion also works without storage. */ }
    revision++;
    reviewing = false;
    renderOutput();
    updateFileControls();
    status('');
}));
element('startReview').addEventListener('click',()=>{
    if(!result || !isReviewMode())return;
    reviewing=!reviewing;renderOutput();status('');
});

element('clearText').addEventListener('click', () => {
    element('inputText').value = '';
    invalidate();
    element('inputText').focus();
});

element('copyOutput').addEventListener('click', async () => {
    if (!result?.text()) return;
    const copied = result.text(), copiedDocument = result;
    const copiedMessage = () => result === copiedDocument && result.text() === copied ? status(exportMessage('已复制')) : undefined;
    try {
        await navigator.clipboard.writeText(copied);
        copiedMessage();
    } catch (_) {
        const temporary = window.document.createElement('textarea'), previous = window.document.activeElement;
        temporary.value = copied;
        temporary.className = 'visually-hidden';
        temporary.style.minHeight = '0';
        window.document.body.append(temporary);
        temporary.focus({preventScroll: true});
        temporary.select();
        let success = false;
        try { success = window.document.execCommand('copy'); }
        finally { temporary.remove(); previous?.focus({preventScroll: true}); }
        if (success) copiedMessage();
        else status('请选中结果文字后复制。', true);
    }
});

function exportMessage(message) {
    return isReviewMode() && result?.pending ? `${message}，剩余 ${result.pending} 处采用默认字形` : message;
}

function downloadResult() {
    if (!result?.text()) return;
    const url = URL.createObjectURL(new Blob([result.text()], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = outputFilename;
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    status(exportMessage('已下载'));
}
element('downloadOutput').addEventListener('click', downloadResult);
element('chooseFile').addEventListener('click', () => element('fileInput').click());
element('fileInput').addEventListener('change', () => {
    const file = element('fileInput').files[0];
    if (!file) return;
    runConversion(async token => {
        const text = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer());
        if (token !== revision) return;
        element('inputText').value = text;
        invalidate();
        importedText = { original: text, displayed: element('inputText').value };
        outputFilename = file.name.replace(/\.[^.]+$/, '') + '-转换结果.txt';
        status('已导入 ' + file.name);
        element('inputText').focus();
    }, '导入失败，请选择 UTF-8 编码的文本文件。').finally(() => { element('fileInput').value = ''; });
});
renderOutput();
updateFileControls();

function feedbackCharacter(input, optional = false) {
    let value = input.trim();
    if (!value && optional) return '';
    const number = /^(?:U\+|0x)[0-9a-f]{1,6}$/i.test(value) ? value.slice(2) : /^[0-9a-f]{4,6}$/i.test(value) ? value : null;
    if (number !== null) {
        const point = parseInt(number, 16);
        if (point > 0x10FFFF || point >= 0xD800 && point <= 0xDFFF) throw new Error('Unicode 码值无效');
        value = String.fromCodePoint(point);
    }
    const point = value.codePointAt(0);
    if ([...value].length !== 1 || point >= 0xD800 && point <= 0xDFFF || point < 0x20 || point >= 0x7F && point < 0xA0) throw new Error('请输入一个字形或有效的 Unicode 码');
    return value;
}
element('openFeedback').addEventListener('click', () => {
    element('feedbackForm').reset();element('feedbackStatus').textContent = '';element('feedbackCode').textContent = '';
    const input = element('inputText'), selectedText = input.value.slice(input.selectionStart, input.selectionEnd);
    if ([...selectedText].length === 1) element('feedbackCharacter').value = selectedText;
    element('feedbackDialog').showModal();element('feedbackCharacter').focus();
});
element('cancelFeedback').addEventListener('click', () => element('feedbackDialog').close());
element('feedbackCharacter').addEventListener('input', () => {
    try { const value = feedbackCharacter(element('feedbackCharacter').value);element('feedbackCode').textContent = `${value} · U+${value.codePointAt(0).toString(16).toUpperCase().padStart(4, '0')}`; }
    catch (_) { element('feedbackCode').textContent = ''; }
});
element('feedbackForm').addEventListener('submit', async event => {
    event.preventDefault();const button = element('submitFeedback');button.disabled = true;
    try {
        const body = {character: feedbackCharacter(element('feedbackCharacter').value), candidate: feedbackCharacter(element('feedbackCandidate').value, true), note: element('feedbackNote').value.trim()};
        if (!body.candidate && !body.note) throw new Error('请填写建议字形或说明');
        const tokenResponse = await fetch('feedback-token', {credentials: 'same-origin', cache: 'no-store'});
        if (!tokenResponse.ok) throw new Error('暂时无法提交，请稍后重试');
        const token = await tokenResponse.json(), headers = {'Content-Type': 'application/json', Accept: 'application/json'};
        if (token.token) headers[token.headerName] = token.token;
        const response = await fetch('feedback', {method: 'POST', headers, credentials: 'same-origin', body: JSON.stringify(body)});
        if (!response.ok) { const problem = await response.json().catch(() => ({}));throw new Error(problem.detail || '提交失败，请稍后重试'); }
        element('feedbackStatus').textContent = '已收到，等待核查。';button.disabled = true;
    } catch (error) { element('feedbackStatus').textContent = error.message;button.disabled = false; }
});
