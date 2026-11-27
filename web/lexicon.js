// SPDX-License-Identifier: MIT
const scalar=value=>typeof value==='string' && [...value].length===1 && !(value.codePointAt(0)>=0xD800 && value.codePointAt(0)<=0xDFFF);
const node=()=>({next:new Map(),right:null,outputs:new Map()});

/** Index simplified context words under their ambiguous source character.
 * The focus character is an anchor, never a word replacement. Context lookup
 * accepts approved glyph variants without changing or cascading the input.
 */
export class DisambiguationIndex {
    constructor(rules=[]) {
        this.bySource=new Map();this.rules=[];
        for(const rule of rules) {
            if(rule.enabled===false || !scalar(rule.character) || !scalar(rule.candidate) || !Array.isArray(rule.contexts))continue;
            const contexts=[...new Set(rule.contexts.filter(word=>typeof word==='string' && [...word].length>=2 && [...word].length<=64 && [...word].includes(rule.candidate)))];
            if(!contexts.length)continue;
            this.rules.push({...rule,contexts});
            if(!this.bySource.has(rule.character))this.bySource.set(rule.character,node());
            const root=this.bySource.get(rule.character);
            for(const context of contexts) {
                const characters=[...context];
                for(let anchor=0;anchor<characters.length;anchor++) {
                    if(characters[anchor]!==rule.candidate)continue;
                    let left=root;
                    for(let offset=anchor-1;offset>=0;offset--) {
                        const value=characters[offset];
                        if(!left.next.has(value))left.next.set(value,node());
                        left=left.next.get(value);
                    }
                    if(!left.right)left.right=node();
                    let right=left.right;
                    for(let offset=anchor+1;offset<characters.length;offset++) {
                        const value=characters[offset];
                        if(!right.next.has(value))right.next.set(value,node());
                        right=right.next.get(value);
                    }
                    right.outputs.set(rule.candidate,characters.length);
                }
            }
        }
    }
    resolve(characters,candidatesAt) {
        const decisions=new Map(),cache=new Map();
        const variantsAt=position=>{
            if(!cache.has(position)) {
                const original=characters[position];
                cache.set(position,scalar(original)?[...new Set([original,...(candidatesAt(position)||[])])]:[]);
            }
            return cache.get(position);
        };
        const advance=(states,values)=>{
            const next=new Set();
            for(const state of states)for(const value of values) {
                const child=state.next.get(value);if(child)next.add(child);
            }
            return [...next];
        };
        for(let position=0;position<characters.length;position++) {
            const root=this.bySource.get(characters[position]);
            if(!root)continue;
            const candidates=candidatesAt(position);
            if(!candidates || candidates.length<2)continue;
            const allowed=new Set(candidates);
            const accept=(target,length)=>{
                if(!allowed.has(target))return;
                const previous=decisions.get(position);
                if(!previous || length>previous.length)decisions.set(position,{length,values:new Set([target])});
                else if(length===previous.length)previous.values.add(target);
            };
            let leftStates=[root];
            for(let distance=0;leftStates.length;distance++) {
                for(const left of leftStates) {
                    if(!left.right)continue;
                    let rightStates=[left.right];
                    for(let offset=position+1;rightStates.length;offset++) {
                        for(const state of rightStates)for(const [target,length]of state.outputs)accept(target,length);
                        if(offset>=characters.length || !rightStates.some(state=>state.next.size))break;
                        rightStates=advance(rightStates,variantsAt(offset));
                    }
                }
                const offset=position-distance-1;
                if(offset<0 || !leftStates.some(state=>state.next.size))break;
                leftStates=advance(leftStates,variantsAt(offset));
            }
        }
        return decisions;
    }
}
