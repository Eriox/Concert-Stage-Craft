#!/usr/bin/env python3
from pathlib import Path
import argparse,zipfile
p=argparse.ArgumentParser();p.add_argument('output',type=Path);a=p.parse_args();root=Path(__file__).resolve().parents[1]
with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED,compresslevel=9)as z:
 for f in sorted(root.rglob('*')):
  rel=f.relative_to(root)
  if f.is_file()and not any(x in {'.gradle','build','__pycache__','.git'}for x in rel.parts)and f.suffix not in {'.pyc','.class'}:z.write(f,Path('concert-stage')/rel)
print(a.output,a.output.stat().st_size,'bytes')
