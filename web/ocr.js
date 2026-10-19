window.LocalOcr={worker:null,controller:null,cancelled:false,
 cancel(){this.cancelled=true;this.controller?.abort();this.worker?.terminate();this.worker=null;this.rejectCancel?.(Error('已取消识别'));},
 async bounded(task,ms,label){let timer;try{return await Promise.race([task,new Promise((_,reject)=>{timer=setTimeout(()=>{this.controller?.abort();reject(Error(label+'超时，请检查网络后重试'));},ms);this.rejectCancel=reject;})]);}finally{clearTimeout(timer);this.rejectCancel=null;}},
 async prepare(progress){
 if(this.worker)return;this.controller=new AbortController();
 const simd=WebAssembly.validate(new Uint8Array([0,97,115,109,1,0,0,0,1,5,1,96,0,1,123,3,2,1,0,10,10,1,8,0,65,0,253,15,253,98,11]));
 const core='vendor/core/tesseract-core-'+(simd?'simd-':'')+'lstm.wasm.js';
 const assets=[['识别引擎','vendor/worker.min.js'],['计算组件',core],['中文模型','vendor/lang/chi_sim.traineddata'],['英文模型','vendor/lang/eng.traineddata']];
 const cache=await caches.open('daily-spending-web-models');
 for(const [name,path] of assets){const url=new URL(path,location.href).href;if(await cache.match(url)){progress(name+'已就绪');continue;}
 await this.bounded((async()=>{progress('正在下载'+name+'…首次约10MB，请保持联网');const res=await fetch(url,{signal:this.controller.signal});if(!res.ok)throw Error(name+'下载失败（'+res.status+'）');const reader=res.body?.getReader(),chunks=[];let received=0,total=Number(res.headers.get('content-length'));
 if(reader){while(true){const {done,value}=await reader.read();if(done)break;chunks.push(value);received+=value.length;progress('下载'+name+' '+(received/1048576).toFixed(1)+'MB'+(total?' / '+(total/1048576).toFixed(1)+'MB':'')+' · 截图不会上传');}}
 else chunks.push(new Uint8Array(await res.arrayBuffer()));
 await cache.put(url,new Response(new Blob(chunks),{headers:{'Content-Type':path.endsWith('.js')?'text/javascript':'application/octet-stream'}}));})(),60000,name+'下载');if(this.cancelled)throw Error('已取消识别');}
 progress('模型已下载，正在启动本地引擎…');
 let failure;const failed=new Promise((_,reject)=>failure=reject);
 const init=Tesseract.createWorker(['chi_sim','eng'],1,{workerPath:new URL('vendor/worker.min.js',location.href).href,corePath:new URL(core,location.href).href,langPath:new URL('vendor/lang',location.href).href,gzip:false,workerBlobURL:false,cacheMethod:'none',errorHandler:e=>failure(Error(String(e))),logger:m=>{const labels={'recognizing text':'正在本机识别','loading tesseract core':'加载计算组件','initializing tesseract':'启动计算组件','loading language traineddata':'载入中文和英文模型','initializing api':'初始化文字识别'};(this.log||progress)((labels[m.status]||'准备识别引擎')+' '+Math.round((m.progress||0)*100)+'%');}});
 let accepted=false;try{this.worker=await this.bounded(Promise.race([init,failed]),45000,'引擎启动');accepted=true;}finally{if(!accepted)init.then(w=>w.terminate()).catch(()=>{});}
 },
 async recognize(file,base,progress){
 this.cancelled=false;
 if(typeof WebAssembly==='undefined'||!window.Tesseract)throw Error('此浏览器暂不支持本地识别，请使用最新版Chrome或Edge');
 if(file.size>25*1024*1024)throw Error('单张截图请小于25MB');
 const bitmap=await createImageBitmap(file);if(bitmap.width*bitmap.height>24000000){bitmap.close();throw Error('截图过长，请分成几张后再选择');}
 const canvas=document.createElement('canvas');canvas.width=bitmap.width;canvas.height=bitmap.height;canvas.getContext('2d').drawImage(bitmap,0,0);bitmap.close();
 await this.prepare(progress);this.log=progress;

 await this.worker.setParameters({tessedit_pageseg_mode:'11',preserve_interword_spaces:'1',tessedit_char_whitelist:''});
 progress('正在本机识别图片…');const {data}=await this.bounded(this.worker.recognize(canvas,{}, {text:true,blocks:true}),90000,'图片识别');let rows=BillParser.rows(data);base=BillParser.baseDate(rows,file.name,base);let candidates=BillParser.parse(rows,base);
 for(let c of candidates.filter(c=>!c.known&&c.dateRow?.y)){const r=c.dateRow,cut=document.createElement('canvas'),top=Math.max(0,Math.floor(r.y-r.h)),height=Math.min(canvas.height-top,Math.ceil(r.h*2));cut.width=Math.floor(canvas.width*.65)*3;cut.height=height*3;cut.getContext('2d').drawImage(canvas,0,top,Math.floor(canvas.width*.65),height,0,0,cut.width,cut.height);const cx=cut.getContext('2d'),pixels=cx.getImageData(0,0,cut.width,cut.height);for(let j=0;j<pixels.data.length;j+=4){let v=pixels.data[j]<210?0:255;pixels.data[j]=pixels.data[j+1]=pixels.data[j+2]=v;}cx.putImageData(pixels,0,0);await this.worker.setParameters({tessedit_pageseg_mode:'7',tessedit_char_whitelist:'今天昨日0123456789-/: '});const retry=await this.bounded(this.worker.recognize(cut),20000,'日期识别');const dt=BillParser.date(retry.data.text,base);if(dt.known)Object.assign(c,dt);}
 const hash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',await file.arrayBuffer()))).map(b=>b.toString(16).padStart(2,'0')).join('');
 return candidates.map((c,i)=>({...c,fingerprint:'webshot-'+hash+'-'+i,imageHash:hash,content:'截图记账：'+c.note}));
}};

