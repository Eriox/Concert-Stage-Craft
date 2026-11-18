// BB_WEB_APP: official Blockbench web assets including dist/bundle.js; CHROME_BIN: Chromium.
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const {chromium}=require(process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES?process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES+'/playwright':'playwright');
(async()=>{
 const app=process.env.BB_WEB_APP,root=path.resolve(__dirname,'blockbench/realistic');if(!app||!process.env.CHROME_BIN)throw new Error('Set BB_WEB_APP and CHROME_BIN');
 const browser=await chromium.launch({executablePath:process.env.CHROME_BIN,headless:true,args:['--no-sandbox','--no-zygote','--disable-gpu','--enable-unsafe-swiftshader']});
 const page=await browser.newPage({viewport:{width:1600,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.route('**/*',async route=>{const u=new URL(route.request().url()),p=path.join(app,decodeURIComponent(u.pathname==='/'?'/index.html':u.pathname));if(u.hostname!=='blockbench.local'||!p.startsWith(app)||!fs.existsSync(p)){await route.abort();return;}await route.fulfill({body:fs.readFileSync(p),contentType:p.endsWith('.js')?'application/javascript':p.endsWith('.css')?'text/css':p.endsWith('.html')?'text/html':p.endsWith('.json')?'application/json':'application/octet-stream'});});
 await page.goto('http://blockbench.local',{waitUntil:'domcontentloaded'});await page.waitForFunction(()=>window.Blockbench?.setup_successful===true,{timeout:30000});
 const version=await page.evaluate(()=>Blockbench.version);console.log('Native Blockbench',version);const reports=[];
 for(const file of fs.readdirSync(root,{recursive:true}).filter(x=>x.endsWith('.bbmodel')).sort()){
  const bytes=fs.readFileSync(root+'/'+file),data=JSON.parse(bytes),before=errors.length;
  await page.evaluate(({data,file})=>Codecs.project.load(data,{path:file}),{data,file});
  await page.waitForFunction(()=>Texture.all.every(t=>t.img.complete&&t.img.naturalWidth>0),{timeout:15000});
  const r=await page.evaluate(()=>{const e=Outliner.elements[0],key=Object.keys(e.vertices)[0],old=e.vertices[key][0];e.vertices[key][0]=old+.01;const editable=e.vertices[key][0]===old+.01;e.vertices[key][0]=old;const compiled=Codecs.project.compile({raw:true});let motion=[];
   if(Project.name==='motor_tower_10_corner3'){
    Modes.options.animate.select();const g=Group.all.find(g=>g.name==='Chariot mobile');
    for(const a of Animation.all){a.select();for(const f of[0,.5,1]){Timeline.setTime(a.length*f);Animator.preview();motion.push({animation:a.name,f,y:g.mesh.position.y});}}
   }
   return {elements:Outliner.elements.length,meshes:Mesh.all.length,groups:Group.all.length,textures:Texture.all.map(t=>({name:t.name,width:t.img.naturalWidth,height:t.img.naturalHeight,error:t.error})),editable,roundtrip_elements:compiled.elements.length,roundtrip_format:compiled.meta.format_version,animations:Animation.all.map(a=>({name:a.name,length:a.length})),motion};});
  r.file=file;r.sha256=crypto.createHash('sha256').update(bytes).digest('hex');r.new_errors=errors.slice(before);
  if(r.elements!==data.elements.length||r.roundtrip_elements!==data.elements.length||r.meshes!==data.elements.length||!r.editable||r.new_errors.length||r.animations.length!==(data.animations||[]).length)throw new Error(JSON.stringify(r));
  if(file==='towers/motor_tower_10_corner3.bbmodel')for(const name of['monter','descendre']){const rows=r.motion.filter(m=>m.animation===name);if(rows.length!==3||Math.abs(rows[2].y-rows[0].y-(name==='monter'?112:-112))>1e-4||Math.abs(rows[1].y-(rows[0].y+rows[2].y)/2)>1e-4)throw new Error('Wrong native motion '+JSON.stringify(rows));}
  reports.push(r);console.log('PASS',file,r.elements,'meshes',r.groups,'groups');await page.evaluate(async()=>{Project.saved=true;await Project.close(true);});
 }
 fs.writeFileSync(path.resolve(root,'../../realistic_docs/blockbench_validation.json'),JSON.stringify({application:'Blockbench',version,runtime:'Official web application, local assets; Chromium software WebGL',models:reports},null,2));console.log('ALL',reports.length,'native open/edit/round-trip checks passed');await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
