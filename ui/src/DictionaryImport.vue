<!-- SPDX-License-Identifier: MIT -->
<script setup>
import { computed,ref } from 'vue';
import { Toast,VButton,VModal,VLoading } from '@halo-dev/components';
import { request } from './api';
import { unicodeCodes } from './unicode';
import { dictionaryImportQuery } from './dictionary-import.js';
const props=defineProps({visible:Boolean});
const emit=defineEmits(['close','saved']);
const file=ref(null),preview=ref(null),busy=ref(false),error=ref(''),confirmed=ref(false),page=ref(1);
const rows=computed(()=>preview.value?.changes.slice((page.value-1)*100,page.value*100)||[]);
const count=value=>Number(value||0).toLocaleString('zh-CN');
let sequence=0;
async function inspect(event) {
    const selected=event.target.files?.[0];if(!selected)return;file.value=selected;event.target.value='';await inspectFile();
}
async function inspectFile() {
    const selected=file.value;preview.value=null;error.value='';confirmed.value=false;page.value=1;
    const ticket=++sequence;if(!selected)return;
    if(selected.size>8*1024*1024){error.value='字库文件不能超过 8 MiB';return;}
    busy.value=true;
    try {const data=await request('/dictionary/import/preview',{method:'POST',headers:{'Content-Type':'application/octet-stream'},body:selected});if(ticket===sequence)preview.value=data;}
    catch(failure){if(ticket===sequence)error.value=failure.message;}
    finally{if(ticket===sequence)busy.value=false;}
}
async function save() {
    if(!file.value || !preview.value || !confirmed.value || busy.value)return;
    busy.value=true;error.value='';
    const params=dictionaryImportQuery(preview.value);
    try {
        await request('/dictionary/import?'+params,{method:'PUT',headers:{'Content-Type':'application/octet-stream'},body:file.value});
        Toast.success('字库已导入');emit('saved');busy.value=false;close();
    } catch(failure){error.value=failure.message;preview.value=null;confirmed.value=false;}
    finally{busy.value=false;}
}
function close(){if(busy.value)return;sequence++;file.value=null;preview.value=null;error.value='';confirmed.value=false;emit('close');}
</script>
<template>
    <VModal :visible="props.visible" title="导入字库" :width="960" @close="close">
        <div class="import-body">
            <label class="file-label">选择字库文件 <input type="file" accept=".zip,.json,application/json,application/zip" :disabled="busy" @change="inspect"></label>
            <p v-if="file">已选：{{file.name}}</p>
            <p class="muted">支持默认字库 ZIP 包，以及“导出字库”生成的 JSON 或自定义映射 JSON。最大 8 MiB。</p>
            <p><a href="https://github.com/RoShioLeo/halo-plugin-glyphlens/blob/main/docs/DICTIONARY-IMPORT.md" target="_blank" rel="noopener noreferrer">自定义字库格式说明</a></p>
            <p v-if="error" role="alert" class="import-error">{{error}}</p>
            <VButton v-if="error && file && !preview" :loading="busy" :disabled="busy" @click="inspectFile">重新预览</VButton>
            <VLoading v-if="busy" />
            <template v-if="preview">
                <h3>{{preview.type==='default'?'更新默认字库':'合并映射'}}</h3>
                <p v-if="preview.type==='default'">默认字库 {{preview.currentDictionaryVersion}} → {{preview.incomingDictionaryVersion}}。站点手动修正 {{count(preview.manualKept)}} 项及站点词典设置保留。</p>
                <p v-else>仅合并文件中指定的映射；未提及的原字保持现状。默认字库仍为 {{preview.currentDictionaryVersion}}。<span v-if="preview.incomingDictionaryVersion!==preview.currentDictionaryVersion">文件基于 {{preview.incomingDictionaryVersion}} 导出，请核对差异。</span></p>
                <p>新增 {{count(preview.newCount)}} 项 · 更新 {{count(preview.changedCount)}} 项 · {{preview.type==='default'?'移除默认':'停用'}} {{count(preview.disabledCount)}} 项 · 未变 {{count(preview.unchangedCount)}} 项</p>
                <p v-if="preview.type==='mappings'" class="muted">“已有修正”项会按文件内容更新；备注未提供时保留当前备注。候选顺序决定默认转换结果。</p>
                <div v-if="preview.changes.length" class="import-table"><table>
                    <thead><tr><th>原字</th><th>当前</th><th>文件候选</th><th>导入后</th><th>处理</th></tr></thead>
                    <tbody><tr v-for="row in rows" :key="row.character"><td><span class="character">{{row.character}}</span><small>{{unicodeCodes(row.character)}}</small></td><td>{{row.before.join(' / ')}}</td><td>{{row.incoming.join(' / ')}}</td><td>{{row.after.join(' / ')}}</td><td>{{row.action}}<small v-if="row.conflict">已有修正</small></td></tr></tbody>
                </table></div>
                <p v-else-if="preview.type==='default' && preview.currentDictionaryVersion!==preview.incomingDictionaryVersion">映射候选未变化，将更新字库版本、词典或来源资料。</p>
                <p v-else>文件与当前字库一致，无需更新。</p>
                <div v-if="preview.changes.length>100" class="import-pages"><span>第 {{page}} / {{Math.ceil(preview.changes.length/100)}} 页</span><VButton :disabled="page<=1 || busy" @click="page--">上一页</VButton><VButton :disabled="page*100>=preview.changes.length || busy" @click="page++">下一页</VButton></div>
                <label class="import-confirm"><input v-model="confirmed" type="checkbox" :disabled="busy">已核对预览中的字形关系、候选顺序及处理方式</label>
            </template>
        </div>
        <template #footer><div class="import-actions"><VButton :disabled="busy" @click="close">取消</VButton><VButton type="primary" :loading="busy" :disabled="busy || !preview || !confirmed" @click="save">确认导入</VButton></div></template>
    </VModal>
</template>
<style scoped>
.import-body{font-size: var(--ls-control-font-size);line-height:1.7}.file-label{display:grid;gap: 8px}.import-body p{margin: 16px 0}.muted{opacity:.65}a{text-decoration:underline}.import-error{color:var(--ls-error)}.import-body h3{font-size:14px;font-weight:600;margin-top: 16px}.import-table{max-height:45vh;overflow:auto}table{border-collapse:collapse;width:100%}th,td{text-align:left;padding: 8px 16px;border-bottom:1px solid var(--ls-faint);vertical-align:top}th{font-weight:500;white-space:nowrap}td{overflow-wrap:anywhere}small{display:block;opacity:.6;font-size: var(--ls-label-font-size)}.character{font-size:22px}.import-confirm{display:flex;align-items:center;gap: 8px;margin: 16px 0 4px}.import-pages,.import-actions{display:flex;align-items:center;justify-content:flex-end;gap: 8px;margin-top: 16px}
</style>
