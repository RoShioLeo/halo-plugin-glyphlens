<!-- SPDX-License-Identifier: MIT -->
<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { Dialog, Toast, VButton, VCard, VEmpty, VLoading, VModal, VPageHeader } from '@halo-dev/components';
import { unicodeInput, unicodeCodes } from './unicode';
import { batchAction, mappingStatus } from './selection';
import { request } from './api';
import LexiconView from './LexiconView.vue';
import FeedbackView from './FeedbackView.vue';
import SourcesView from './SourcesView.vue';

const section=ref('glyphs'),query=ref(''),source=ref('all'),page=ref(1);
const result=ref({items:[],total:0,version:null,effectiveCount:0,changedCount:0});
const loading=ref(false),saving=ref(false),selected=ref([]),error=ref('');
const editorVisible=ref(false),historyVisible=ref(false),history=ref([]),sourceVisible=ref(false),sourceRecords=ref([]);
const selectable=computed(()=>result.value.items);
const selectedAction=computed(()=>batchAction(result.value.items,selected.value));
const actionLabels={disable:'停用',enable:'启用',approve:'审核'};
const batchReview=reactive({visible:false,rows:[],version:null,confirmed:false});
const allSelected=computed(()=>selectable.value.length>0 && selectable.value.every(row=>selected.value.includes(row.character)));
const edit=reactive({character:'',candidates:'',note:'',existing:false,version:null,pending:false,confirmed:false,reasons:[],links:[]});
const candidatePreview=computed(()=>edit.candidates.split('\n').filter(value=>value.trim()).map(value=>{
    try { const character=unicodeInput(value);return {character,code:unicodeCodes(character)}; }
    catch(failure) { return {error:failure.message}; }
}));
const characterPreview=computed(()=>{try{return unicodeInput(edit.character);}catch(_){return '';}});
let sequence=0;
async function load() {
    const ticket=++sequence;loading.value=true;error.value='';
    try {
        const params=new URLSearchParams({query:unicodeInput(query.value),source:source.value,page:String(page.value),size:'50'});
        const data=await request('/dictionary?'+params);
        if(ticket===sequence) {result.value=data;selected.value=[];}
    } catch(failure) {if(ticket===sequence)error.value=failure.message;}
    finally {if(ticket===sequence)loading.value=false;}
}
watch(source,()=>{page.value=1;editorVisible.value=false;load();});
onMounted(load);
function search(){page.value=1;load();}
function toggleAll(){selected.value=allSelected.value?[]:selectable.value.map(row=>row.character);}
async function saveBatch(action,characters,version,confirmed=false) {
    saving.value=true;
    try {
        await request('/dictionary/batch',{method:'PUT',body:JSON.stringify({action,characters,version,confirmed})});
        batchReview.visible=false;Toast.success('已保存');await load();
    } catch(failure){Toast.error(failure.message);}
    finally{saving.value=false;}
}
function applySelected() {
    if(!selectedAction.value || saving.value)return;
    const action=selectedAction.value,characters=[...selected.value],version=result.value.version??null;
    if(action==='approve') {
        Object.assign(batchReview,{visible:true,rows:result.value.items.filter(row=>characters.includes(row.character)),version,confirmed:false});return;
    }
    Dialog.warning({title:actionLabels[action]+'所选 '+characters.length+' 项映射？',
        description:action==='enable'?'重新启用停用前保存的规则，待审候选仍需单独审核。':'停用后保留原字。',
        confirmText:actionLabels[action],cancelText:'取消',showCancel:true,onConfirm:()=>saveBatch(action,characters,version)});
}
function openEditor(row) {
    Object.assign(edit,{character:row?.character||'',candidates:(row?.savedCandidates||(row?.reviewRequired?row.reviewCandidates:row?.candidates)||[]).join('\n'),
        note:row?.note||'',existing:!!row,version:result.value.version??null,pending:!!row?.reviewRequired,confirmed:false,reasons:row?.reviewReasons||[],links:row?.reviewLinks||[]});
    editorVisible.value=true;
}
async function save() {
    if(edit.pending && !edit.confirmed){Toast.error('请先确认字形关系及候选顺序');return;}
    saving.value=true;
    try {
        await request('/dictionary/entries',{method:'PUT',body:JSON.stringify({character:edit.character,candidates:edit.candidates.split('\n').map(value=>value.trim()).filter(Boolean),
            note:edit.note,action:edit.pending?'approve':'override',confirmed:edit.confirmed,version:edit.version})});
        editorVisible.value=false;Toast.success('已保存');await load();
    } catch(failure){Toast.error(failure.message);}
    finally{saving.value=false;}
}
function change(row,action) {
    const version=result.value.version??null;
    Dialog.warning({title:(action==='disable'?'停用':action==='enable'?'启用':'重置')+'「'+row.character+'」的映射？',
        description:action==='enable'?'重新启用停用前保存的规则。':action==='disable'?'停用后保留原字。':'将恢复默认字库中的映射。',
        confirmText:'确认',cancelText:'取消',showCancel:true,onConfirm:async()=>{
            saving.value=true;
            try{await request('/dictionary/entries',{method:'PUT',body:JSON.stringify({character:row.character,action,version,note:'',confirmed:false})});Toast.success('已保存');await load();}
            catch(failure){Toast.error(failure.message);}finally{saving.value=false;}
        }});
}
async function showHistory(){try{history.value=await request('/dictionary/history');historyVisible.value=true;}catch(failure){Toast.error(failure.message);}}
async function download(kind) {
    try {
        const data=await request('/dictionary/export?kind='+kind);
        const url=URL.createObjectURL(new Blob([JSON.stringify(data,null,2)],{type:'application/json;charset=utf-8'}));
        const link=document.createElement('a');link.href=url;link.download='字镜'+(kind==='reviews'?'-待确认关系':kind==='overrides'?'-修正记录':'-字库')+'.json';link.click();
        setTimeout(()=>URL.revokeObjectURL(url),1000);
    } catch(failure){Toast.error(failure.message);}
}
const formatCandidates=values=>values?.length?values.join(' / '):'保持原字';
const pendingCandidates=row=>row.pending?row.reviewCandidates.filter(value=>!row.candidates.includes(value)):[];
const rowStatus=mappingStatus;
const rowStatusLabel=row=>({disabled:'停用',pending:'待审',enabled:'启用'})[rowStatus(row)];
const rowSource=row=>row.source==='supplement'?'基础补充':row.source==='custom'||row.source==='disabled'?'自定义修正':row.source==='pending'?'来源资料':'默认字库';
const formatTime=value=>value?new Date(value).toLocaleString('zh-CN'):'初始修正';
function openPublic(){window.open('/tools/glyphlens/','_blank','noopener,noreferrer');}
function submitEditor(){document.getElementById('char-edit-form')?.requestSubmit();}
async function showSources(row) {
    try{const data=await request('/sources?'+new URLSearchParams({character:row.character}));sourceRecords.value=data.records;sourceVisible.value=true;}
    catch(failure){Toast.error(failure.message);}
}
</script>

