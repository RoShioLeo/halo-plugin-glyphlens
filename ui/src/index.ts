// SPDX-License-Identifier: MIT
import { definePlugin } from '@halo-dev/ui-shared';
import { IconCharacterRecognition } from '@halo-dev/components';
import { markRaw } from 'vue';
import DictionaryView from './DictionaryView.vue';

export default definePlugin({routes:[{parentName:'ToolsRoot',route:{
    path:'/glyphlens-dictionary',name:'GlyphLensDictionary',component:DictionaryView,
    meta:{title:'字镜 · GlyphLens',permissions:['plugin:lemonslice-glyphlens:manage'],
        menu:{name:'字镜',group:'tool',priority:30,icon:markRaw(IconCharacterRecognition)}}
}}]});
