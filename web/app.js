// SPDX-License-Identifier: MIT
import { GlyphConverter } from './engine.js';
const converters = new Map();
const element = id => document.getElementById(id);
const status = (message, error = false) => {
    element('conversionStatus').textContent = message;
    element('conversionStatus').dataset.error = String(error);
};

fetch('access', {credentials: 'same-origin', cache: 'no-store'})
    .then(response => response.ok ? response.json() : {canManage: false})
    .then(access => { element('dictionaryAdmin').hidden = access.canManage !== true; })
    .catch(() => { element('dictionaryAdmin').hidden = true; });
window.addEventListener('focus', () => converters.clear());

async function currentConverter() {
    const option = element('optionSelect').value;
    if (!converters.has(option)) {
        const converter = new GlyphConverter(option);
        converters.set(option, converter);
    }
    const converter = converters.get(option);
    try {
        await converter.loaded;
    } catch (error) {
        converters.delete(option);
        throw error;
    }
    converter.setMode(element('modeSelect').value);
    return converter;
}

async function runConversion(button, action) {
    button.disabled = true;
    status('');
    try { await action(); }
    catch (_) { status('转换失败，请检查网络或文本文件后重试。', true); }
    finally { button.disabled = false; }
}

element('convertText').addEventListener('click', event => runConversion(event.currentTarget, async () => {
    const converter = await currentConverter();
    element('outputText').value = converter.convert(element('inputText').value);
    element('copyOutput').disabled = !element('outputText').value;
}));

element('clearText').addEventListener('click', () => {
    element('inputText').value = '';
    element('outputText').value = '';
    element('copyOutput').disabled = true;
    status('');
    element('inputText').focus();
});

element('copyOutput').addEventListener('click', async () => {
    if (!element('outputText').value) return;
    try {
        await navigator.clipboard.writeText(element('outputText').value);
        status('已复制');
    } catch (_) {
        element('outputText').focus();
        element('outputText').select();
        status(document.execCommand('copy') ? '已复制' : '请复制已选中的文字。');
    }
});

element('chooseFile').addEventListener('click', () => element('fileInput').click());
element('fileInput').addEventListener('change', () => {
    const file = element('fileInput').files[0];
    element('convertFile').disabled = !file;
    status(file ? file.name : '');
});
element('convertFile').addEventListener('click', event => runConversion(event.currentTarget, async () => {
    const file = element('fileInput').files[0];
    if (!file) return;
    const converter = await currentConverter();
    const text = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer());
    const converted = converter.convert(text);
    const url = URL.createObjectURL(new Blob([converted], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = file.name.replace(/\.[^.]+$/, '') + '-转换结果.txt';
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    status('文件已转换');
}));

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
    const input = element('inputText'), selected = input.value.slice(input.selectionStart, input.selectionEnd);
    if ([...selected].length === 1) element('feedbackCharacter').value = selected;
    element('feedbackDirection').value = element('optionSelect').value;
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
        const body = {character: feedbackCharacter(element('feedbackCharacter').value), candidate: feedbackCharacter(element('feedbackCandidate').value, true), direction: element('feedbackDirection').value, note: element('feedbackNote').value.trim()};
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