<template>
    <VPageHeader title="字镜 · GlyphLens">
        <template #actions>
            <div class="actions" v-if="section === 'glyphs'">
                <VButton :disabled="loading || !!error" @click="showHistory">修改记录</VButton>
                <VButton :disabled="loading || !!error" @click="download('overrides')">导出修正</VButton>
                <VButton :disabled="loading || !!error" @click="download('table')">导出字库</VButton>
                <VButton :disabled="loading || !!error" @click="download('reviews')">导出待确认</VButton>
                <VButton @click="openPublic">查看前台</VButton>
            </div>
        </template>
    </VPageHeader>
    <div class="dictionary-page">
        <div class="actions section-nav">
            <VButton :type="section==='glyphs'?'secondary':undefined" @click="section='glyphs'">字库</VButton>
            <VButton :type="section==='lexicon'?'secondary':undefined" @click="section='lexicon'">词典消歧</VButton>
            <VButton :type="section==='feedback'?'secondary':undefined" @click="section='feedback'">字形反馈</VButton>
            <VButton :type="section==='sources'?'secondary':undefined" @click="section='sources'">资料来源</VButton>
        </div>
        <template v-if="section === 'glyphs'">
        <VCard>
            <div class="filters">
                <div class="filter-field">
                    <label for="char-filter-source">范围</label>
                    <FormKit id="char-filter-source" v-model="source" type="select" outer-class="char-filter-control" :options="[{label: '全部字形', value: 'all'}, {label: '自定义修正', value: 'changed'},{label:'待确认关系',value:'pending'}]" />
                </div>
                <div class="filter-field search-field">
                    <label for="char-filter-query">检索</label>
                    <FormKit id="char-filter-query" v-model="query" type="text" outer-class="char-filter-control" :delay="0" placeholder="原字、候选字或 Unicode 编码" @keyup.enter="search" />
                </div>
                <div class="filter-actions">
                    <VButton :loading="loading" @click="search">查询</VButton>
                    <VButton type="primary" :disabled="loading || saving || !!error" @click="openEditor()">新增映射</VButton>
                </div>
            </div>
        </VCard>
        <VCard v-if="error"><p role="alert">{{ error }}</p><VButton @click="load">重新加载</VButton></VCard>
        <VCard v-else>
            <div class="list-toolbar">
                <div class="summary">有效映射 {{ result.effectiveCount }} 项 · 自定义修正 {{ result.changedCount }} 项 · 待确认 {{ result.pendingCount || 0 }} 项</div>
                <div class="actions">
                    <span v-if="selected.length" class="selection-count">已选 {{ selected.length }} 项</span>
                    <VButton :disabled="!selectedAction || loading || saving" :loading="saving" :title="selected.length && !selectedAction ? '混合状态不能批量操作' : undefined" @click="applySelected">{{ actionLabels[selectedAction] || '操作' }}</VButton>
                    <VButton v-if="selected.length" :disabled="saving" @click="selected = []">取消</VButton>
                </div>
            </div>
            <VLoading v-if="loading" />
            <VEmpty v-else-if="!result.items.length" title="没有匹配的字形" />
            <div v-else class="table-wrap">
                <table>
                    <thead><tr><th class="selection-cell"><input type="checkbox" aria-label="全选当前页映射" :checked="allSelected" :indeterminate="selected.length > 0 && !allSelected" :disabled="!selectable.length || saving" @change="toggleAll"></th><th>原字</th><th>映射</th><th>状态</th><th>来源</th><th>备注</th><th>操作</th></tr></thead>
                    <tbody>
                        <tr v-for="row in result.items" :key="row.character">
                            <td class="selection-cell"><input v-model="selected" type="checkbox" :value="row.character" :aria-label="`选择原字 ${row.character}`" :disabled="saving"></td>
                            <td><span class="glyph"><span class="character">{{ row.character }}</span><small>{{ unicodeCodes(row.character) }}</small></span></td>
                            <td><span v-if="row.candidates.length === 1 && row.candidates[0] === row.character" class="note">保持原字</span><div v-else class="glyph-list"><span v-for="(candidate, index) in row.candidates" :key="index" class="glyph"><span class="character">{{ candidate }}</span><small>{{ unicodeCodes(candidate) }}</small></span></div><small v-if="pendingCandidates(row).length" class="note">待确认：{{ pendingCandidates(row).join(' / ') }}</small></td>
                            <td><span class="mapping-status" :class="`mapping-status--${rowStatus(row)}`" :title="row.pending ? '已确定候选继续生效，额外候选待审' : row.source === 'disabled' ? '本表映射已停用' : undefined"><i aria-hidden="true"></i>{{ rowStatusLabel(row) }}</span></td>
                            <td>{{ rowSource(row) }}</td>
                            <td class="note">{{ row.note }}</td>
                            <td><div class="actions">
                                <VButton size="xs" :disabled="saving" @click="openEditor(row)">{{ row.pending ? '审核' : '编辑' }}</VButton>
                                <VButton v-if="row.source !== 'disabled'" size="xs" :disabled="saving" @click="change(row, 'disable')">停用</VButton>
                                <VButton v-if="row.source === 'disabled'" size="xs" :disabled="saving" @click="change(row, 'enable')">启用</VButton>
                                <VButton v-if="['custom','disabled'].includes(row.source)" size="xs" :disabled="saving" @click="change(row, 'reset')">重置</VButton>
                                <VButton size="xs" @click="showSources(row)">来源</VButton>
                            </div></td>
                        </tr>
                    </tbody>
                </table>
            </div>
            <div class="pagination">
                <span>共 {{ result.total }} 项 · 第 {{ page }} 页</span>
                <VButton :disabled="loading || page <= 1" @click="page--; load()">上一页</VButton>
                <VButton :disabled="loading || page * 50 >= result.total" @click="page++; load()">下一页</VButton>
            </div>
        </VCard>
        <VModal :visible="batchReview.visible" title="审核映射" :width="640" @close="batchReview.visible = false">
            <div class="history table-wrap"><table><thead><tr><th>原字</th><th>确认后的候选顺序</th></tr></thead><tbody><tr v-for="row in batchReview.rows" :key="row.character"><td class="character">{{ row.character }}</td><td>{{ row.reviewCandidates.join(' / ') }}<small v-for="reason in row.reviewReasons" :key="reason">{{ reason }}</small></td></tr></tbody></table></div>
            <label class="batch-confirm"><input type="checkbox" v-model="batchReview.confirmed" :disabled="saving">已核对所选映射及候选顺序</label>
            <div class="editor-actions"><VButton :disabled="saving" @click="batchReview.visible=false">取消</VButton><VButton type="primary" :loading="saving" :disabled="!batchReview.confirmed || saving" @click="saveBatch('approve',batchReview.rows.map(row=>row.character),batchReview.version,true)">确认</VButton></div>
        </VModal>
        <VModal :visible="editorVisible" :title="edit.existing ? '编辑映射' : '新增映射'" :width="560" @close="editorVisible = false">
            <FormKit id="char-edit-form" type="form" :actions="false" :disabled="saving" @submit="save">
                <div v-if="edit.pending" class="review-note"><p v-for="reason in edit.reasons" :key="reason">{{ reason }}</p><a v-for="(url,index) in edit.links" :key="url" :href="url" target="_blank" rel="noopener noreferrer">来源 {{ index+1 }} </a></div>
                <FormKit v-model="edit.character" type="text" label="原字" validation="required" :delay="0" :disabled="edit.existing" :help="edit.existing ? undefined : '支持字形、U+4E00、0x4E00、4E00'" />
                <div v-if="!edit.existing && characterPreview" class="edit-preview"><span class="glyph"><span class="character">{{ characterPreview }}</span><small>{{ unicodeCodes(characterPreview) }}</small></span></div>
                    <FormKit v-model="edit.candidates" type="textarea" label="候选字" help="每行一个候选，支持字形或十六进制 Unicode 码。" validation="required" :rows="5" />
                    <div class="edit-preview"><template v-for="(candidate, index) in candidatePreview" :key="index"><span v-if="!candidate.error" class="glyph"><span class="character">{{ candidate.character }}</span><small>{{ unicodeCodes(candidate.character) }}</small></span><span v-else role="alert">{{ candidate.error }}</span></template></div>

                <FormKit v-model="edit.note" type="textarea" label="备注" :maxlength="300" :rows="2" />
                <FormKit v-if="edit.pending" v-model="edit.confirmed" type="checkbox" label="我已确认这些字形关系及候选顺序" />
            </FormKit>
            <template #footer>
                <div class="editor-actions">
                    <VButton :disabled="saving" @click="editorVisible = false">取消</VButton>
                    <VButton type="primary" :loading="saving" :disabled="saving || (edit.pending && !edit.confirmed)" @click="submitEditor">{{ edit.pending ? '确认' : '保存' }}</VButton>
                </div>
            </template>
        </VModal>
        <VModal :visible="sourceVisible" title="字形来源" :width="640" @close="sourceVisible=false">
            <p v-if="!sourceRecords.length">该条目来自人工修正或待核查的补充资料。</p>
            <p v-for="(record,index) in sourceRecords" :key="index"><a :href="record.url" target="_blank" rel="noopener noreferrer">{{ record.label }}</a></p>
        </VModal>
        </template>
        <LexiconView v-else-if="section === 'lexicon'" />
        <FeedbackView v-else-if="section === 'feedback'" />
        <SourcesView v-else />
        <VModal :visible="historyVisible" title="修改记录" :width="900" @close="historyVisible = false">
            <VEmpty v-if="!history.length" title="还没有后台修改记录" />
            <div v-else class="table-wrap history">
                <table>
                    <thead><tr><th>时间</th><th>原字</th><th>修改前</th><th>修改后</th><th>操作人</th><th>备注</th></tr></thead>
                    <tbody><tr v-for="(item, index) in history" :key="index">
                        <td>{{ formatTime(item.updatedAt) }}</td><td class="character">{{ item.character }}</td>
                        <td>{{ formatCandidates(item.before) }}</td><td>{{ formatCandidates(item.after) }}</td>
                        <td>{{ item.updatedBy }}</td><td>{{ item.note }}</td>
                    </tr></tbody>
                </table>
            </div>
        </VModal>
    </div>
