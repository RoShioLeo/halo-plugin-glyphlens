// SPDX-License-Identifier: MIT
import './design-tokens.css';
import './design-system.css';
import { definePlugin } from '@halo-dev/ui-shared';
import { markRaw } from 'vue';
import DictionaryView from './DictionaryView.vue';
import GlyphLensIcon from './GlyphLensIcon.vue';

export default definePlugin({routes:[{parentName:'ToolsRoot',route:{
    path:'/glyphlens-dictionary',name:'GlyphLensDictionary',component:DictionaryView,
    meta:{title:'字镜 · GlyphLens',permissions:['plugin:lemonslice-glyphlens:manage'],
        menu:{name:'字镜',group:'tool',priority:30,icon:markRaw(GlyphLensIcon)}}
}}]});
