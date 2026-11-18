import json, glob, os, sys, collections
R='/home/claude/v3/out/CSC_StagePlatforms_Scaffolding_V3'
rows=[]; bad=0
for f in sorted(glob.glob(R+'/**/*.bbmodel', recursive=True)):
    d=json.load(open(f)); errs=[]
    ids=[e['uuid'] for e in d['elements']]; idset=set(ids)
    if len(ids)!=len(idset): errs.append('uuid dupliqués')
    seen=collections.Counter(); nulls=[]
    def w(o, path):
        for c in o:
            if isinstance(c,dict):
                if not c['children'] and c.get('export') is False: nulls.append(('/'.join(path+[c['name']]), c['origin']))
                w(c['children'], path+[c['name']])
            else: seen[c]+=1
    w(d['outliner'], [])
    if set(seen)!=idset: errs.append(f'outliner/éléments incohérents ({len(set(seen)^idset)})')
    if any(v>1 for v in seen.values()): errs.append('élément référencé 2×')
    nt=len(d['textures'])
    for e in d['elements']:
        for fn,fc in e['faces'].items():
            t=fc.get('texture')
            if t is not None and not (0<=t<nt): errs.append(f'texture hors liste {e["name"]}'); break
            tw=d['textures'][t]['uv_width'] if t is not None else 1e9; th=d['textures'][t]['uv_height'] if t is not None else 1e9
            uvs=list(fc['uv'].values()) if e['type']=='mesh' else [fc['uv'][:2],fc['uv'][2:]]
            if any(not(-0.01<=u<=tw+0.01 and -0.01<=v<=th+0.01) for u,v in uvs): errs.append(f'UV hors texture {e["name"]}'); break
        if e['type']=='cube' and any(a>b for a,b in zip(e['from'],e['to'])): errs.append('cube inversé')
        if e['type']=='mesh':
            for fc in e['faces'].values():
                if any(k not in e['vertices'] for k in fc['vertices']): errs.append('sommet manquant'); break
    ty=collections.Counter(e['type'] for e in d['elements'])
    rows.append({'fichier':os.path.relpath(f,R),'elements':len(d['elements']),'cubes':ty['cube'],'meshes':ty['mesh'],'textures':nt,
                 'taille_ko':os.path.getsize(f)//1024,'erreurs':sorted(set(errs)),'reperes':[{'nom':n,'position':p} for n,p in nulls]})
    if errs: bad+=1; print('ERREUR',os.path.relpath(f,R),sorted(set(errs))[:3])
json.dump(rows,open(R+'/Documentation/inventaire_et_reperes.json','w'),ensure_ascii=False,indent=1)
print(len(rows),'fichiers,',bad,'en erreur ; éléments total',sum(r['elements'] for r in rows))
