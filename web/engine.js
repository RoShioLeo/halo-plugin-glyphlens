// SPDX-License-Identifier: MIT
import { DisambiguationIndex } from './lexicon.js';

const directions = new Set(['v2s', 'v2t']);
const modes = new Set(['one2many', 'one2one']);
const graphemes = new Intl.Segmenter('und', {granularity: 'grapheme'});
const scalar = value => typeof value === 'string' && [...value].length === 1 &&
    !(value.codePointAt(0) >= 0xD800 && value.codePointAt(0) <= 0xDFFF);
const json = url => fetch(url, {cache: 'no-store'}).then(response => {
    if (!response.ok) throw new Error('资料加载失败');return response.json();
});

/** Exactly one lookup per source character. Replacements never re-enter the input. */
export class GlyphConverter {
    constructor(direction = 'v2s', dictionary, lexicon = []) {
        if (!directions.has(direction)) throw new TypeError('未知转换方向');
        this.direction = direction;
        this.mode = 'one2many';
        this.main = new Uint32Array(0xA000 - 0x3400);
        this.extension = new Uint32Array(0x33480 - 0x20000);
        this.other = new Map();
        this.pool = [null];
        this.lexicon = new DisambiguationIndex();
        this.loaded = dictionary ? Promise.resolve(this.install(dictionary, lexicon)) :
            Promise.all([json(`dictionaries/${direction}.json`), json(`lexicon.json?direction=${direction}`)])
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
    convert(input) {
        if (typeof input !== 'string') throw new TypeError('请输入文本');
        const key = this.mode === 'one2many' ? 'many' : 'one';
        const output = [];
        // The catalog only defines single scalars. Preserve entire unknown glyph
        // sequences (IVS, combining marks, emoji) instead of changing their base.
        const characters = Array.from(graphemes.segment(input), part => part.segment);
        const entries = characters.map(character => scalar(character) ? this.entry(character) : undefined);
        if (!this.lexicon.rules.length) {
            for (let position = 0; position < characters.length; position++) {
                output.push(entries[position]?.[key] ?? characters[position]);
            }
        } else {
            const decisions = this.lexicon.resolve(characters, position => entries[position]?.candidates);
            for (let position = 0; position < characters.length; position++) {
                const character = characters[position], entry = entries[position], selected = decisions.get(position)?.values;
                if (selected?.size === 1) output.push(selected.values().next().value);
                else if (selected?.size > 1 && key === 'many') {
                    const candidates = entry.candidates.filter(candidate => selected.has(candidate));
                    output.push(`【${candidates.join('|')}】`);
                } else output.push(entry?.[key] ?? character);
            }
        }
        return output.join('');
    }
}
