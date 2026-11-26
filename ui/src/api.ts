// SPDX-License-Identifier: MIT
export const base = '/apis/console.api.glyphlens.lemonslice.cloud/v1alpha1';
export async function request(path: string, init: RequestInit = {}) {
    const cookie=document.cookie.split('; ').find(value=>value.startsWith('XSRF-TOKEN='));
    const headers:Record<string,string>={Accept:'application/json',...(init.headers as Record<string,string> || {})};
    if(init.body) headers['Content-Type']='application/json';
    if(cookie) headers['X-XSRF-TOKEN']=decodeURIComponent(cookie.slice('XSRF-TOKEN='.length));
    const response=await fetch(base+path,{...init,headers,credentials:'same-origin'});
    if(!response.ok) {
        const problem=await response.json().catch(()=>({}));
        throw new Error(response.status===409?'资料已被修改，请刷新后重新确认':response.status===403||response.status===401?'仅超级管理员可以访问':problem.detail||'操作失败，请重试');
    }
    return response.json();
}
