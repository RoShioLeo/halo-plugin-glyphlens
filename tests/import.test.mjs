import test from 'node:test';
import assert from 'node:assert/strict';
import { dictionaryImportQuery } from '../ui/src/dictionary-import.js';

test('first import omits the version when Halo omits or serializes a null stateVersion',()=>{
    const responses=['{"fileHash":"same-file"}','{"fileHash":"same-file","stateVersion":null}'];
    for(const response of responses){
        const params=dictionaryImportQuery(JSON.parse(response));
        assert.equal(params.has('version'),false);
        assert.equal(params.get('fileHash'),'same-file');assert.equal(params.get('confirmed'),'true');
    }
});

test('existing imports keep the actual version including zero for conflict protection',()=>{
    for(const version of [0,12]){
        const params=dictionaryImportQuery({fileHash:'same-file',stateVersion:version});
        assert.equal(params.get('version'),String(version));
    }
});
