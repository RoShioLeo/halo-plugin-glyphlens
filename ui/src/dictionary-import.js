// SPDX-License-Identifier: MIT
export function dictionaryImportQuery(preview) {
    const params=new URLSearchParams({fileHash:preview.fileHash,confirmed:'true'});
    if(preview.stateVersion!=null)params.set('version',String(preview.stateVersion));
    return params;
}
