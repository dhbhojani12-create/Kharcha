const C='kharcha-v5';
const CORE=['./','./index.html','./manifest.json','./icon-192.png','./icon-512.png'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(C).then(c=>c.addAll(CORE)));self.skipWaiting()});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(k=>Promise.all(k.filter(x=>x!==C).map(x=>caches.delete(x)))));self.clients.claim()});
self.addEventListener('fetch',e=>{
  const r=e.request;
  if(r.method==='POST'&&new URL(r.url).searchParams.get('a')==='share'){
    e.respondWith((async()=>{const f=await r.formData();const c=await caches.open('kharcha-share');
      await c.put('./shared-text',new Response(JSON.stringify({title:f.get('title')||'',text:f.get('text')||'',url:f.get('url')||''})));
      await c.delete('./shared-file');const file=f.getAll('files')[0];if(file&&file.size)await c.put('./shared-file',new Response(file,{headers:{'content-type':file.type||'image/png'}}));
      return Response.redirect(new URL('./?a=share',self.registration.scope).href,303)})());return}
  if(r.method!=='GET')return;
  const u=new URL(r.url);
  if(u.origin===location.origin){
    e.respondWith(fetch(r).then(res=>{const cp=res.clone();caches.open(C).then(c=>c.put(r,cp));return res}).catch(()=>caches.match(r,{ignoreSearch:true}).then(m=>m||caches.match('./index.html'))));
  }else if(u.host==='cdn.jsdelivr.net'){
    e.respondWith(caches.match(r).then(m=>m||fetch(r).then(res=>{const cp=res.clone();caches.open(C).then(c=>c.put(r,cp));return res})));
  }
});
