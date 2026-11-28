<!-- SPDX-License-Identifier: MIT -->
<script setup>
import { onMounted,ref } from 'vue';
import { VCard,VButton,VEmpty,Dialog,Toast } from '@halo-dev/components';
import { request } from './api';
import { unicodeCodes } from './unicode';
const data=ref({items:[],version:null}),loading=ref(false),status=ref('pending');
async function load(){loading.value=true;try{data.value=await request('/feedback');}catch(error){Toast.error(error.message);}finally{loading.value=false;}}
function process(ticket,next){Dialog.warning({title:next==='resolved'?'将反馈标为已处理？':'将反馈标为不采纳？',description:'此操作不会自动修改字库。',confirmText:'确认',cancelText:'取消',showCancel:true,onConfirm:async()=>{try{await request('/feedback/'+ticket.id,{method:'PUT',body:JSON.stringify({status:next,reply:'',version:data.value.version})});await load();Toast.success('已更新');}catch(error){Toast.error(error.message);}}});}
onMounted(load);
</script>
<template><VCard>
    <div class="toolbar"><FormKit v-model="status" type="select" :options="[{label:'待核查',value:'pending'},{label:'已处理',value:'resolved'},{label:'不采纳',value:'rejected'}]" /><VButton :loading="loading" @click="load">刷新</VButton></div>
    <VEmpty v-if="!data.items.some(item=>item.status===status)" title="没有对应反馈" />
    <div v-else class="table-wrap"><table><thead><tr><th>原字</th><th>建议字形</th><th>说明</th><th>时间</th><th>操作</th></tr></thead><tbody>
        <tr v-for="ticket in data.items.filter(item=>item.status===status)" :key="ticket.id">
            <td><span class="glyph">{{ticket.character}}</span><small>{{unicodeCodes(ticket.character)}}</small></td><td><span class="glyph">{{ticket.candidate||'—'}}</span><small>{{ticket.candidate?unicodeCodes(ticket.candidate):''}}</small></td>
            <td>{{ticket.note}}</td><td>{{new Date(ticket.createdAt).toLocaleString('zh-CN')}}</td>
            <td><div class="buttons"><VButton size="xs" v-if="status==='pending'" @click="process(ticket,'resolved')">已处理</VButton><VButton size="xs" v-if="status==='pending'" @click="process(ticket,'rejected')">不采纳</VButton></div></td>
        </tr>
    </tbody></table></div>
</VCard></template>
<style scoped>
.toolbar,.buttons{display:flex;align-items:center;gap: 8px;justify-content:space-between}.toolbar{margin-bottom: 16px}.toolbar :deep([data-type=select]){margin: 0;max-width:220px}.buttons{justify-content:start}.table-wrap{overflow:auto}table{border-collapse:collapse;width:100%;font-size: var(--ls-control-font-size)}th,td{text-align:left;padding: 16px;border-bottom:1px solid var(--ls-faint);vertical-align:middle}.glyph{font-size:22px}small{display:block;font-size: var(--ls-label-font-size);opacity:.6}
</style>
