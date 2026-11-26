// SPDX-License-Identifier: MIT
export const mappingStatus = row => row.source === 'disabled' ? 'disabled' : row.pending ? 'pending' : 'enabled';
export function batchAction(rows, selected) {
    if (!selected.length) return null;
    const chosen = rows.filter(row => selected.includes(row.character));
    if (chosen.length !== selected.length) return null;
    const states = new Set(chosen.map(mappingStatus));
    if (states.size !== 1) return null;
    return {enabled:'disable', pending:'approve', disabled:'enable'}[[...states][0]];
}
