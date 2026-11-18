import sys,os
bad=0;n=0
for root in sys.argv[1:]:
  for dp,_,fs in os.walk(root):
    for f in fs:
      if not f.endswith('.obj'): continue
      n+=1;v=vt=vn=0
      for i,l in enumerate(open(os.path.join(dp,f))):
        t=l.split()
        if not t: continue
        if t[0]=='v': v+=1
        elif t[0]=='vt': vt+=1
        elif t[0]=='vn': vn+=1
        elif t[0]=='f':
          for s in t[1:]:
            p=(s.split('/')+['',''])[:3]
            if int(p[0])>v or (p[1] and int(p[1])>vt) or (p[2] and int(p[2])>vn): bad+=1; print('BAD',f,i+1); break
print(n,'obj files, faces referencing undefined vertices:',bad)
