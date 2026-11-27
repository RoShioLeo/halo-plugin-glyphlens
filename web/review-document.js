// SPDX-License-Identifier: MIT
/** Choices belong to occurrences, never to every occurrence of a source glyph. */
export class ReviewDocument {
    #choices = new Map();
    constructor(parts) {
        this.parts = Object.freeze(parts.map(part => typeof part === 'string' ? part :
            Object.freeze({source: part.source, candidates: Object.freeze([...part.candidates])})));
        this.positions = Object.freeze(this.parts.flatMap((part, index) => typeof part === 'string' ? [] : [index]));
    }
    get pending() { return this.positions.length - this.#choices.size; }
    value(index) { return this.#choices.get(index) ?? this.parts[index]?.candidates?.[0]; }
    reviewed(index) { return this.#choices.has(index); }
    choose(index, value) {
        const part = this.parts[index];
        if (!part?.candidates?.includes(value)) throw new TypeError('请选择当前字形的合法候选');
        this.#choices.set(index, value);
    }
    text() { return this.parts.map((part, index) => typeof part === 'string' ? part : this.value(index)).join(''); }
    next(index, step = 1, pendingOnly = false) {
        const count = this.positions.length;
        if (!count) return null;
        let position = this.positions.indexOf(index);
        if (position < 0) position = step > 0 ? -1 : 0;
        for (let offset = 1; offset <= count; offset++) {
            const candidate = this.positions[(position + step * offset % count + count) % count];
            if (!pendingOnly || !this.reviewed(candidate)) return candidate;
        }
        return null;
    }
}
