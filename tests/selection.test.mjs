import test from 'node:test';
import assert from 'node:assert/strict';
import { batchAction } from '../ui/src/selection.js';
const rows=[{character:'甲',source:'base',pending:false},{character:'乙',source:'custom',pending:false},{character:'丙',source:'base',pending:true},{character:'丁',source:'disabled',pending:false}];
test('batch actions follow the uniform selected status and reject mixed or stale selections',()=>{
    assert.equal(batchAction(rows,['甲','乙']),'disable');
    assert.equal(batchAction(rows,['丙']),'approve');
    assert.equal(batchAction(rows,['丁']),'enable');
    for(const selected of [[],['甲','丙'],['甲','丁'],['丙','丁'],['甲','丙','丁'],['甲','戊']])assert.equal(batchAction(rows,selected),null);
});
