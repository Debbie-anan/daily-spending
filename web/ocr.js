window.LocalOcr={worker:null,async recognize(file,base,progress){
 if(file.size>25*1024*1024)throw Error('单张截图请小于25MB');
 const bitmap=await createImageBitmap(file);if(bitmap.width*bitmap.height>24000000){bitmap.close();throw Error('截图过长，请分成几张后再选择');}
 const canvas=document.createElement('canvas');canvas.width=bitmap.width;canvas.height=bitmap.height;canvas.getContext('2d').drawImage(bitmap,0,0);bitmap.close();
 if(!this.worker)this.worker=await Tesseract.createWorker(['chi_sim','eng'],1,{workerPath:new URL('vendor/worker.min.js',location.href).href,corePath:new URL('vendor/core/',location.href).href,langPath:new URL('vendor/lang',location.href).href,gzip:false,workerBlobURL:false,cacheMethod:'none',logger:m=>progress(m.status==='recognizing text'?'正在本机识别 '+Math.round(m.progress*100)+'%':'正在准备本地识别模型…')});
 await this.worker.setParameters({tessedit_pageseg_mode:'11',preserve_interword_spaces:'1',tessedit_char_whitelist:''});
 const {data}=await this.worker.recognize(canvas,{}, {text:true,blocks:true});let rows=BillParser.rows(data);let candidates=BillParser.parse(rows,base);
 for(let c of candidates.filter(c=>!c.known&&c.dateRow?.y)){const r=c.dateRow,cut=document.createElement('canvas'),top=Math.max(0,Math.floor(r.y-r.h)),height=Math.min(canvas.height-top,Math.ceil(r.h*2));cut.width=Math.floor(canvas.width*.65)*3;cut.height=height*3;cut.getContext('2d').drawImage(canvas,0,top,Math.floor(canvas.width*.65),height,0,0,cut.width,cut.height);const cx=cut.getContext('2d'),pixels=cx.getImageData(0,0,cut.width,cut.height);for(let j=0;j<pixels.data.length;j+=4){let v=pixels.data[j]<210?0:255;pixels.data[j]=pixels.data[j+1]=pixels.data[j+2]=v;}cx.putImageData(pixels,0,0);await this.worker.setParameters({tessedit_pageseg_mode:'7',tessedit_char_whitelist:'今天昨日0123456789-/: '});const retry=await this.worker.recognize(cut);const dt=BillParser.date(retry.data.text,base);if(dt.known)Object.assign(c,dt);}
 const hash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',await file.arrayBuffer()))).map(b=>b.toString(16).padStart(2,'0')).join('');
 return candidates.map((c,i)=>({...c,fingerprint:'webshot-'+hash+'-'+i,imageHash:hash,content:'截图记账：'+c.note}));
}};

