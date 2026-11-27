// SPDX-License-Identifier: MIT
export const base = '/apis/console.api.glyphlens.lemonslice.cloud/v1alpha1';
export async function response(path: string, init: RequestInit = {}) {
    const cookie=document.cookie.split('; ').find(value=>value.startsWith('XSRF-TOKEN='));
    const headers:Record<string,string>={Accept:'application/json',...(init.headers as Record<string,string> || {})};
    if(init.body && !headers['Content-Type']) headers['Content-Type']='application/json';
    if(cookie) headers['X-XSRF-TOKEN']=decodeURIComponent(cookie.slice('XSRF-TOKEN='.length));
    const result=await fetch(base+path,{...init,headers,credentials:'same-origin'});
    if(!result.ok) {
        const problem=await result.json().catch(()=>({}));
        throw new Error(result.status===409?'资料已被修改，请刷新后重新确认':result.status===403||result.status===401?'仅超级管理员可以访问':problem.detail||'操作失败，请重试');
    }
    return result;
}
export async function request(path: string, init: RequestInit = {}) {return (await response(path,init)).json();}
