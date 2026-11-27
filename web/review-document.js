// SPDX-License-Identifier: MIT
/** Choices belong to occurrences, never to every occurrence of a source glyph. */
export class ReviewDocument {
    #choices = new Map();
    constructor(parts) {
        this.parts = Object.freeze(parts.map(part => typeof part === 'string' ? part :
            Object.freeze({source: part.source, candidates: Object.freeze([...part.candidates]), initial:part.initial??part.candidates[0], matched:part.matched===true})));
        this.positions = Object.freeze(this.parts.flatMap((part, index) => typeof part === 'string' ? [] : [index]));
    }
    get pending() { return this.positions.filter(index=>!this.confirmed(index)).length; }
    get matchedCount() { return this.positions.filter(index=>this.matched(index)).length; }
    get manual() { return this.#choices.size; }
    value(index) { return this.#choices.get(index) ?? this.parts[index]?.initial; }
    reviewed(index) { return this.#choices.has(index); }
    matched(index) { return this.parts[index]?.matched===true; }
    confirmed(index) { return this.reviewed(index); }
    choose(index, value) {
        const part = this.parts[index];
        if (!part?.candidates?.includes(value)) throw new TypeError('请选择当前字形的合法候选');
        this.#choices.set(index, value);
    }
    confirmRemaining() {
        let count=0;
        for(const index of this.positions)if(!this.confirmed(index)){this.choose(index,this.value(index));count++;}
        return count;
    }
    text() { return this.parts.map((part, index) => typeof part === 'string' ? part : this.value(index)).join(''); }
    next(index, step = 1, pendingOnly = false) {
        const positions=this.positions;
        const count = positions.length;
        if (!count) return null;
        let position = positions.indexOf(index);
        if (position < 0) position = step > 0 ? -1 : 0;
        for (let offset = 1; offset <= count; offset++) {
            const candidate = positions[(position + step * offset % count + count) % count];
            if (!pendingOnly || !this.confirmed(candidate)) return candidate;
        }
        return null;
    }
}
