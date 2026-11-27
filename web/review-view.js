// SPDX-License-Identifier: MIT
export class ReviewView {
    constructor(elements, onChange) {
        this.elements = elements;
        this.onChange = onChange;
        this.buttons = new Map();
        this.active = null;
        const {panel, previous, next, text, choices} = elements;
        previous.addEventListener('click', () => this.move(-1));
        next.addEventListener('click', () => this.move(1));
        text.addEventListener('click', event => {
            const button = event.target.closest?.('[data-review-position]');
            if (button) this.activate(Number(button.dataset.reviewPosition));
        });
        choices.addEventListener('click', event => {
            const button = event.target.closest?.('[data-candidate]');
            if (button) this.choose(button.dataset.candidate);
        });
        panel.addEventListener('keydown', event => {
            if (!event.target.closest?.('[data-review-position], [data-candidate]')) return;
            if (['ArrowRight', 'ArrowDown', 'ArrowLeft', 'ArrowUp'].includes(event.key)) {
                event.preventDefault();
                this.move(['ArrowRight', 'ArrowDown'].includes(event.key) ? 1 : -1);
            } else if (event.key === 'Escape') {
                event.preventDefault();
                elements.picker.hidden = true;
                this.buttons.get(this.active)?.setAttribute('aria-expanded', 'false');
                this.buttons.get(this.active)?.focus({preventScroll: true});
            } else if (!elements.picker.hidden && /^[1-9]$/.test(event.key) && !event.ctrlKey && !event.altKey && !event.metaKey) {
                const value = this.document?.parts[this.active]?.candidates[Number(event.key) - 1];
                if (value) { event.preventDefault(); this.choose(value); }
            }
        });
    }
    clear() {
        this.document = null;
        this.active = null;
        this.buttons.clear();
        this.elements.text.replaceChildren();
        this.elements.choices.replaceChildren();
        this.elements.picker.hidden = true;
        this.elements.panel.hidden = true;
    }
    show(document) {
        if (this.document !== document) {
            this.clear();
            this.document = document;
            const owner = this.elements.text.ownerDocument, fragment = owner.createDocumentFragment();
            document.parts.forEach((part, index) => {
                if (typeof part === 'string') fragment.append(owner.createTextNode(part));
                else {
                    const button = owner.createElement('button');
                    button.type = 'button';
                    button.className = 'review-word';
                    button.dataset.reviewPosition = String(index);
                    button.tabIndex = -1;
                    button.setAttribute('aria-controls', this.elements.picker.id);
                    this.buttons.set(index, button);
                    this.updateWord(index);
                    fragment.append(button);
                }
            });
            this.elements.text.append(fragment);
            this.activate(document.positions[0], false);
        }
        this.elements.panel.hidden = false;
        this.updateStatus();
    }
    updateWord(index) {
        const button = this.buttons.get(index), part = this.document.parts[index];
        button.textContent = this.document.value(index);
        button.classList.toggle('is-reviewed', this.document.reviewed(index));
        button.setAttribute('aria-label', `原字 ${part.source}，当前 ${button.textContent}，${part.candidates.length} 个候选${this.document.reviewed(index) ? '，已校对' : ''}`);
        button.setAttribute('aria-expanded', String(this.active === index && !this.elements.picker.hidden));
    }
    updateStatus() {
        const total = this.document.positions.length, pending = this.document.pending;
        this.elements.status.textContent = !total ? '没有多候选字' : pending ? `待校对 ${pending} / ${total} 处` : `已校对 ${total} 处`;
        this.elements.previous.disabled = this.elements.next.disabled = !total;
    }
    activate(index, moveFocus = true) {
        if (index == null || !this.buttons.has(index)) return;
        if (this.active != null && this.active !== index) {
            const previous = this.buttons.get(this.active);
            previous.classList.remove('is-active');
            previous.tabIndex = -1;
            previous.setAttribute('aria-expanded', 'false');
        }
        const activeButton = this.buttons.get(index);
        activeButton.classList.add('is-active');
        activeButton.tabIndex = 0;
        activeButton.setAttribute('aria-expanded', 'true');
        this.active = index;
        const part = this.document.parts[index], owner = this.elements.choices.ownerDocument;
        this.elements.source.textContent = `${part.source} →`;
        this.elements.choices.setAttribute('aria-label', `原字 ${part.source} 的候选`);
        this.elements.choices.replaceChildren(...part.candidates.map((value, position) => {
            const button = owner.createElement('button');
            button.type = 'button';
            button.dataset.candidate = value;
            button.textContent = value;
            button.title = `U+${value.codePointAt(0).toString(16).toUpperCase().padStart(4, '0')}${position < 9 ? ` · 数字键 ${position + 1}` : ''}`;
            button.setAttribute('aria-pressed', String(value === this.document.value(index)));
            return button;
        }));
        this.elements.picker.hidden = false;
        if (moveFocus) {
            const button = this.buttons.get(index);
            button.focus({preventScroll: true});
            button.scrollIntoView?.({block: 'nearest', inline: 'nearest'});
        }
    }
    move(step) {
        if (this.document) this.activate(this.document.next(this.active, step));
    }
    choose(value) {
        if (this.active == null || !this.document) return;
        const previous = this.active;
        this.document.choose(previous, value);
        this.updateWord(previous);
        this.updateStatus();
        this.onChange(this.document);
        const next = this.document.next(previous, 1, true);
        if (next != null) this.activate(next);
        else {
            this.elements.picker.hidden = true;
            const button = this.buttons.get(previous);
            button.setAttribute('aria-expanded', 'false');
            button.classList.remove('is-active');
            button.focus({preventScroll: true});
        }
    }
}
