#!/usr/bin/env python3
"""Optimisation mémoire du chargement : convertit les blockstates « multipart » en « variants » quand chaque état n'affiche
qu'un modèle. Minecraft cuit un MultiPartBakedModel PAR ÉTAT qui garde la liste de TOUS les sélecteurs : un bloc de scène
« Large » (4 orientations × 128 parts × attaché) avec 400 sélecteurs = 400 000 paires (prédicat, modèle) ; 23 millions en tout en 1.8.2,
d'où les 8 Go au chargement. En « variants », chaque état pointe vers un seul modèle cuit et partagé (cache par modèle + rotation).
Sûr à relancer (les fichiers déjà en variants sont ignorés)."""
import json, itertools, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]; AS=ROOT/'common/src/main/resources/assets/concertstage'
specs={s['id']:s for s in json.loads((ROOT/'common/src/main/resources/data/concertstage/stage_equipment.json').read_text())['equipment']}
DYES=['white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray','cyan','purple','blue','brown','green','red','black']
FACING=['north','east','south','west']
def domains(name):
    if name in specs:
        s=specs[name]; cnt=s['grid'][0]*s['grid'][1]*s['grid'][2]
        d={'facing':FACING,'part':[str(i) for i in range(128 if cnt>16 else 16)]}
        if s.get('mode')=='joined': d['joined']=['false','true']
        if s.get('mode')=='mounted': d['mounted']=['false','true']
        return d
    if name=='curtain_cloth': return {'axis':['x','z'],'color':DYES,'top':['false','true'],'drop':[str(i) for i in range(6)],'shift':['0','1','2']}
    if name=='curtain_rail': return {'facing':FACING,'color':DYES,'drop':[str(i) for i in range(6)],'shift':['0','1','2']}
    return None
def matches(when,state):
    if not when: return True
    if 'OR' in when: return any(matches(w,state) for w in when['OR'])
    if 'AND' in when: return all(matches(w,state) for w in when['AND'])
    return all(state.get(k) in str(v).split('|') for k,v in when.items())
EMPTY={'concertstage:block/stage/empty','concertstage:block/empty_truss'}
import hashlib
def load_model(ref):
    return json.loads((AS/'models'/(ref.split(':',1)[1]+'.json')).read_text())
def composite(hits):
    """Plusieurs modèles pour un même état (même rotation) : un modèle composé (éléments mis bout à bout, textures renommées)."""
    ys={h.get('y',0) for h in hits}; xs={h.get('x',0) for h in hits}
    if len(ys)>1 or len(xs)>1 or any(h.get('uvlock') for h in hits): return None
    tex={}; els=[]; ao=True
    for i,h in enumerate(hits):
        m=load_model(h['model'])
        if 'parent' in m or 'loader' in m: return None
        ao=ao and m.get('ambientocclusion',True)
        ren={k:f'p{i}_{k}' for k in m.get('textures',{}) if k!='particle'}
        for k,v in m.get('textures',{}).items():
            if k=='particle': tex.setdefault('particle',v if not v.startswith('#') else m['textures'].get(v[1:],v))
            else: tex[ren[k]]=v if not v.startswith('#') else '#'+ren.get(v[1:],v[1:])
        for e in m.get('elements',[]):
            e=json.loads(json.dumps(e))
            for f in e.get('faces',{}).values():
                t=f.get('texture','')
                if t.startswith('#'): f['texture']='#'+ren.get(t[1:],t[1:])
            els.append(e)
    name='composite/'+hashlib.sha1('|'.join(h['model'] for h in hits).encode()).hexdigest()[:16]
    out=AS/'models/block'/(name+'.json'); out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps({'ambientocclusion':ao,'textures':tex,'elements':els},separators=(',',':')))
    r={'model':'concertstage:block/'+name}
    if hits[0].get('y'): r['y']=hits[0]['y']
    if hits[0].get('x'): r['x']=hits[0]['x']
    return r
def convert(path):
    d=json.loads(path.read_text())
    if 'multipart' not in d: return None
    dom=domains(path.stem)
    if dom is None: return None
    used=set()
    for r in d['multipart']: used|=set(r.get('when',{}))
    keys=[k for k in dom if k in used]
    unknown=used-set(keys)-{'OR','AND'}
    if unknown: return None
    variants={}
    empty_model=next((r['apply']['model'] for r in d['multipart'] if not r.get('when') and r['apply']['model'] in EMPTY),'concertstage:block/stage/empty')
    for combo in itertools.product(*[dom[k] for k in keys]):
        st=dict(zip(keys,combo))
        hits=[r['apply'] for r in d['multipart'] if matches(r.get('when'),st) and not (isinstance(r['apply'],dict) and r['apply'].get('model') in EMPTY)]
        if any(isinstance(h,list) for h in hits): return None
        if len(hits)>1:
            c=composite(hits)
            if c is None: return None
            hits=[c]
        variants[','.join(f'{k}={v}' for k,v in st.items())]=hits[0] if hits else {'model':empty_model}
    before=len(d['multipart'])
    path.write_text(json.dumps({'variants':variants},separators=(',',':')))
    return before,len(variants)
def main():
    done=kept=0
    for p in sorted(AS.glob('blockstates/*.json')):
        r=convert(p)
        if r: done+=1
        elif 'multipart' in json.loads(p.read_text()): kept+=1; print('multipart conservé :',p.stem)
    print('convertis',done,'; multipart restants',kept)
if __name__=='__main__': main()
