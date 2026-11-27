<!-- SPDX-License-Identifier: MIT -->
<script setup>
import { onMounted,ref } from 'vue';
import { VCard,VButton,Toast } from '@halo-dev/components';
import { request } from './api';
const data=ref({summary:{},links:{}}),loading=ref(false);
const labels={mainland:'通用规范汉字表',unicode:'Unicode Unihan',opencc:'OpenCC 字符表',variants:'中国台湾地区教育主管部门《異體字字典》（补充参考）'};
const formatCount=(value)=>Number(value||0).toLocaleString('zh-CN');
async function load(){loading.value=true;try{data.value=await request('/sources');}catch(error){Toast.error(error.message);}finally{loading.value=false;}}
onMounted(load);
</script>
<template><VCard>
    <div class="head"><strong>字形资料来源</strong><VButton :loading="loading" @click="load">刷新</VButton></div>
    <p v-for="(url,key) in data.links" :key="key"><a :href="url" target="_blank" rel="noopener noreferrer">{{labels[key]||key}}</a></p>
    <section v-if="data.summary.counts" class="counts">
        <h3>当前基础字库</h3>
        <p>有效映射 {{formatCount(data.summary.counts.records)}} 条 · 待确认 {{formatCount(data.summary.counts.pending)}} 项</p>
        <p class="muted">单一字库，已包含发布前确认的修正和参考资料补充。</p>
    </section>
    <section class="counts">
        <h3>《通用规范汉字表》原始资料</h3>
        <p>规范字 {{formatCount(data.summary.mainland_standard)}} 个 · 附表繁体对应 {{formatCount(data.summary.formal_traditional)}} 条 · 附表异体对应 {{formatCount(data.summary.formal_variants)}} 条</p>
    </section>
</VCard></template>
<style scoped>.head{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px}p{margin:8px 0;font-size:14px;line-height:1.7}a{color:#8b6a17;text-decoration:underline}.counts{margin-top:24px}.counts h3{margin:0;font-size:14px;font-weight:600}.muted{opacity:.7}</style>
