// SPDX-License-Identifier: MIT
export function unicodeInput(input: string): string {
    const value = input.trim();
    const prefixed = /^(?:U\+|0x)([0-9a-f]{1,6})$/i.exec(value);
    const hex = prefixed?.[1] ?? (/^[0-9a-f]{4,6}$/i.test(value) ? value : null);
    if (hex === null) return value;
    const code = Number.parseInt(hex, 16);
    if (code > 0x10ffff || (code >= 0xd800 && code <= 0xdfff)) throw new Error('请输入有效的 Unicode 码值');
    return String.fromCodePoint(code);
}

export function unicodeCodes(value: string): string {
    return Array.from(value, character => 'U+' + character.codePointAt(0)!.toString(16).toUpperCase().padStart(4, '0')).join(' ');
}
