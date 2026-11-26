<!-- SPDX-License-Identifier: MIT -->
<script setup>
import { onMounted,ref } from 'vue';
import { VCard,VButton,Toast } from '@halo-dev/components';
import { request } from './api';
const data=ref({summary:{},links:{}}),loading=ref(false);
const labels={mainland:'通用规范汉字表',variants:'教育部异体字字典',unicode:'Unicode Unihan',opencc:'OpenCC 字符表'};
async function load(){loading.value=true;try{data.value=await request('/sources');}catch(error){Toast.error(error.message);}finally{loading.value=false;}}
onMounted(load);
</script>
<template><VCard>
    <div class="head"><strong>字形资料来源</strong><VButton :loading="loading" @click="load">刷新</VButton></div>
    <p v-for="(url,key) in data.links" :key="key"><a :href="url" target="_blank" rel="noopener noreferrer">{{labels[key]||key}}</a></p>
    <div class="counts"><p>规范字 {{data.summary.mainland_standard||0}} 个</p><p>正字来源 {{data.summary.regular_source_records||0}} 条</p><p>异体来源 {{data.summary.variant_source_records||0}} 条</p><p>可定位 Unicode 的正异关系 {{data.summary.unicode_relations||0}} 条</p><p>仅有图片、私用字码等资料另列核查，不强行编入转换。</p></div>
</VCard></template>
<style scoped>.head{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px}p{margin:12px 0;font-size:14px}a{color:#8b6a17;text-decoration:underline}.counts{margin-top:28px;opacity:.7}</style>
