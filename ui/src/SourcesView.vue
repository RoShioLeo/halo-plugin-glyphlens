<!-- SPDX-License-Identifier: MIT -->
<script setup>
import { onMounted,ref } from 'vue';
import { VCard,VButton,Toast } from '@halo-dev/components';
import { request,response } from './api';
const emit=defineEmits(['import']);
const data=ref({summary:{},links:{}}),loading=ref(false);
const sources=[
    {key:'mainland',name:'通用规范汉字表及附表',use:'规范简体及已明确的繁体、异体对应。'},
    {key:'unicode',name:'Unicode 资料',use:'规范等价别名、Unihan 字形对应及竖排标点。'},
    {key:'opencc',name:'OpenCC 通用字符表',use:'经确认的通用字符补充关系，不采用地区词组表。'},
    {key:'variants',name:'中国台湾地区教育主管部门《異體字字典》',use:'对应唯一、目标常用且无来源冲突的补充参考。'},
    {key:'manual',name:'手动维护来源',use:'维护者手工补充，原始文献出处尚待核对。'},
    {key:'retained',name:'保留原字选项',use:'转换策略，未发生字形替换，不计作外部资料。'},
    {key:'unresolved',name:'原始出处待补',use:'映射已确认采用，原始资料仍需补齐。'}
];
const count=value=>Number(value||0).toLocaleString('zh-CN');
async function load(){loading.value=true;try{data.value=await request('/sources');}catch(error){Toast.error(error.message);}finally{loading.value=false;}}
onMounted(load);
async function downloadPackage(){
    loading.value=true;
    try{
        const result=await response('/dictionary/package'),url=URL.createObjectURL(await result.blob());
        const link=document.createElement('a');link.href=url;link.download='GlyphLens-Dictionary-'+data.value.summary.version+'.zip';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
    }catch(error){Toast.error(error.message);}finally{loading.value=false;}
}
</script>
<template><VCard>
    <div class="head"><strong>字库版本</strong><div class="actions"><VButton :disabled="loading" @click="emit('import')">导入字库</VButton><VButton :disabled="loading || !data.summary.version" @click="downloadPackage">导出默认字库包</VButton><VButton :loading="loading" @click="load">刷新</VButton></div></div>
    <section v-if="data.summary.counts">
        <h3>默认字库 <span class="muted">{{data.summary.version}}</span></h3>
        <p>收录原字 {{count(data.summary.counts.records)}} 个 · 候选对应 {{count(data.summary.counts.relations)}} 条 · 多候选 {{count(data.summary.counts.multi)}} 字 · 待确认 {{count(data.summary.counts.pending)}} 项</p>
        <div v-if="data.summary.source_counts" class="table-wrap"><table><thead><tr><th>来源</th><th>收录字数</th><th>映射条数</th><th>录入范围</th></tr></thead><tbody>
            <tr v-for="source in sources.filter(item=>item.key!=='unresolved'||data.summary.source_counts[item.key]?.relations)" :key="source.key"><td><a v-if="data.links[source.key]" :href="data.links[source.key]" target="_blank" rel="noopener noreferrer">{{source.name}}</a><span v-else>{{source.name}}</span></td><td>{{count(data.summary.source_counts[source.key]?.characters)}}</td><td>{{count(data.summary.source_counts[source.key]?.relations)}}</td><td>{{source.use}}</td></tr>
        </tbody><tfoot><tr><th>全库总计</th><td>{{count(data.summary.counts.records)}}</td><td>{{count(data.summary.counts.relations)}}</td><td>按完整字库去重统计。</td></tr></tfoot></table></div>
        <p class="muted">按本版默认字库自动统计，不含安装后的站点修正。每条对应只归入一个来源，详情保留可核查的原始记录及中间字归并路径。同字可跨来源，保留原字和出处待补另列。人工审核独立记录，不替代资料出处。</p>
    </section>
    <section v-if="data.summary.lexicon">
        <h3>默认消歧词典</h3>
        <p>{{count(data.summary.lexicon.characters)}} 个多候选原字 · {{count(data.summary.lexicon.rules)}} 组规则 · {{count(data.summary.lexicon.contexts)}} 个简体词条</p>
        <p>按原字的合法候选和简体上下文词条推荐默认字形。校对由用户逐处确认，或按当前结果确认剩余位置。</p>
        <p><a href="https://github.com/RoShioLeo/halo-plugin-glyphlens/blob/main/docs/LEXICON.md" target="_blank" rel="noopener noreferrer">词条范围与来源说明</a></p>
    </section>
    <section><h3>导入与导出</h3><p>默认字库包包含本版映射、消歧词典、来源及许可。站点手动修正请在“字库”中导出 JSON；导入时会先预览处理结果，再确认保存。</p><p><a href="https://github.com/RoShioLeo/halo-plugin-glyphlens/blob/main/docs/DICTIONARY-IMPORT.md" target="_blank" rel="noopener noreferrer">自定义字库格式说明</a></p></section>
    <section><h3>致谢</h3><p>感谢 @小兽KEMONO 提供技术支持</p></section>
</VCard></template>
<style scoped>
.actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px}.head{gap:12px;flex-wrap:wrap}
.head{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px}section+section{margin-top:28px}h3{font-size:14px;font-weight:600;margin:0 0 8px}h3 span{font-weight:400;margin-left:8px}p{margin:8px 0;font-size:13px;line-height:1.7}a{color:#8b6a17;text-decoration:underline}.muted{opacity:.7}.table-wrap{overflow:auto}table{border-collapse:collapse;width:100%;font-size:13px}th,td{text-align:left;padding:10px 12px;border-bottom:1px solid #94a3b830;line-height:1.7;vertical-align:top}th{font-weight:500}td:first-child{min-width:160px}
</style>
