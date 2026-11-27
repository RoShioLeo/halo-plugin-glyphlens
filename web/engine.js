// SPDX-License-Identifier: MIT
import { DisambiguationIndex } from './lexicon.js';
import { ReviewDocument } from './review-document.js';

const modes = new Set(['one2many', 'one2one']);
const graphemes = new Intl.Segmenter('und', {granularity: 'grapheme'});
const scalar = value => typeof value === 'string' && [...value].length === 1 &&
    !(value.codePointAt(0) >= 0xD800 && value.codePointAt(0) <= 0xDFFF);
const json = url => fetch(url, {cache: 'no-store'}).then(response => {
    if (!response.ok) throw new Error('资料加载失败');return response.json();
});

/** Exactly one lookup per source character. Replacements never re-enter the input. */
export class GlyphConverter {
    constructor(dictionary, lexicon = []) {
        this.mode = 'one2one';
        this.main = new Uint32Array(0xA000 - 0x3400);
        this.extension = new Uint32Array(0x33480 - 0x20000);
        this.other = new Map();
        this.pool = [null];
        this.lexicon = new DisambiguationIndex();
        this.loaded = dictionary ? Promise.resolve(this.install(dictionary, lexicon)) :
            Promise.all([json('dictionary.json'), json('lexicon.json')])
                .then(([entries, rules]) => this.install(entries, rules));
    }
    install(dictionary, rules = []) {
        if (!dictionary || Array.isArray(dictionary) || typeof dictionary !== 'object') throw new TypeError('字库格式错误');
        this.main.fill(0);this.extension.fill(0);this.other.clear();this.pool = [null];
        for (const [source, values] of Object.entries(dictionary)) {
            if (!scalar(source) || !Array.isArray(values) || !values.length || values.some(value => !scalar(value))) {
                throw new TypeError('字形关系必须由单字组成');
            }
            const candidates = Object.freeze([...new Set(values)]), point = source.codePointAt(0), id = this.pool.length;
            this.pool.push(Object.freeze({candidates, one: candidates[0], many: candidates.length > 1 ? `【${candidates.join('|')}】` : candidates[0]}));
            if (point >= 0x3400 && point < 0xA000) this.main[point - 0x3400] = id;
            else if (point >= 0x20000 && point < 0x33480) this.extension[point - 0x20000] = id;
            else this.other.set(point, id);
        }
        this.lexicon = new DisambiguationIndex(rules);
        return this;
    }
    entry(character) {
        const point = character.codePointAt(0);
        const id = point >= 0x3400 && point < 0xA000 ? this.main[point - 0x3400] :
            point >= 0x20000 && point < 0x33480 ? this.extension[point - 0x20000] : this.other.get(point);
        return this.pool[id];
    }
    setMode(mode) {
        if (!modes.has(mode)) throw new TypeError('未知候选模式');this.mode = mode;return this;
    }
    plan(input) {
        if (typeof input !== 'string') throw new TypeError('请输入文本');
        // The catalog only defines single scalars. Preserve entire unknown glyph
        // sequences (IVS, combining marks, emoji) instead of changing their base.
        const characters = Array.from(graphemes.segment(input), part => part.segment);
        const entries = characters.map(character => scalar(character) ? this.entry(character) : undefined);
        const decisions = this.lexicon.rules.length ?
            this.lexicon.resolve(characters, position => entries[position]?.candidates) : new Map();
        return {characters, entries, decisions};
    }
    candidates(plan, position) {
        const values = plan.entries[position]?.candidates ?? [plan.characters[position]];
        const selected = plan.decisions.get(position)?.values;
        return selected?.size ? values.filter(value => selected.has(value)) : values;
    }
    convert(input) {
        const plan = this.plan(input), output = [], key = this.mode === 'one2many' ? 'many' : 'one';
        for (let position = 0; position < plan.characters.length; position++) {
            if (!plan.decisions.has(position)) output.push(plan.entries[position]?.[key] ?? plan.characters[position]);
            else {
                const values = this.candidates(plan, position);
                output.push(key === 'many' && values.length > 1 ? `【${values.join('|')}】` : values[0]);
            }
        }
        return output.join('');
    }
    review(input) {
        const plan = this.plan(input), parts = [];
        let text = [];
        for (let position = 0; position < plan.characters.length; position++) {
            const candidates = this.candidates(plan, position);
            if (candidates.length === 1) text.push(candidates[0]);
            else {
                if (text.length) { parts.push(text.join('')); text = []; }
                parts.push({source: plan.characters[position], candidates});
            }
        }
        if (text.length) parts.push(text.join(''));
        return new ReviewDocument(parts);
    }
}
