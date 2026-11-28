// SPDX-License-Identifier: MIT
// Shared design data generator. Distributed with each independently built project.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath, pathToFileURL } from 'node:url';

const aliases = {
  theme: {
    '--ls-interface-ink': '--ls-ink', '--ls-interface-muted': '--ls-muted',
    '--ls-interface-line': '--ls-faint', '--ls-input-border': '--ls-control-border',
    '--ls-accent': '--ls-brand-accent', '--ls-comment-button-ink': '--ls-primary-ink',
    '--halo-search-widget-primary-color': '--ls-brand-accent',
  },
  kitchen: {
    '--font-sans': '--ls-font-sans', '--ls-canvas': '--ls-paper', '--ls-field': '--ls-paper',
    '--ls-rail': '--ls-surface', '--ls-soft': '--ls-surface', '--ls-hover': '--ls-option-hover',
    '--ls-line': '--ls-faint', '--ls-input': '--ls-control-border', '--k-subtle': '--ls-muted',
    '--ls-accent': '--ls-brand-accent', '--ls-accent-strong': '--ls-brand-accent',
    '--ls-accent-soft': '--ls-selection', '--ls-action': '--ls-primary-bg',
    '--ls-action-ink': '--ls-primary-ink', '--ls-action-hover': '--ls-primary-hover',
    '--ls-dishes-surface': '--ls-paper', '--ls-dishes-soft': '--ls-surface',
    '--ls-dishes-line': '--ls-faint', '--ls-dishes-ink': '--ls-ink',
    '--ls-dishes-muted': '--ls-muted', '--ls-dishes-gold': '--ls-brand-accent',
    '--ls-dishes-highlight': '--ls-brand-accent', '--ls-dishes-gold-soft': '--ls-selection',
    '--ls-dishes-danger': '--ls-error',
  },
  glyphlens: {
    '--font-sans': '--ls-font-sans', '--paper': '--ls-paper', '--surface': '--ls-surface',
    '--ink': '--ls-ink', '--muted': '--ls-muted', '--line': '--ls-faint',
    '--input-border': '--ls-control-border', '--accent': '--ls-brand-accent',
    '--primary': '--ls-primary-bg', '--primary-ink': '--ls-primary-ink',
    '--primary-hover': '--ls-primary-hover', '--error': '--ls-error',
    '--selection': '--ls-selection', '--shadow': '--ls-popover-shadow',
  },
};
const outputs = {
  theme: ['src/css/design-tokens.css', 'ui-plugin/src/design-tokens.css'],
  kitchen: ['web-frontend/src/styles/design-tokens.css', 'ui/src/design-tokens.css'],
  glyphlens: ['web/design-tokens.css', 'ui/src/design-tokens.css'],
};

export function validateTokens(data) {
  if (data.schemaVersion !== 1 || !data.version || !Number.isInteger(data.mobileBreakpoint)) throw new Error('Invalid design data header.');
  const light = Object.keys(data.palette?.light || {}).sort();
  const dark = Object.keys(data.palette?.dark || {}).sort();
  if (!light.length || JSON.stringify(light) !== JSON.stringify(dark)) throw new Error('Light and dark palettes must define the same roles.');
  for (const [name, value] of Object.entries({...data.common, ...data.mobile, ...data.palette.light, ...data.palette.dark})) {
    if (!/^--[a-z0-9-]+$/.test(name) || typeof value !== 'string' || /[;{}\n\r]/.test(value)) throw new Error(`Invalid design role: ${name}`);
  }
  for (const name of light) {
    if (name in data.common || name in data.mobile) throw new Error(`Color role also changes geometry: ${name}`);
  }
  for (const name of Object.keys(data.mobile)) {
    if (!(name in data.common)) throw new Error(`Mobile role lacks a default: ${name}`);
  }
}

export function renderTokens(data, project, {native = false} = {}) {
  validateTokens(data);
  if (!(project in aliases)) throw new Error(`Unknown project: ${project}`);
  const digest = crypto.createHash('sha256').update(JSON.stringify(data)).digest('hex');
  const block = (selector, values, scheme) => `${selector} {\n${scheme ? `  color-scheme: ${scheme};\n` : ''}${Object.entries(values).map(([key, value]) => `  ${key}: ${value};`).join('\n')}\n}\n`;
  const mapped = Object.fromEntries(Object.entries(aliases[project]).map(([key, value]) => [key, `var(${value})`]));
  let css = `/* Generated from ${data.source}; design ${data.version}; sha256 ${digest}. Do not edit. */\n`;
  css += block(':root', {...data.common, ...data.palette.light, ...mapped}, native ? undefined : 'light');
  if (native) {
    // Halo owns its appearance preference. Never apply OS fallback to the native host.
    css += block('html.dark, html[data-theme="dark"], html[data-color-scheme="dark"]', data.palette.dark);
  } else {
    css += block('html[data-color-scheme="dark"]', data.palette.dark, 'dark');
    css += `@media (prefers-color-scheme: dark) {\n${block('html:not([data-color-scheme])', data.palette.dark, 'dark').split('\n').filter(Boolean).map(line => `  ${line}`).join('\n')}\n}\n`;
  }
  css += `@media (max-width: ${data.mobileBreakpoint}px) {\n${block(':root', data.mobile).split('\n').filter(Boolean).map(line => `  ${line}`).join('\n')}\n}\n`;
  return css;
}

export function syncProject(root, project, data, {check = false} = {}) {
  const stale = [];
  for (const file of outputs[project] || []) {
    const content = renderTokens(data, project, {native: file.startsWith('ui-plugin/') || file.startsWith('ui/')});
    const destination = path.join(root, file);
    const current = fs.existsSync(destination) ? fs.readFileSync(destination, 'utf8').replace(/\r\n/g, '\n') : null;
    if (current === content) continue;
    stale.push(file);
    if (!check) { fs.mkdirSync(path.dirname(destination), {recursive:true}); fs.writeFileSync(destination, content); }
  }
  return stale;
}

function main() {
  const args = process.argv.slice(2);
  const selected = args.indexOf('--project');
  const project = selected >= 0 ? args[selected + 1] : 'theme';
  if (!(project in outputs)) throw new Error(`Unknown project: ${project}`);
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  const data = JSON.parse(fs.readFileSync(path.join(root, 'design/tokens.json'), 'utf8'));
  const check = args.includes('--check');
  const stale = syncProject(root, project, data, {check});
  if (check && stale.length) throw new Error(`Generated design files are stale: ${stale.join(', ')}. Run the workspace design sync.`);
  console.log(`${project}: design ${data.version} ${check ? 'verified' : 'generated'} (${stale.length} files ${check ? 'outdated' : 'updated'}).`);
}
if (process.argv[1] && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url) {
  try { main(); } catch (error) { console.error(error.message); process.exitCode = 1; }
}
