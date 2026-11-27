// SPDX-License-Identifier: MIT
import { GlyphConverter } from './engine.js';
import { ReviewView } from './review-view.js';
let converterCache = null;
const element = id => document.getElementById(id);
const selected = name => [...document.getElementsByName(name)].find(input => input.checked).value;
const isReviewMode = () => selected('mode') === 'one2many';
let result = null, reviewing = false, revision = 0, operations = 0, outputFilename = '字镜-转换结果.txt';
const status = (message, error = false) => {
    element('conversionStatus').textContent = message;
    element('conversionStatus').dataset.error = String(error);
};

fetch('access', {credentials: 'same-origin', cache: 'no-store'})
    .then(response => response.ok ? response.json() : {canManage: false})
    .then(access => { element('dictionaryAdmin').hidden = access.canManage !== true; })
    .catch(() => { element('dictionaryAdmin').hidden = true; });
window.addEventListener('focus', () => { converterCache = null; });

const review = new ReviewView({
    panel: element('reviewPanel'), text: element('reviewText'), status: element('reviewStatus'),
    previous: element('previousCandidate'), next: element('nextCandidate'), picker: element('candidatePicker'),
    confirmRemaining: element('confirmRemaining'),
    source: element('candidateSource'), choices: element('candidateChoices'),
}, document => { if (result === document) { renderOutput(); status(''); } });

function renderOutput() {
    const text = result?.text() ?? '';
    const active = !!result && isReviewMode() && reviewing;
    element('outputText').value = text;
    element('copyOutput').disabled = element('downloadOutput').disabled = !text;
    element('outputText').hidden = active;
    element('outputLabel').textContent = active ? '校对结果' : '转换结果';
    element('startReview').hidden = !isReviewMode();
    element('startReview').disabled = !result;
    element('startReview').textContent = active ? '结束校对' : '开始校对';
    if (active) review.show(result);
    else element('reviewPanel').hidden = true;
}

function invalidate() {
    revision++;
    result = null;
    reviewing = false;
    review.clear();
    renderOutput();
    status('');
}

function updateFileControls() {
    element('convertText').disabled = operations > 0;
    element('convertFile').disabled = operations > 0 || !element('fileInput').files[0];
    element('convertFile').textContent = isReviewMode() ? '转换文件' : '转换并下载';
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

async function runConversion(action) {
    const token = ++revision;
    operations++;
    updateFileControls();
    status('');
    try { await action(token); }
    catch (_) { if (token === revision) status('转换失败，请检查网络或文本文件后重试。', true); }
    finally { operations--; updateFileControls(); }
}

async function convertInput(token) {
    const input = element('inputText').value;
    const converter = await currentConverter();
    if (token !== revision || input !== element('inputText').value) return;
    result = converter.review(input);
    reviewing = false;
    outputFilename = '字镜-转换结果.txt';
    renderOutput();
}

element('convertText').addEventListener('click', () => runConversion(convertInput));
element('inputText').addEventListener('input', invalidate);
document.querySelectorAll('input[name="mode"]').forEach(input => input.addEventListener('change', () => {
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
    invalidate();
    updateFileControls();
    status(file ? file.name : '');
});
element('convertFile').addEventListener('click', () => runConversion(async token => {
    const file = element('fileInput').files[0];
    if (!file) return;
    const [converter, bytes] = await Promise.all([currentConverter(), file.arrayBuffer()]);
    if (token !== revision || file !== element('fileInput').files[0]) return;
    const text = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
    element('inputText').value = text;
    result = converter.review(text);
    reviewing = false;
    outputFilename = file.name.replace(/\.[^.]+$/, '') + '-转换结果.txt';
    renderOutput();
    if (!isReviewMode()) downloadResult();
    else status('文件已载入');
}));
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