</template>

<style scoped>
.dictionary-page { display: grid; grid-template-columns: minmax(0, 1fr); min-width: 0; gap: 16px; padding: 16px; container-type: inline-size; }
.actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.filters { display: grid; grid-template-columns: minmax(140px, 200px) minmax(220px, 1fr) auto; align-items: end; gap: 16px; padding: 8px; }
.filter-field { display: grid; gap: 12px; min-width: 0; }
.filter-field > label { font-size: 13px; }
.filter-field :deep(.char-filter-control) { margin: 0 !important; }
.filter-actions { display: flex; align-items: center; gap: 16px; }
.list-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; padding: 8px 12px 16px; }
.summary, .selection-count { font-size: 13px; opacity: .7; }
.selection-cell { width: 40px; }
.selection-cell input { width: 16px; height: 16px; vertical-align: middle; accent-color: #292b2a; cursor: pointer; }
.selection-cell input:disabled { cursor: default; opacity: .4; }
.table-wrap { overflow: auto; }
table { border-collapse: collapse; width: 100%; font-size: 13px; }
th, td { text-align: left; padding: 12px; border-bottom: 1px solid #94a3b830; vertical-align: middle; }
th { white-space: nowrap; font-weight: 500; }
td small { display: block; font-size: 10px; opacity: .6; }
.character { font-size: 22px; line-height: 1.4; }
.glyph { display: inline-flex; flex-direction: column; align-items: flex-start; }
.glyph small { display: block; margin-top: 2px; font-size: 10px; line-height: 1.5; opacity: .6; }
.glyph-list { display: flex; flex-wrap: wrap; align-items: start; gap: 12px 20px; }
.mapping-status { display: inline-flex; align-items: center; gap: 8px; white-space: nowrap; }
.mapping-status i { width: 8px; height: 8px; flex: none; border-radius: 50%; background: #16a34a; }
.mapping-status--disabled i { background: #dc2626; }
.mapping-status--pending i { background: #d69e12; }
.batch-confirm { display: flex; align-items: center; gap: 8px; margin: 16px 0; }
.edit-preview { display: flex; flex-wrap: wrap; gap: 12px 20px; margin: -8px 0 20px; }
.note { min-width: 120px; max-width: 240px; overflow-wrap: anywhere; }
.pagination { display: flex; align-items: center; justify-content: end; flex-wrap: wrap; gap: 12px; padding: 16px 8px 0; font-size: 13px; }
.history { max-height: 60vh; }
.editor-actions { display: flex; justify-content: end; gap: 8px; }
.section-nav { padding: 0 4px; }
.review-note { padding: 12px; margin-bottom: 16px; border: 1px solid #e0bc54; border-radius: 6px; font-size: 13px; }
.review-note p { margin: 0 0 6px; }
@media (max-width: 900px) { .filters { grid-template-columns: 1fr 1fr; } .search-field { grid-column: 1 / -1; } .filter-actions { grid-column: 1 / -1; justify-content: end; } }
@container (max-width: 900px) { .filters { grid-template-columns: 1fr 1fr; } .search-field { grid-column: 1 / -1; } .filter-actions { grid-column: 1 / -1; justify-content: end; } }
@media (max-width: 700px) { .dictionary-page { padding: 8px; } }
</style>
