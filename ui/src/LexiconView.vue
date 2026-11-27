<!-- SPDX-License-Identifier: MIT -->
<script setup>
import { onMounted,reactive,ref } from 'vue';
import { VCard,VButton,VModal,VEmpty,Toast } from '@halo-dev/components';
import { request } from './api';
const data=ref({items:[],version:null}),visible=ref(false),saving=ref(false),loading=ref(false);
const edit=reactive({id:null,phrase:'',result:'',sense:'',enabled:true,version:null});
function resultOf(rule){const chars=[...rule.phrase];for(const choice of rule.choices)chars[choice.offset]=choice.target;return chars.join('');}
async function load(){loading.value=true;try{data.value=await request('/lexicon');}catch(error){Toast.error(error.message);}finally{loading.value=false;}}
function open(rule){Object.assign(edit,{id:rule?.id||null,phrase:rule?.phrase||'',result:rule?resultOf(rule):'',sense:rule?.sense||'',enabled:rule?.enabled??true,version:data.value.version});visible.value=true;}
async function save(){saving.value=true;try{await request('/lexicon',{method:'PUT',body:JSON.stringify(edit)});visible.value=false;Toast.success('已保存');await load();}catch(error){Toast.error(error.message);}finally{saving.value=false;}}
function submit(){document.getElementById('glyphlens-lexicon-form')?.requestSubmit();}
onMounted(load);
</script>
<template>
    <VCard>
        <div class="toolbar"><p>词典仅从已确认字形候选中消歧，不替换词汇。</p><div><VButton :loading="loading" @click="load">刷新</VButton><VButton type="primary" :disabled="loading" @click="open()">新增词条</VButton></div></div>
        <VEmpty v-if="!data.items.length" title="还没有消歧词条" />
        <div v-else class="table-wrap"><table><thead><tr><th>词条</th><th>消歧结果</th><th>含义</th><th>状态</th><th>操作</th></tr></thead><tbody>
            <tr v-for="rule in data.items" :key="rule.id"><td>{{rule.phrase}}</td><td>{{resultOf(rule)}}</td><td>{{rule.sense}}</td><td>{{rule.enabled?'已启用':'已停用'}}</td><td><VButton size="xs" @click="open(rule)">编辑</VButton></td></tr>
        </tbody></table></div>
    </VCard>
    <VModal :visible="visible" title="消歧词条" :width="580" @close="visible=false">
        <FormKit id="glyphlens-lexicon-form" type="form" :actions="false" :disabled="saving" @submit="save">
            <FormKit v-model="edit.phrase" type="text" label="原词条" validation="required" :maxlength="128" />
            <FormKit v-model="edit.result" type="text" label="消歧结果" validation="required" help="字数须相同；每个字只能使用已有候选。" :maxlength="128" />
            <FormKit v-model="edit.sense" type="textarea" label="含义" :maxlength="500" :rows="2" />
            <FormKit v-model="edit.enabled" type="checkbox" label="启用此词条" />
        </FormKit>
        <template #footer><div class="buttons"><VButton :disabled="saving" @click="visible=false">取消</VButton><VButton type="primary" :loading="saving" @click="submit">保存</VButton></div></template>
    </VModal>
</template>
<style scoped>
.toolbar,.buttons{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}.toolbar{margin-bottom:20px}.toolbar p{font-size:13px;opacity:.7}.toolbar>div,.buttons{display:flex;gap:8px}.buttons{justify-content:end}.table-wrap{overflow:auto}table{border-collapse:collapse;width:100%;font-size:13px}th,td{text-align:left;padding:12px;border-bottom:1px solid #94a3b830}
</style>
