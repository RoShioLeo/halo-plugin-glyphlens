// SPDX-License-Identifier: MIT
/** An approved phrase may only choose existing character candidates. */
export class DisambiguationIndex {
    constructor(rules = []) {
        this.nodes = [{next: new Map(), fail: 0, outputs: []}];
        this.rules = [];
        for (const rule of rules) {
            const phrase = Array.from(rule.phrase || '').map(c => c.codePointAt(0));
            if (phrase.length < 2 || phrase.length > 64 || !Array.isArray(rule.choices) || !rule.choices.length) continue;
            const index = this.rules.push({...rule, points: phrase}) - 1;
            let state = 0;
            for (const point of phrase) {
                if (!this.nodes[state].next.has(point)) {
                    this.nodes[state].next.set(point, this.nodes.length);
                    this.nodes.push({next: new Map(), fail: 0, outputs: []});
                }
                state = this.nodes[state].next.get(point);
            }
            this.nodes[state].outputs.push(index);
        }
        const queue = [...this.nodes[0].next.values()];
        for (let offset = 0; offset < queue.length; offset++) {
            const parent = queue[offset];
            for (const [point, child] of this.nodes[parent].next) {
                let failure = this.nodes[parent].fail;
                while (failure && !this.nodes[failure].next.has(point)) failure = this.nodes[failure].fail;
                this.nodes[child].fail = this.nodes[failure].next.get(point) || 0;
                this.nodes[child].outputs.push(...this.nodes[this.nodes[child].fail].outputs);
                queue.push(child);
            }
        }
    }
    resolve(characters, candidatesAt) {
        const selected = new Map();
        let state = 0;
        for (let offset = 0; offset < characters.length; offset++) {
            const point = characters[offset].codePointAt(0);
            while (state && !this.nodes[state].next.has(point)) state = this.nodes[state].fail;
            state = this.nodes[state].next.get(point) || 0;
            for (const index of this.nodes[state].outputs) {
                const rule = this.rules[index], start = offset - rule.points.length + 1;
                for (const choice of rule.choices) {
                    const position = start + choice.offset;
                    if (position < start || position > offset || characters[position] !== choice.source) continue;
                    const candidates = candidatesAt(position);
                    if (!candidates || candidates.length < 2 || !candidates.includes(choice.target)) continue;
                    const previous = selected.get(position);
                    if (!previous || rule.points.length > previous.length) {
                        selected.set(position, {length: rule.points.length, values: new Set([choice.target])});
                    } else if (rule.points.length === previous.length) previous.values.add(choice.target);
                }
            }
        }
        return selected;
    }
}
