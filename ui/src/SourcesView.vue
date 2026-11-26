<!-- SPDX-License-Identifier: MIT -->
<script setup>
import { onMounted,ref } from 'vue';
import { VCard,VButton,Toast } from '@halo-dev/components';
import { request } from './api';
const data=ref({summary:{},links:{}}),loading=ref(false);
const labels={mainland:'通用规范汉字表',unicode:'Unicode Unihan',opencc:'OpenCC 字符表'};
async function load(){loading.value=true;try{data.value=await request('/sources');}catch(error){Toast.error(error.message);}finally{loading.value=false;}}
onMounted(load);
</script>
<template><VCard>
    <div class="head"><strong>字形资料来源</strong><VButton :loading="loading" @click="load">刷新</VButton></div>
    <p v-for="(url,key) in data.links" :key="key"><a :href="url" target="_blank" rel="noopener noreferrer">{{labels[key]||key}}</a></p>
    <div class="counts"><p>规范字 {{data.summary.mainland_standard||0}} 个</p><p>附表繁体对应 {{data.summary.formal_traditional||0}} 条</p><p>附表异体对应 {{data.summary.formal_variants||0}} 条</p></div>
</VCard></template>
<style scoped>.head{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px}p{margin:12px 0;font-size:14px}a{color:#8b6a17;text-decoration:underline}.counts{margin-top:28px;opacity:.7}</style>
