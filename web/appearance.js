// SPDX-License-Identifier: MIT
(() => {
    const key = 'lemonslice-color-mode';
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    const order = ['light', 'dark', 'system'];
    const labels = {system: '跟随系统', light: '浅色', dark: '深色'};
    let mode = 'system';
    try {
        const saved = localStorage.getItem(key);
        if (['light', 'dark', 'system'].includes(saved)) mode = saved;
    } catch (_) { /* The current page can still change appearance. */ }
    function apply() {
        const scheme = mode === 'system' ? (media.matches ? 'dark' : 'light') : mode;
        document.documentElement.dataset.colorMode = mode;
        document.documentElement.dataset.colorScheme = scheme;
        document.documentElement.style.colorScheme = scheme;
        document.querySelector('meta[name="theme-color"]').content = scheme === 'dark' ? '#15171b' : '#ffffff';
        document.querySelectorAll('[data-color-mode-icon]').forEach(icon => { icon.toggleAttribute('hidden', icon.dataset.colorModeIcon !== mode); });
        const toggle = document.getElementById('appearanceToggle');
        if (toggle) {
            const next = order[(order.indexOf(mode) + 1) % order.length];
            toggle.title = `外观：${labels[mode]}${mode === 'system' ? `（当前${labels[scheme]}）` : ''}`;
            toggle.setAttribute('aria-label', `外观模式：${labels[mode]}，点击切换为${labels[next]}`);
        }
    }
    apply();
    media.addEventListener('change', apply);
    window.addEventListener('storage', event => {
        if (event.key === key || event.key === null) {
            try { mode = localStorage.getItem(key); } catch (_) { mode = 'system'; }
            if (!order.includes(mode)) mode = 'system';
            apply();
        }
    });
    document.addEventListener('DOMContentLoaded', () => {
        apply();
        document.getElementById('appearanceToggle').addEventListener('click', () => {
            mode = order[(order.indexOf(mode) + 1) % order.length];
            try { localStorage.setItem(key, mode); } catch (_) { /* Keep the in-memory setting. */ }
            apply();
        });
    });
})();
