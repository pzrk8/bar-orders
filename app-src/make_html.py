import json,re,sys,os
VERSION=os.environ.get('APP_VERSION','1.2')
UI_BUILD=int(os.environ.get('UI_BUILD','3'))
SEEDFILE=os.environ.get('SEED_FILE')
src=open('/home/claude/bar-order/index.html',encoding='utf-8').read()
def R(old,new):
    global src
    assert src.count(old)==1,('MISS',old[:90]); src=src.replace(old,new)
def ld(n):
    r=json.load(open('/home/claude/apk-seed/app/%s.json'%n,encoding='utf-8')); return r.get('data',r)
seed={'catalog':ld('catalog'),'suppliers':ld('suppliers'),'cart':{'lines':{},'via':{}}}
if SEEDFILE:
    b=json.load(open(SEEDFILE,encoding='utf-8')); seed['catalog']=b['catalog']; seed['suppliers']=b['suppliers']

# fonts: drop Google Fonts, use bundled files
src=re.sub(r'<link rel="preconnect"[^>]*>\n','',src)
src=re.sub(r'<link rel="stylesheet" href="https://fonts.googleapis.com[^>]*>\n','',src)
src=src.replace('<meta name="claude:viewport-fit-sides" content="cover">\n','')
CYR="U+0301,U+0400-045F,U+0490-0491,U+04B0-04B1,U+2116"
LAT="U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,U+2000-206F,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD"
ff=''
for w in (400,500,600,700):
    for sub,rng in (('cyrillic',CYR),('latin',LAT)):
        ff+="@font-face{font-family:'Onest';font-style:normal;font-weight:%d;font-display:swap;src:url(fonts/onest-%s-%d-normal.woff2) format('woff2');unicode-range:%s}\n"%(w,sub,w,rng)
for sub,rng in (('cyrillic',CYR),('latin',LAT)):
    ff+="@font-face{font-family:'Unbounded';font-style:normal;font-weight:600;font-display:swap;src:url(fonts/unbounded-%s-600-normal.woff2) format('woff2');unicode-range:%s}\n"%(sub,rng)
R('<style>\n/* Layout:','<style>\n'+ff+'html{color-scheme:light}\n:root{padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}\nbody{margin:0}\nimg{max-width:100%}\n*{-webkit-tap-highlight-color:transparent}\nbutton{-webkit-user-select:none;user-select:none}\n.updbar{position:fixed;left:12px;right:12px;bottom:calc(var(--nav-h) + 10px + env(safe-area-inset-bottom,0px));z-index:35;background:var(--surface);border:1.5px solid var(--accent);border-radius:16px;box-shadow:var(--shadow);padding:12px;display:flex;align-items:center;gap:8px;flex-wrap:wrap}\n.updbar>div{flex:1 1 140px;min-width:0;display:flex;flex-direction:column;gap:2px}\n.updbar span{font-size:13px;color:var(--muted)}\n/* Layout:')

# storage through the Android bridge
R("function lsGet(k){try{return JSON.parse(localStorage.getItem(LS+k)||'null');}catch(e){return null;}}",
  "function lsGet(k){try{if(window.AppStore)return JSON.parse(AppStore.get(LS+k)||'null');return JSON.parse(localStorage.getItem(LS+k)||'null');}catch(e){return null;}}")
R("function lsSet(k,v){try{localStorage.setItem(LS+k,JSON.stringify(v));}catch(e){}}",
  "function lsSet(k,v){try{if(window.AppStore){AppStore.set(LS+k,JSON.stringify(v));return;}localStorage.setItem(LS+k,JSON.stringify(v));}catch(e){}}")
R("['catalog','suppliers','cart'].forEach(n=>{const v=lsGet('doc:'+n);const d=docs[n];",
  "['catalog','suppliers','cart'].forEach(n=>{let v=lsGet('doc:'+n);if(!v&&window.SEED&&window.SEED[n]){v=JSON.parse(JSON.stringify(window.SEED[n]));lsSet('doc:'+n,v);}const d=docs[n];")
R("async function start(){\n  render();\n","async function start(){\n  render();\n  if(window.APP_MODE){startLocal();try{AppStore.uiReady();}catch(e){}return;}\n")
R("if(mode==='local')return","if(mode==='local'&&!window.APP_MODE)return")
R("  const fallback=()=>{if(legacyCopy(text))done();else openCopySheet(text,key);};\n",
  "  const fallback=()=>{if(legacyCopy(text))done();else openCopySheet(text,key);};\n  if(window.AppStore){try{AppStore.copy(text);done();return;}catch(e){}}\n")
