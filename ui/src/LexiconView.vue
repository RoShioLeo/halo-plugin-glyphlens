<!-- SPDX-License-Identifier: MIT -->
<script setup>
import { computed,onMounted,onUnmounted,reactive,ref,watch } from 'vue';
import { VCard,VButton,VModal,VEmpty,Toast } from '@halo-dev/components';
import { request } from './api';
import { unicodeInput,unicodeCodes } from './unicode';
const data=ref({items:[],version:null}),visible=ref(false),saving=ref(false),loading=ref(false);
const edit=reactive({id:null,character:'',candidate:'',contexts:'',note:'',enabled:true,version:null});
const options=ref([]),candidateLoading=ref(false),candidateError=ref('');
const labels={default:'默认词典',modified:'默认修正',custom:'自定义'};
const candidateOptions=computed(()=>{
    const values=[...options.value];if(edit.candidate && !values.includes(edit.candidate))values.push(edit.candidate);
    return values.map(value=>({label:value+' · '+unicodeCodes(value)+(options.value.includes(value)?'':'（当前不可用）'),value}));
});
let sequence=0,timer;
watch(()=>[visible.value,edit.character],()=>{
    const ticket=++sequence;clearTimeout(timer);options.value=[];candidateError.value='';candidateLoading.value=false;
    if(!visible.value || !edit.character.trim())return;
    candidateLoading.value=true;
    timer=setTimeout(async()=>{
        try {
            const character=unicodeInput(edit.character);
            const result=await request('/lexicon/candidates?'+new URLSearchParams({character}));
            if(ticket!==sequence)return;
            options.value=result.candidates;
            if(!result.eligible)candidateError.value='该字目前没有多候选，请先在字库中确认字形关系。';
            if(!edit.candidate && result.eligible)edit.candidate=result.candidates[0];
        } catch(error){if(ticket===sequence)candidateError.value=error.message;}
        finally{if(ticket===sequence)candidateLoading.value=false;}
    },200);
},{flush:'sync'});
onUnmounted(()=>{sequence++;clearTimeout(timer);});
async function load(){loading.value=true;try{data.value=await request('/lexicon');}catch(error){Toast.error(error.message);}finally{loading.value=false;}}
function open(rule){Object.assign(edit,{id:rule?.id||null,character:rule?.character||'',candidate:rule?.candidate||'',contexts:rule?.contexts.join('\n')||'',note:rule?.note||'',enabled:rule?.enabled??true,version:data.value.version??null});visible.value=true;}
async function save(){
    saving.value=true;
    try {
        await request('/lexicon',{method:'PUT',body:JSON.stringify({...edit,contexts:edit.contexts.split('\n').map(word=>word.trim()).filter(Boolean)})});
        visible.value=false;Toast.success('已保存');await load();
    } catch(error){Toast.error(error.message);}finally{saving.value=false;}
}
function submit(){document.getElementById('glyphlens-lexicon-form')?.requestSubmit();}
onMounted(load);
</script>
<template>
    <VCard>
        <div class="toolbar"><p>按原字及候选字维护简体上下文词条。</p><div><VButton :loading="loading" @click="load">刷新</VButton><VButton type="primary" :disabled="loading" @click="open()">新增规则</VButton></div></div>
        <VEmpty v-if="!data.items.length" title="还没有消歧词条" />
        <div v-else class="table-wrap"><table><thead><tr><th>原字</th><th>命中候选</th><th>简体词条</th><th>来源</th><th>备注</th><th>状态</th><th>操作</th></tr></thead><tbody>
            <tr v-for="rule in data.items" :key="rule.id">
                <td><span class="glyph">{{rule.character}}</span><small>{{unicodeCodes(rule.character)}}</small></td>
                <td><span class="glyph">{{rule.candidate}}</span><small>{{unicodeCodes(rule.candidate)}}</small></td>
                <td class="contexts">{{rule.contexts.join('、')}}</td><td>{{labels[rule.source]||'自定义'}}</td><td>{{rule.note}}</td><td>{{rule.enabled?'已启用':'已停用'}}</td><td><VButton size="xs" @click="open(rule)">编辑</VButton></td>
            </tr>
        </tbody></table></div>
    </VCard>
    <VModal :visible="visible" title="上下文消歧" :width="580" @close="visible=false">
        <FormKit id="glyphlens-lexicon-form" type="form" :actions="false" :disabled="saving" @submit="save">
            <FormKit v-model="edit.character" type="text" label="原字" validation="required" :delay="0" help="填写字库中有多个候选的原字，支持十六进制 Unicode 码。" />
            <FormKit v-model="edit.candidate" type="select" label="命中候选" validation="required" :options="candidateOptions" :disabled="candidateLoading" placeholder="选择候选字" />
            <p v-if="candidateError" class="field-message" role="status">{{candidateError}}</p>
            <FormKit v-model="edit.contexts" type="textarea" label="简体词条" validation="required" :rows="5" :delay="0" help="每行一个包含所选候选字的词条，例如：干燥、干净。" />
            <FormKit v-model="edit.note" type="textarea" label="备注" :maxlength="500" :rows="2" />
            <FormKit v-model="edit.enabled" type="checkbox" label="启用此规则" />
        </FormKit>
        <template #footer><div class="buttons"><VButton :disabled="saving" @click="visible=false">取消</VButton><VButton type="primary" :loading="saving" :disabled="saving || candidateLoading || (edit.enabled && (!!candidateError || !options.includes(edit.candidate)))" @click="submit">保存</VButton></div></template>
    </VModal>
</template>
<style scoped>
.toolbar,.buttons{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}.toolbar{margin-bottom:20px}.toolbar p{font-size:13px;opacity:.7}.toolbar>div,.buttons{display:flex;gap:8px}.buttons{justify-content:end}.table-wrap{overflow:auto}table{border-collapse:collapse;width:100%;font-size:13px}th,td{text-align:left;padding:12px;border-bottom:1px solid #94a3b830;vertical-align:middle}.glyph{font-size:22px;line-height:1.4}small{display:block;font-size:10px;opacity:.6}.contexts{min-width:200px;max-width:460px;overflow-wrap:anywhere}.field-message{font-size:13px;margin:-8px 0 16px;color:#a16207}
</style>