R("start();\n})();","""/* ---------- app: back button, saving on minimize, backups ---------- */
let pendingImport=null;
function flushAll(){['catalog','suppliers','cart'].forEach(n=>{const d=docs[n];if(d.pending){clearTimeout(d.timer);flush(n);}});}
window.__flushAll=flushAll;
document.addEventListener('visibilitychange',()=>{if(document.hidden)flushAll();});
window.addEventListener('pagehide',flushAll);
window.__back=function(){
  if(sheet){closeSheet();return true;}
  if(S.pick){S.pick=null;setView('sup');return true;}
  if(S.view!=='catalog'){setView('catalog');return true;}
  if(S.q||S.cat!=='all'||S.sup!=='all'){S.q='';$('#q').value='';$('#qClear').hidden=true;S.cat='all';S.sup='all';window.scrollTo(0,0);render();return true;}
  return false;
};
function fmtDate(iso){const d=new Date(iso);return isNaN(d)?'':d.toLocaleDateString('ru-RU',{day:'numeric',month:'long'})+', '+d.toLocaleTimeString('ru-RU',{hour:'2-digit',minute:'2-digit'});}
function backupHtml(){
  if(!window.AppStore)return '';
  const last=lsGet('lastBackup');
  let h='<section class="card setcard"><div class="chead"><h2>Файл настроек</h2></div><p class="sub">В файле все позиции с названиями, категориями и объёмами, а также поставщики с их Telegram и приветствиями. Отправьте его себе в Telegram, например в «Избранное». Если телефон сломается или приложение удалится, установите APK заново и загрузите этот файл.</p>';
  if(pendingImport){const n=Object.values((pendingImport.catalog||{}).items||{}).filter(i=>i&&!i.del&&i.name).length;const d=pendingImport.date?fmtDate(pendingImport.date):'';
    h+='<div class="confirm"><p>Заменить каталог и поставщиков данными из файла'+(d?' от '+esc(d):'')+'? В файле '+n+' поз. Текущий заказ очистится.</p><div class="actions"><button class="btn danger" data-act="bk-yes">Заменить</button><button class="btn2" data-act="bk-no">Отмена</button></div></div>';}
  else h+='<div class="actions"><button class="btn" data-act="bk-share">Отправить файл</button><button class="btn2" data-act="bk-save">Сохранить на телефон</button><button class="btn2" data-act="bk-load">Загрузить из файла</button></div>';
  h+='<p class="small">'+(last?'Последний файл: '+esc(fmtDate(last)):'Файл настроек ещё ни разу не сохранялся')+' · версия '+window.APP_VERSION+'</p>';
  return h+'</section>';
}
function backupData(){flushAll();const now=new Date();return {now:now,json:JSON.stringify({app:'barorders',v:1,date:now.toISOString(),catalog:docs.catalog.eff,suppliers:docs.suppliers.eff})};}
function backupName(now){return 'zakazy-bar-nastroiki-'+now.getFullYear()+'-'+String(now.getMonth()+1).padStart(2,'0')+'-'+String(now.getDate()).padStart(2,'0')+'.json';}
window.__updateReady=notes=>{
  let el=document.getElementById('updBar');
  if(!el){el=document.createElement('div');el.id='updBar';el.className='updbar';document.body.appendChild(el);}
  el.innerHTML='<div><b>Вышло обновление</b>'+(notes?'<span>'+esc(notes)+'</span>':'')+'</div><button class="btn" data-act="upd-go">Обновить</button><button class="btn-ghost" data-act="upd-later" aria-label="Позже" style="font-size:20px;padding:0 8px">✕</button>';
};
window.__backupDone=ok=>{if(ok){lsSet('lastBackup',new Date().toISOString());render();}toast(ok?'Файл сохранён':'Не удалось сохранить файл');};
window.__importBackup=text=>{
  try{const d=JSON.parse(text);if(!d||!d.catalog||!d.catalog.items)throw 0;pendingImport=d;if(S.view!=='sup')setView('sup');else render();window.scrollTo(0,0);}
  catch(e){toast('Этот файл не похож на файл настроек');}
};
document.addEventListener('click',e=>{
  const b=e.target.closest('[data-act]');if(!b)return;
  const a=b.dataset.act;
  if(a==='bk-share'){const x=backupData();try{AppStore.shareBackup(x.json,backupName(x.now));lsSet('lastBackup',x.now.toISOString());render();}catch(err){toast('Не удалось открыть отправку');}}
  else if(a==='bk-save'){const x=backupData();AppStore.exportBackup(x.json,backupName(x.now));}
  else if(a==='bk-load')AppStore.importBackup();
  else if(a==='upd-go'){flushAll();AppStore.applyUpdate();}
  else if(a==='upd-later'){const el=document.getElementById('updBar');if(el)el.remove();}
  else if(a==='bk-no'){pendingImport=null;render();}
  else if(a==='bk-yes'){const src={catalog:pendingImport.catalog,suppliers:pendingImport.suppliers,cart:{lines:{},via:{}}};
    ['catalog','suppliers','cart'].forEach(n=>{const d=docs[n];clearTimeout(d.timer);d.pending=null;d.inflight=null;d.snap=isObj(src[n])?src[n]:{};lsSet('doc:'+n,d.snap);recompute(n);});
    lsSet('copied',null);pendingImport=null;toast('Настройки загружены');render();}
});
start();
})();""")
head='<!doctype html>\n<html lang="ru">\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover">\n'
boot="<!-- BARORDERS_UI ui-build:"+str(UI_BUILD)+" -->\n<script>window.APP_MODE=true;window.APP_VERSION='"+VERSION+" (сборка "+str(UI_BUILD)+")';try{if(window.AppStore&&AppStore.isNight)document.documentElement.setAttribute('data-theme',AppStore.isNight()?'dark':'light');}catch(e){}</script>\n"
seedjs='<script>window.SEED='+json.dumps(seed,ensure_ascii=False).replace('</','<\\/')+';</script>\n'
# split: title+style into head, rest into body
i=src.index('</style>')+len('</style>')
out=head+boot+src[:i]+'\n</head>\n<body>\n'+seedjs+src[i:]+'\n</body>\n</html>\n'
open('/home/claude/apk/assets/index.html','w',encoding='utf-8').write(out)
open('/home/claude/apk/assets/ui_build.txt','w').write(str(UI_BUILD))
web=head+boot+src[:i]+'\n</head>\n<body>\n'+src[i:]+'\n</body>\n</html>\n'
os.makedirs('/home/claude/apk/publish',exist_ok=True)
open('/home/claude/apk/publish/index.html','w',encoding='utf-8').write(web)
print('ok',len(out))
