# CSC 1.2.0 - Lightshow Revolution : les 13 constructeurs de projecteurs (coordonnees absolues en metres)
from math import sin, cos, pi, radians, sqrt


def base_mh(fx, w, d, h, name, items=('pcin', 'pcout', 'xlr', 'xlr', 'rj', 'rj'), disp=-.24):
    fx.to('BASE', 'ROOT')
    fx.box((w, d, h - .018), (0, 0, .018 + (h - .018) / 2), 'body', bevel=.014, lod=2)
    for sx in (-1, 1):
        for sy in (-1, 1): fx.cyl(.02, .018, (sx * (w / 2 - .04), sy * (d / 2 - .04), .009), 'rubber', segs=12)
    fx.box((w * .72, .004, .005), (0, -d / 2 - .001, h * .26), 'accent', bevel=0, lod=0)
    fx.push('-Y', (0, -d / 2, h * .60))
    fx.display(.07, .042, (w * disp, 0, 0))
    fx.text('CSC', .028, (w * .24, .006, 0)); fx.text(name, .0095, (w * .24, -.018, 0))
    fx.pop()
    fx.grp('CONNECTORS'); fx.push('Y', (0, d / 2, h * .58)); fx.connectors(list(items), pitch=min(.036, (w - .05) / len(items))); fx.pop()
    fx.grp('VENTILATION')
    for ax, sx in (('X', 1), ('-X', -1)):
        fx.push(ax, (sx * w / 2, 0, h * .56)); fx.vent(d * .5, h * .40, n=6); fx.pop()
    fx.grp('BASE')
    for i, sx in enumerate((-1, 1)):
        for sy in (-1, 1): fx.cyl(.014, .004, (sx * w * .3, sy * d * .28, .016), 'steel', segs=12, lod=0)
        fx.mount('MOUNT_OMEGA_%d' % (i + 1), (sx * w * .3, 0, .014))
    fx.mount('MOUNT_SAFETY', (0, d * .4, .014))


def yoke_mh(fx, half, th, prof, Z, hub, bridge, turn, zb, vent=True):
    """Lyre : plateau tournant, pont, deux bras (profil propre a chaque appareil), moyeux TILT."""
    fx.to('YOKE', 'PAN')
    fx.cyl(turn, .024, (0, 0, zb + .012), 'body2', segs=40, lod=2)
    fx.cyl(turn + .002, .004, (0, 0, zb + .016), 'accent', segs=40, lod=0)
    fx.box((2 * half + th, bridge[0], bridge[1]), (0, 0, zb + .02 + bridge[1] / 2), 'body', bevel=.012, lod=2)
    xo = half + th / 2
    for sx in (-1, 1):
        fx.prism(prof, th, (sx * half, 0, 0), 'body', axis='X', lod=2, bevel=.005)
        fx.cyl(hub, .012, (sx * (xo + .005), 0, Z), 'body2', axis='X', segs=28)
        fx.cyl(hub * .68, .005, (sx * (xo + .012), 0, Z), 'accent', axis='X', segs=28, lod=0)
        zm = (prof[0][1] + Z) / 2 + .01
        fx.box((.004, prof[2][0] * 1.1, (Z - prof[0][1]) * .5), (sx * (xo + .001), 0, zm), 'body2', bevel=.002, lod=0)
        fx.bolts([(sx * (xo + .003), yy * prof[2][0] * .45, zm + zz * (Z - prof[0][1]) * .2) for yy in (-1, 1) for zz in (-1, 1)], .0035, 'X')
        if vent:
            fx.grp('VENTILATION'); fx.push('X' if sx > 0 else '-X', (sx * (xo + .003), 0, zm)); fx.vent(prof[2][0] * .7, (Z - prof[0][1]) * .3, n=5); fx.pop(); fx.grp('YOKE')


# ------------------------------------------------------------------ 1. PHANTOM S1 (Moving Spot)
def phantom_s1(fx):
    Z = .50
    fx.pivot('ROOT', None, (0, 0, 0)); fx.pivot('PAN', 'ROOT', (0, 0, .135)); fx.pivot('TILT', 'PAN', (0, 0, Z))
    base_mh(fx, .40, .335, .135, 'PHANTOM S1')
    for sx in (-1, 1): fx.handle((sx * .20, -.09, .09), (sx * .20, .09, .09), (sx * .032, 0, 0), .008)
    yoke_mh(fx, .2125, .045, [(-.105, .16), (.105, .16), (.088, Z), (.06, Z + .085), (-.06, Z + .085), (-.088, Z)], Z, .072, (.21, .055), .15, .135)
    for sx in (-1, 1): fx.cyl(.03, .03, (sx * .18, 0, Z), 'steel', axis='X', segs=16)
    fx.to('HEAD', 'TILT')
    fx.loft([(.26, .105, .105, 2.5), (.235, .15, .15, 3.5), (.08, .17, .17, 5), (-.14, .17, .17, 5), (-.235, .155, .155, 3), (-.275, .134, .134, 2.2)], 'body', loc=(0, 0, Z), segs=36, lod=2)
    fx.loft([(-.02, .1725, .1725, 5), (-.034, .1725, .1725, 5)], 'accent', loc=(0, 0, Z), segs=36, lod=0)
    fx.box((.20, .012, .20), (0, .262, Z), 'body2', bevel=.004)
    for i in range(9): fx.box((.006, .05, .17), ((i - 4) * .021, .29, Z), 'aludark', bevel=0, lod=0)
    fx.box((.20, .05, .012), (0, .29, Z + .089), 'body2', bevel=.002); fx.box((.20, .05, .012), (0, .29, Z - .089), 'body2', bevel=.002)
    fx.grp('VENTILATION')
    for ax, sx in (('X', 1), ('-X', -1)):
        fx.push(ax, (sx * .1685, .13, Z)); fx.vent(.11, .10, n=7); fx.pop()
    fx.push('Z', (0, .13, Z + .1685)); fx.vent(.12, .10, n=7); fx.pop()
    fx.push('Y', (0, .314, Z)); fx.fan(.062); fx.pop()
    fx.grp('HEAD')
    fx.push('Z', (0, -.08, Z + .1702)); fx.text('CSC', .034, (0, .02, 0)); fx.text('PHANTOM S1', .013, (0, -.012, 0)); fx.pop()
    fx.to('OPTICS', 'TILT')
    fx.tube(.136, .108, .03, (0, -.287, Z), 'body2', axis='-Y', segs=36, lod=2)
    fx.tube(.139, .133, .006, (0, -.302, Z), 'accent', axis='-Y', segs=36, lod=0)
    fx.lathe([(.108, 0), (.098, -.05)], (0, -.30, Z), 'black', axis='-Y', segs=36)
    fx.lens(.098, .028, (0, -.25, Z), segs=36)
    fx.emit('EMIT_MAIN', (0, -.30, Z))
    for n, y in (('ZOOM_GROUP', -.20), ('FOCUS_GROUP', -.12), ('PRISM_1', -.06), ('PRISM_2', -.03), ('IRIS', .0), ('GOBO_ROT_1', .03),
                 ('GOBO_ROT_2', .06), ('ANIM_WHEEL', .09), ('COLOR_CMY', .13), ('LED_ENGINE', .20)): fx.mark(n, (0, y, Z))
    fx.focus((0, -.27, Z), .21)


# ------------------------------------------------------------------ 2. PROFILE P1 (Moving Profile)
def profile_p1(fx):
    Z = .44
    fx.pivot('ROOT', None, (0, 0, 0)); fx.pivot('PAN', 'ROOT', (0, 0, .10)); fx.pivot('TILT', 'PAN', (0, 0, Z))
    base_mh(fx, .36, .258, .10, 'PROFILE P1', disp=-.22)
    fx.handle((-.10, -.129, .085), (.10, -.129, .085), (0, -.022, 0), .006)
    yoke_mh(fx, .185, .03, [(-.065, .125), (.065, .125), (.05, Z - .02), (.05, Z + .03), (-.05, Z + .03), (-.05, Z - .02)], Z, .05, (.15, .04), .12, .10, vent=False)
    for sx in (-1, 1):
        fx.cyl(.05, .03, (sx * .185, 0, Z + .03), 'body', axis='X', segs=28, lod=2)
        fx.cyl(.024, .05, (sx * .145, 0, Z), 'steel', axis='X', segs=16)
        fx.box((.003, .012, .22), (sx * .2015, .03, .28), 'accent', bevel=0, lod=0)
    fx.rod((.20, -.03, Z - .07), (.235, -.03, Z - .07), .005, 'steel', lod=0); fx.cyl(.011, .016, (.24, -.03, Z - .07), 'rubber', axis='X', segs=12, lod=0)
    fx.to('HEAD', 'TILT')
    fx.loft([(.22, .09, .10, 3), (.20, .12, .125, 4.5), (-.12, .12, .125, 4.5), (-.20, .112, .115, 4), (-.27, .10, .10, 4)], 'body', loc=(0, 0, Z), segs=32, lod=2)
    fx.loft([(-.03, .128, .133, 4.5), (-.09, .128, .133, 4.5)], 'body2', loc=(0, 0, Z), segs=32)
    for dx, dz, sz in ((0, .139, (.07, .05, .018)), (0, -.139, (.07, .05, .018)), (.134, 0, (.018, .05, .07)), (-.134, 0, (.018, .05, .07))):
        fx.box(sz, (dx, -.06, Z + dz), 'body2', bevel=.004);
    fx.bolts([(sx * .03, -.06, Z + .1485) for sx in (-1, 1)], .0035, 'Z')
    fx.loft([(.07, .1215, .1265, 4.5), (.062, .1215, .1265, 4.5)], 'accent', loc=(0, 0, Z), segs=32, lod=0)
    fx.box((.15, .01, .16), (0, .222, Z), 'body2', bevel=.003)
    fx.handle((-.05, .227, Z + .06), (.05, .227, Z + .06), (0, .03, 0), .006)
    fx.grp('VENTILATION')
    fx.push('Y', (0, .227, Z - .015)); fx.fan(.048); fx.pop()
    for ax, sx in (('X', 1), ('-X', -1)):
        fx.push(ax, (sx * .119, .13, Z)); fx.vent(.10, .11, n=8); fx.pop()
    fx.grp('HEAD')
    fx.push('Z', (0, .12, Z + .1252)); fx.text('CSC', .03, (0, .016, 0)); fx.text('PROFILE P1', .012, (0, -.012, 0)); fx.pop()
    fx.to('OPTICS', 'TILT')
    for dx, dz, sz in ((0, .10, (.212, .012, .012)), (0, -.10, (.212, .012, .012)), (.10, 0, (.012, .012, .212)), (-.10, 0, (.012, .012, .212))):
        fx.box(sz, (dx, -.276, Z + dz), 'body2', bevel=.002)
    fx.tube(.094, .078, .02, (0, -.278, Z), 'body2', axis='-Y', segs=32, lod=2)
    fx.cyl(.094, .004, (0, -.268, Z), 'black', axis='-Y', segs=32)
    fx.lens(.078, .02, (0, -.272, Z), segs=32)
    fx.emit('EMIT_MAIN', (0, -.29, Z))
    for n, y in (('ZOOM_GROUP', -.20), ('FOCUS_GROUP', -.14), ('FRAMING_ROTATION', -.06), ('IRIS', -.01), ('PRISM', .02), ('GOBO_ROT', .05),
                 ('ANIM_WHEEL', .08), ('COLOR_MIX', .12), ('LED_ENGINE', .17)): fx.mark(n, (0, y, Z))
    for i, (dx, dz) in enumerate(((0, .06), (.06, 0), (0, -.06), (-.06, 0))): fx.mark('FRAMING_BLADE_%d' % (i + 1), (dx, -.06, Z + dz))
    fx.focus((0, -.26, Z), .19)


# ------------------------------------------------------------------ 3. RAZOR B1 (Moving Beam)
def razor_b1(fx):
    Z = .33
    fx.pivot('ROOT', None, (0, 0, 0)); fx.pivot('PAN', 'ROOT', (0, 0, .11)); fx.pivot('TILT', 'PAN', (0, 0, Z))
    base_mh(fx, .365, .25, .11, 'RAZOR B1', items=('pcin', 'xlr', 'xlr', 'rj'))
    for sx in (-1, 1): fx.handle((sx * .1825, -.07, .078), (sx * .1825, .07, .078), (sx * .024, 0, 0), .007)
    yoke_mh(fx, .17, .035, [(-.075, .13), (.075, .13), (.07, .27), (.045, Z + .055), (-.045, Z + .055), (-.07, .27)], Z, .05, (.14, .04), .11, .11, vent=False)
    for sx in (-1, 1): fx.cyl(.022, .022, (sx * .145, 0, Z), 'steel', axis='X', segs=16)
    fx.to('HEAD', 'TILT')
    fx.lathe([(0, -.15), (.06, -.148), (.105, -.12), (.132, -.05), (.138, .04), (.128, .11), (.112, .145)], (0, 0, Z), 'body', axis='-Y', segs=36, lod=2)
    fx.tube(.1395, .136, .012, (0, -.03, Z), 'accent', axis='-Y', segs=36, lod=0)
    fx.box((.09, .10, .012), (0, .03, Z + .131), 'body2', bevel=.003)
    fx.bolts([(sx * .035, .03 + sy * .04, Z + .138) for sx in (-1, 1) for sy in (-1, 1)], .0035, 'Z')
    fx.grp('VENTILATION')
    fx.push('Y', (0, .149, Z)); fx.fan(.05); fx.pop()
    for ax, sx in (('X', 1), ('-X', -1)):
        fx.push(ax, (sx * .1335, .06, Z)); fx.vent(.06, .05, n=5); fx.pop()
    for k in range(10):
        a = 2 * pi * k / 10
        fx.rod((.07 * cos(a), .146, Z + .07 * sin(a)), (.115 * cos(a), .112, Z + .115 * sin(a)), .004, 'aludark', segs=6, lod=0)
    fx.to('OPTICS', 'TILT')
    fx.tube(.114, .094, .022, (0, -.152, Z), 'body2', axis='-Y', segs=36, lod=2)
    fx.cyl(.112, .004, (0, -.143, Z), 'black', axis='-Y', segs=36)
    fx.lens(.094, .04, (0, -.15, Z), segs=36)
    fx.emit('EMIT_MAIN', (0, -.19, Z))
    for n, y in (('FOCUS', -.09), ('FROST', -.05), ('PRISM_8F', -.02), ('GOBO_WHEEL', .02), ('COLOR_WHEEL', .05), ('LAMP_SOURCE', .10)): fx.mark(n, (0, y, Z))
    fx.focus((0, -.15, Z), .19)


# ------------------------------------------------------------------ 4. FLASHCORE FX (Hybrid Strobe)
def flashcore_fx(fx):
    Z = .165
    fx.pivot('ROOT', None, (0, 0, 0)); fx.pivot('TILT', 'ROOT', (0, 0, Z))
    fx.to('BASE', 'ROOT')
    fx.box((.39, .15, .055), (0, 0, .0375), 'body', bevel=.008, lod=2)
    for sx in (-1, 1):
        for sy in (-1, 1): fx.cyl(.012, .01, (sx * .165, sy * .05, .005), 'rubber', segs=10)
        fx.box((.024, .11, .15), (sx * .183, 0, .135), 'body', bevel=.006, lod=2)
        fx.cyl(.032, .008, (sx * .198, 0, Z), 'body2', axis='X', segs=20); fx.cyl(.02, .004, (sx * .203, 0, Z), 'accent', axis='X', segs=20, lod=0)
        fx.cyl(.018, .012, (sx * .171, 0, Z), 'steel', axis='X', segs=12)
        fx.tube(.012, .008, .004, (sx * .205, .05, .04), 'steel', axis='Y', segs=12, lod=0)
        fx.mount('MOUNT_OMEGA_%d' % (1 if sx < 0 else 2), (sx * .11, 0, .01))
        fx.cyl(.013, .004, (sx * .11, 0, .008), 'steel', segs=12, lod=0)
    fx.box((.30, .003, .004), (0, -.0755, .018), 'accent', bevel=0, lod=0)
    fx.push('-Y', (0, -.075, .04)); fx.display(.05, .022, (-.12, 0, 0)); fx.text('CSC', .02, (.06, 0, 0)); fx.text('FLASHCORE FX', .008, (.135, 0, 0)); fx.pop()
    fx.grp('CONNECTORS'); fx.push('Y', (0, .075, .0375)); fx.connectors(['pcin', 'pcout', 'xlr', 'xlr'], pitch=.036); fx.pop()
    fx.to('HEAD', 'TILT')
    fx.box((.336, .085, .185), (0, 0, Z), 'body', bevel=.006, lod=2)
    for dz in (-.0225, .0225): fx.box((.33, .008, .006), (0, -.044, Z + dz), 'body2', bevel=0)
    fx.box((.322, .004, .036), (0, -.0435, Z), 'reflector', bevel=0)
    fx.push('Z', (0, 0, Z + .0925)); fx.text('CSC FLASHCORE FX', .012, (0, 0, 0)); fx.pop()
    fx.grp('VENTILATION')
    fx.box((.10, .02, .12), (0, .0525, Z), 'body2', bevel=.003)
    fx.push('Y', (0, .0625, Z)); fx.fan(.042); fx.pop()
    for i in range(13):
        if abs(i - 6) > 2: fx.box((.004, .02, .16), ((i - 6) * .026, .0525, Z), 'aludark', bevel=0, lod=0)
    fx.grp('LED_CELLS')
    for i in range(12):
        fx.cell('STROBE_%02d' % (i + 1), 0); fx.box((.0245, .005, .028), ((i - 5.5) * .0265, -.0455, Z), 'diffuser', bevel=.001); fx.endcell()
    for row, (dz, tag) in enumerate(((.058, 'RGB_T'), (-.058, 'RGB_B'))):
        fx.grp('HEAD'); fx.box((.326, .003, .06), (0, -.0435, Z + dz), 'black', bevel=0)
        for i in range(6):
            cx = (i - 2.5) * .0535
            fx.cell('%s_%02d' % (tag, i + 1), row + 1)
            if fx.lod: fx.box((.049, .003, .056), (cx, -.0445, Z + dz), 'led', bevel=0, lod=2)
            for a in range(4):
                for b in range(4): fx.box((.0085, .003, .0095), (cx + (a - 1.5) * .0118, -.0462, Z + dz + (b - 1.5) * .0135), 'led', bevel=0, lod=0)
            fx.endcell()
    fx.emit('EMIT_STROBE', (0, -.05, Z)); fx.emit('EMIT_RGB_TOP', (0, -.047, Z + .058)); fx.emit('EMIT_RGB_BOTTOM', (0, -.047, Z - .058))
    fx.focus((0, -.045, Z), .2)


# ------------------------------------------------------------------ 5. PIXELBAR B16 (Motorized LED Bar)
def pixelbar_b16(fx):
    Z = .205
    fx.pivot('ROOT', None, (0, 0, 0)); fx.pivot('TILT', 'ROOT', (0, 0, Z))
    fx.to('BASE', 'ROOT')
    fx.box((1.0, .139, .07), (0, 0, .045), 'body', bevel=.008, lod=2)
    for sx in (-1, 1):
        for sy in (-1, 1): fx.cyl(.014, .01, (sx * .44, sy * .045, .005), 'rubber', segs=10)
        fx.box((.03, .11, .20), (sx * .485, 0, .17), 'body', bevel=.008, lod=2)
        fx.cyl(.042, .008, (sx * .503, 0, Z), 'body2', axis='X', segs=24); fx.cyl(.027, .004, (sx * .508, 0, Z), 'accent', axis='X', segs=24, lod=0)
        fx.cyl(.02, .012, (sx * .467, 0, Z), 'steel', axis='X', segs=12)
        fx.box((.10, .06, .006), (sx * .30, 0, .008), 'steel', bevel=.001, lod=0); fx.mount('MOUNT_OMEGA_%d' % (1 if sx < 0 else 2), (sx * .30, 0, .005))
    fx.mount('MOUNT_SAFETY', (0, .05, .01))
    fx.box((.80, .003, .004), (0, -.0705, .022), 'accent', bevel=0, lod=0)
    fx.push('-Y', (0, -.0695, .05)); fx.display(.06, .028, (-.03, 0, 0)); fx.text('CSC', .024, (.20, 0, 0)); fx.text('PIXELBAR B16', .010, (.32, 0, 0)); fx.pop()
    fx.grp('CONNECTORS'); fx.push('Y', (0, .0695, .046)); fx.connectors(['pcin', 'pcout', 'xlr', 'xlr', 'rj', 'rj']); fx.pop()
    fx.grp('VENTILATION')
    for sx in (-1, 1):
        fx.push('Y', (sx * .33, .0695, .046)); fx.vent(.14, .04, n=4); fx.pop()
    fx.to('HEAD', 'TILT')
    fx.prism([(-.055, -.05), (-.055, .05), (-.035, .062), (.04, .062), (.058, .035), (.058, -.035), (.04, -.062), (-.035, -.062)], .93, (0, 0, Z), 'body', axis='X', lod=2, bevel=.003)
    fx.box((.92, .004, .09), (0, -.0565, Z), 'body2', bevel=0)
    fx.box((.92, .003, .003), (0, -.0585, Z + .042), 'accent', bevel=0, lod=0)
    fx.push('Z', (0, 0, Z + .062)); fx.text('CSC PIXELBAR B16', .014, (0, 0, 0)); fx.pop()
    fx.grp('VENTILATION')
    for i in range(30): fx.box((.003, .02, .068), ((i - 14.5) * .03, .067, Z), 'aludark', bevel=0, lod=0)
    for i in range(16):
        x = (i - 7.5) * .0575
        fx.grp('OPTICS'); fx.tube(.0262, .0228, .006, (x, -.0595, Z), 'black', axis='-Y', segs=16, lod=0)
        fx.cell('CELL_%02d' % (i + 1), i); fx.lens(.0228, .009, (x, -.0585, Z), m='led', segs=16); fx.endcell()
        fx.emit('EMIT_%02d' % (i + 1), (x, -.068, Z))
    fx.mark('ZOOM_CARRIAGE', (0, -.03, Z))
    fx.focus((-.33, -.06, Z), .2)


# ------------------------------------------------------------------ 6. PIXELTUBE T1 (RGB LED Tube)
def pixeltube_t1(fx):
    r = .021; zc = .12
    fx.pivot('ROOT', None, (0, 0, 0)); fx.pivot('ROLL', 'ROOT', (0, 0, zc))
    fx.to('SUPPORT', 'ROOT')
    for i, x in enumerate((-.36, .36)):
        fx.box((.09, .13, .006), (x, 0, .003), 'steel', bevel=.002, lod=2)
        fx.rod((x, 0, .006), (x, 0, zc - .026), .008, 'steel', lod=2)
        fx.tube(.028, .0215, .022, (x, 0, zc), 'steel', axis='X', segs=24)
        fx.cyl(.008, .022, (x, .037, zc), 'rubber', axis='Y', segs=10, lod=0)
        fx.mount('MOUNT_HOLDER_%d' % (i + 1), (x, 0, zc - .03))
    fx.to('BODY', 'ROLL')
    n = 14 if fx.lod == 0 else (8 if fx.lod == 1 else 4)
    arc = [(r * cos(-pi / 2 + pi * k / n), r * sin(-pi / 2 + pi * k / n)) for k in range(n + 1)]
    fx.prism(arc, .975, (0, 0, zc), 'aludark', axis='X', lod=2)
    for sx in (-1, 1):
        fx.cyl(.0215, .03, (sx * .5025, 0, zc), 'alu', axis='X', segs=24, lod=2)
        fx.tube(.0222, .0205, .004, (sx * .4885, 0, zc), 'accent', axis='X', segs=24, lod=0)
        fx.rod((sx * .517, 0, zc), (sx * .521, 0, zc), .003, 'steel', segs=6, lod=0)
        fx.tube(.008, .005, .003, (sx * .527, 0, zc), 'steel', axis='Y', segs=12, lod=0)
        fx.mount('MOUNT_EYEBOLT_%s' % ('L' if sx < 0 else 'R'), (sx * .527, 0, zc), 'X' if sx > 0 else '-X')
    fx.box((.15, .006, .02), (-.38, .0205, zc), 'plastic', bevel=.001); fx.box((.05, .002, .012), (-.41, .0243, zc), 'screen', bevel=0)
    for k in range(4): fx.cyl(.0045, .003, (-.365 + k * .014, .0245, zc), 'rubber', axis='Y', segs=8, lod=0)
    fx.push('Y', (.15, .0212, zc)); fx.text('CSC PIXELTUBE T1', .008, (0, 0, 0)); fx.pop()
    fx.grp('CONNECTORS')
    fx.cyl(.007, .006, (.5025, .022, zc), 'black', axis='Y', segs=10); fx.tube(.009, .007, .004, (.5025, .0235, zc), 'steel', axis='Y', segs=10, lod=0)
    fx.cyl(.004, .004, (-.5025, .0225, zc), 'ledgreen', axis='Y', segs=8, lod=0)
    fx.grp('LED_CELLS')
    front = [(-.0205 * cos(-pi / 2 + pi * k / n), .0205 * sin(-pi / 2 + pi * k / n)) for k in range(n + 1)]
    for i in range(16):
        x = (i - 7.5) * .975 / 16
        fx.cell('PIXEL_%02d' % (i + 1), i); fx.prism(front, .975 / 16 - .0006, (x, 0, zc), 'diffuser', axis='X'); fx.endcell()
        fx.emit('EMIT_PX_%02d' % (i + 1), (x, -.021, zc))
    fx.focus((-.42, 0, zc), .12)


# ------------------------------------------------------------------ 7-9. BLINDERS X2 / X4 / X8
def pod(fx, x, z, yf, s, idx):
    fx.grp('HEAD')
    fx.box((s, .045, s), (x, yf + .0805, z), 'body', bevel=.012, lod=2)
    fx.tube(s * .485, s * .44, .062, (x, yf + .03, z), 'body', axis='-Y', segs=28, lod=2)
    fx.grp('OPTICS'); fx.tube(s * .49, s * .455, .006, (x, yf - .001, z), 'accent', axis='-Y', segs=28, lod=0)
    fx.cell('CELL_%02d' % idx, idx)
    fx.lathe([(0, -.056), (.02, -.056), (s * .25, -.036), (s * .38, -.013), (s * .445, 0)], (x, yf + .002, z), 'ledrefl', axis='-Y', segs=28)
    fx.cyl(.016, .004, (x, yf + .055, z), 'chip', axis='-Y', segs=12)
    fx.endcell()
    fx.emit('EMIT_%02d' % idx, (x, yf, z))


def blinder_x2(fx):
    Z = .155; yf = -.065
    fx.pivot('ROOT', None, (0, 0, 0)); fx.pivot('TILT', 'ROOT', (0, 0, Z))
    fx.to('YOKE', 'ROOT')
    fx.box((.469, .07, .006), (0, 0, .003), 'steel', bevel=.001, lod=2)
    for sx in (-1, 1):
        fx.box((.006, .07, .185), (sx * .2315, 0, .0925), 'steel', bevel=.001, lod=2)
        fx.cyl(.024, .016, (sx * .2425, 0, Z), 'plastic', axis='X', segs=8, smooth=False); fx.cyl(.012, .004, (sx * .2515, 0, Z), 'accent', axis='X', segs=12, lod=0)
        fx.cyl(.011, .03, (sx * .2165, 0, Z), 'steel', axis='X', segs=10)
    fx.cyl(.016, .006, (0, 0, .009), 'steel', segs=12, lod=0); fx.mount('MOUNT_CLAMP', (0, 0, 0)); fx.mount('MOUNT_SAFETY', (0, .06, Z))
    fx.to('HEAD', 'TILT')
    for i, x in enumerate((-.105, .105)): pod(fx, x, Z, yf, .20, i + 1)
    fx.grp('HEAD')
    fx.box((.41, .034, .13), (0, .055, Z), 'body2', bevel=.005, lod=2)
    fx.box((.02, .06, .20), (0, -.02, Z), 'body', bevel=.004)
    fx.handle((-.06, .03, Z + .10), (.06, .03, Z + .10), (0, 0, .03), .007)
    fx.box((.36, .003, .004), (0, .0725, Z + .055), 'accent', bevel=0, lod=0)
    fx.push('Y', (0, .072, Z)); fx.display(.045, .022, (-.14, .03, 0), buttons=2); fx.text('CSC BLINDER X2', .010, (.10, .035, 0)); fx.pop()
    fx.grp('CONNECTORS'); fx.push('Y', (.02, .072, Z - .025)); fx.connectors(['pcin', 'pcout', 'xlr', 'xlr'], pitch=.036); fx.pop()
    fx.grp('VENTILATION')
    for sx in (-1, 1):
        for k in range(6): fx.box((.004, .014, .11), (sx * (.115 + k * .016), .079, Z), 'aludark', bevel=0, lod=0)
    fx.focus((-.105, yf, Z), .16)


def blinder_x4(fx):
    Z = .23; yf = -.065
    fx.pivot('ROOT', None, (0, 0, 0)); fx.pivot('TILT', 'ROOT', (0, 0, Z))
    fx.pivot('ROW_1', 'TILT', (0, 0, .33)); fx.pivot('ROW_2', 'TILT', (0, 0, .13))
    fx.to('YOKE', 'ROOT')
    fx.box((.47, .08, .006), (0, 0, .003), 'steel', bevel=.001, lod=2)
    for sx in (-1, 1):
        fx.box((.006, .08, .262), (sx * .232, 0, .131), 'steel', bevel=.001, lod=2)
        fx.rod((sx * .229, 0, .07), (sx * .16, 0, .006), .005, 'steel', lod=0)
        fx.cyl(.03, .018, (sx * .244, 0, Z), 'plastic', axis='X', segs=8, smooth=False); fx.cyl(.015, .004, (sx * .254, 0, Z), 'accent', axis='X', segs=12, lod=0)
        fx.cyl(.012, .026, (sx * .218, 0, Z), 'steel', axis='X', segs=10)
    fx.cyl(.016, .006, (0, 0, .009), 'steel', segs=12, lod=0); fx.mount('MOUNT_CLAMP', (0, 0, 0)); fx.mount('MOUNT_SAFETY', (0, .08, Z))
    fx.to('HEAD', 'TILT')
    for sx in (-1, 1): fx.box((.008, .09, .41), (sx * .206, .01, Z), 'body2', bevel=.002, lod=2)
    fx.rod((-.206, .085, Z), (.206, .085, Z), .012, 'steel', lod=2)
    for sx in (-1, 1): fx.box((.008, .05, .03), (sx * .206, .065, Z), 'body2', bevel=.002)
    fx.box((.17, .05, .15), (0, .105, Z), 'body2', bevel=.006, lod=2)
    fx.box((.15, .003, .004), (0, .1305, Z + .062), 'accent', bevel=0, lod=0)
    fx.push('Y', (0, .13, Z)); fx.display(.04, .02, (-.035, .04, 0), buttons=2); fx.text('CSC BLINDER X4', .008, (.0, .018, 0)); fx.pop()
    fx.grp('CONNECTORS'); fx.push('Y', (0, .13, Z - .035)); fx.connectors(['pcin', 'pcout', 'xlr', 'xlr'], pitch=.036); fx.pop()
    fx.grp('VENTILATION')
    for sx in (-1, 1):
        for k in range(4): fx.box((.004, .04, .13), (sx * (.089 + k * .012), .105, Z), 'aludark', bevel=0, lod=0)
    idx = 1
    for row, zz in (('ROW_1', .33), ('ROW_2', .13)):
        fx.to('HEAD', row)
        for x in (-.10, .10):
            pod(fx, x, zz, yf, .19, idx); idx += 1
        fx.grp('HEAD')
        fx.box((.39, .02, .09), (0, .048, zz), 'body2', bevel=.004, lod=2)
        for sx in (-1, 1):
            fx.cyl(.009, .016, (sx * .199, .02, zz), 'steel', axis='X', segs=10)
            fx.cyl(.015, .01, (sx * .215, .02, zz), 'plastic', axis='X', segs=8, lod=0, smooth=False)
    fx.focus((-.10, yf, .33), .16)


def blinder_x8(fx):
    Z = .26; yf = -.07
    fx.pivot('ROOT', None, (0, 0, 0)); fx.pivot('TILT', 'ROOT', (0, 0, Z))
    fx.to('YOKE', 'ROOT')
    fx.box((.976, .10, .008), (0, 0, .012), 'steel', bevel=.001, lod=2)
    for sx in (-1, 1):
        fx.box((.008, .10, .30), (sx * .484, 0, .158), 'steel', bevel=.001, lod=2)
        fx.box((.06, .14, .008), (sx * .40, 0, .004), 'rubber', bevel=.002)
        fx.rod((sx * .48, .03, .11), (sx * .38, .03, .016), .006, 'steel', lod=0); fx.rod((sx * .48, -.03, .11), (sx * .38, -.03, .016), .006, 'steel', lod=0)
        fx.cyl(.036, .02, (sx * .498, 0, Z), 'plastic', axis='X', segs=8, smooth=False); fx.cyl(.018, .004, (sx * .509, 0, Z), 'accent', axis='X', segs=12, lod=0)
        fx.cyl(.014, .02, (sx * .474, 0, Z), 'steel', axis='X', segs=10)
    fx.rod((-.484, .04, .05), (.484, .04, .05), .008, 'steel', lod=1)
    for i, x in enumerate((-.25, .25)):
        fx.cyl(.016, .006, (x, 0, .019), 'steel', segs=12, lod=0); fx.mount('MOUNT_CLAMP_%d' % (i + 1), (x, 0, .008))
    fx.mount('MOUNT_SAFETY', (0, .10, Z))
    fx.to('HEAD', 'TILT')
    idx = 1
    for zz in (.37, .15):
        for x in (-.33, -.11, .11, .33):
            pod(fx, x, zz, yf, .20, idx); idx += 1
    fx.grp('HEAD')
    fx.box((.90, .028, .44), (0, .047, Z), 'body2', bevel=.004, lod=2)
    fx.box((.02, .11, .452), (0, -.012, Z), 'body', bevel=.003); fx.box((.90, .11, .02), (0, -.012, Z), 'body', bevel=.003)
    for dz in (-.226, .226): fx.box((.94, .12, .012), (0, -.008, Z + dz), 'body2', bevel=.003, lod=2)
    for sx in (-1, 1): fx.box((.012, .12, .464), (sx * .464, -.008, Z), 'body2', bevel=.003, lod=2)
    fx.box((.86, .003, .004), (0, -.0695, Z + .226), 'accent', bevel=0, lod=0)
    for x0 in (-.30, .18): fx.handle((x0, 0, Z + .232), (x0 + .12, 0, Z + .232), (0, 0, .035), .008)
    fx.box((.36, .06, .17), (0, .091, Z), 'body2', bevel=.006, lod=2)
    fx.push('Z', (0, .091, Z + .085)); fx.display(.05, .024, (-.08, 0, 0), buttons=4); fx.text('CSC BLINDER X8', .010, (.09, 0, 0)); fx.pop()
    fx.grp('CONNECTORS'); fx.push('Y', (0, .121, Z)); fx.connectors(['pcin', 'pcout', 'xlr', 'xlr'], pitch=.034); fx.pop()
    fx.grp('VENTILATION')
    for sx in (-1, 1):
        fx.push('Y', (sx * .125, .121, Z)); fx.fan(.04); fx.pop()
        for k in range(12): fx.box((.003, .034, .40), (sx * (.20 + k * .021), .078, Z), 'aludark', bevel=0, lod=0)
    fx.focus((-.22, yf, .37), .25)


# ------------------------------------------------------------------ 10. FOLLOWSPOT F1
def followspot_f1(fx):
    Z = 1.46
    fx.pivot('ROOT', None, (0, 0, 0)); fx.pivot('PAN', 'ROOT', (0, 0, 1.22)); fx.pivot('TILT', 'PAN', (0, 0, Z))
    fx.to('STAND', 'ROOT')
    fx.rod((0, 0, .30), (0, 0, 1.20), .028, 'steel', segs=16, lod=2)
    fx.cyl(.042, .34, (0, 0, .50), 'body2', segs=16, lod=2); fx.cyl(.052, .05, (0, 0, .64), 'body2', segs=16); fx.cyl(.05, .05, (0, 0, .34), 'body2', segs=16)
    for k in range(3):
        a = pi / 2 + 2 * pi * k / 3; f = (.56 * cos(a), .56 * sin(a), .03)
        fx.rod((0, 0, .64), f, .017, 'body2', segs=10, lod=2)
        fx.rod((0, 0, .34), (f[0] * .5, f[1] * .5, .335), .009, 'steel', segs=8)
        fx.cyl(.032, .03, (f[0], f[1], .015), 'rubber', segs=12)
    fx.cyl(.06, .03, (0, 0, 1.205), 'body2', segs=20, lod=2)
    fx.rod((.02, 0, .70), (.09, 0, .70), .006, 'steel', lod=0); fx.cyl(.014, .02, (.095, 0, .70), 'rubber', axis='X', segs=10, lod=0)
    fx.mount('MOUNT_STAND_SPIGOT', (0, 0, 1.22))
    fx.to('YOKE', 'PAN')
    fx.cyl(.07, .02, (0, 0, 1.23), 'body2', segs=24, lod=2); fx.cyl(.072, .004, (0, 0, 1.236), 'accent', segs=24, lod=0)
    fx.box((.40, .12, .03), (0, 0, 1.255), 'body', bevel=.008, lod=2)
    for sx in (-1, 1):
        fx.box((.022, .12, .275), (sx * .189, 0, 1.3775), 'body', bevel=.006, lod=2)
        fx.cyl(.042, .02, (sx * .21, 0, Z), 'plastic', axis='X', segs=8, smooth=False); fx.cyl(.02, .004, (sx * .221, 0, Z), 'accent', axis='X', segs=12, lod=0)
        fx.cyl(.024, .032, (sx * .164, 0, Z), 'steel', axis='X', segs=12)
    fx.to('HEAD', 'TILT')
    fx.loft([(.62, .13, .14, 4), (.60, .15, .16, 5), (.16, .15, .16, 5), (.13, .128, .138, 5), (-.36, .128, .138, 5), (-.385, .11, .12, 3)], 'body', loc=(0, 0, Z), segs=32, lod=2)
    fx.loft([(.15, .1525, .1625, 5), (.135, .1525, .1625, 5)], 'accent', loc=(0, 0, Z), segs=32, lod=0)
    fx.box((.31, .07, .34), (0, -.33, Z), 'body2', bevel=.008, lod=2)
    for i in range(6):
        x = (i - 2.5) * .042
        fx.rod((x, -.33, Z + .17), (x, -.35, Z + .215), .004, 'steel', segs=6, lod=0); fx.cyl(.009, .012, (x, -.352, Z + .219), 'accent' if i == 0 else 'plastic', segs=8, lod=0)
        fx.mark('COLOR_FRAME_%d' % (i + 1), (x, -.33, Z))
    for nm, y in (('IRIS', -.06), ('DOUSER', -.20)):
        fx.box((.004, .12, .03), (.13, y, Z + .05), 'steel', bevel=0, lod=0)
        fx.rod((.13, y, Z + .05), (.19, y, Z + .07), .005, 'steel', segs=6); fx.cyl(.013, .022, (.196, y, Z + .072), 'rubber', axis='X', segs=10)
        fx.push('X', (.1285, y, Z + .085)); fx.text(nm, .012, (0, 0, 0)); fx.pop(); fx.mark(nm, (0, y, Z))
    for sx in (-1, 1): fx.handle((sx * .105, .62, Z - .07), (sx * .105, .62, Z + .07), (0, .075, 0), .011, 'body2')
    fx.handle((-.128, -.24, Z - .06), (-.128, -.24, Z + .06), (-.06, 0, 0), .010, 'body2')
    fx.handle((0, -.10, Z + .138), (0, .08, Z + .138), (0, 0, .05), .010, 'body2')
    fx.box((.03, .10, .03), (-.075, .30, Z + .174), 'plastic', bevel=.003, lod=0); fx.tube(.012, .009, .02, (-.075, .24, Z + .178), 'steel', axis='Y', segs=10, lod=0)
    fx.push('-X', (-.128, -.10, Z + .0)); fx.text('CSC', .05, (0, .03, 0)); fx.text('FOLLOWSPOT F1', .017, (0, -.02, 0)); fx.pop()
    fx.push('Z', (0, .53, Z + .16)); fx.display(.07, .04, (-.02, 0, 0)); fx.pop()
    fx.grp('CONNECTORS'); fx.push('-Z', (0, .47, Z - .16)); fx.connectors(['pcin', 'xlr', 'xlr', 'rj']); fx.pop()
    fx.grp('VENTILATION')
    for ax, sx in (('X', 1), ('-X', -1)):
        fx.push(ax, (sx * .1495, .38, Z)); fx.vent(.30, .19, n=10); fx.pop()
    fx.push('Z', (0, .33, Z + .1595)); fx.vent(.16, .22, n=9); fx.pop()
    fx.push('Y', (0, .62, Z)); fx.fan(.08); fx.pop()
    fx.to('OPTICS', 'TILT')
    fx.lathe([(.105, 0), (.118, .02), (.118, .30), (.135, .32), (.135, .40), (.126, .41), (.118, .40)], (0, -.38, Z), 'body2', axis='-Y', segs=36, lod=2)
    fx.tube(.123, .117, .045, (0, -.47, Z), 'rubber', axis='-Y', segs=36); fx.tube(.123, .117, .045, (0, -.60, Z), 'rubber', axis='-Y', segs=36)
    fx.tube(.121, .117, .006, (0, -.535, Z), 'accent', axis='-Y', segs=36, lod=0)
    fx.push('X', (.123, -.47, Z)); fx.text('FOCUS', .01, (0, 0, 0)); fx.pop(); fx.push('X', (.123, -.60, Z)); fx.text('ZOOM', .01, (0, 0, 0)); fx.pop()
    fx.lens(.118, .03, (0, -.765, Z), segs=36)
    fx.emit('EMIT_MAIN', (0, -.80, Z))
    for n, y in (('ZOOM', -.60), ('FOCUS', -.47), ('SOURCE', .40)): fx.mark(n, (0, y, Z))
    fx.focus((0, -.70, Z), .32)


# ------------------------------------------------------------------ 11. PIXELWASH W37
def pixelwash_w37(fx):
    Z = .43; p = .056
    fx.pivot('ROOT', None, (0, 0, 0)); fx.pivot('PAN', 'ROOT', (0, 0, .11)); fx.pivot('TILT', 'PAN', (0, 0, Z))
    base_mh(fx, .40, .257, .11, 'PIXELWASH W37')
    for sy in (-1, 1): fx.handle((-.09, sy * .1285, .092), (.09, sy * .1285, .092), (0, sy * .024, 0), .007)
    yoke_mh(fx, .235, .04, [(-.085, .13), (.085, .13), (.075, Z - .03), (.055, Z + .07), (-.055, Z + .07), (-.075, Z - .03)], Z, .06, (.17, .045), .13, .11)
    for sx in (-1, 1): fx.cyl(.028, .014, (sx * .21, 0, Z), 'steel', axis='X', segs=16)
    fx.to('HEAD', 'TILT')
    fx.lathe([(0, -.115), (.10, -.115), (.175, -.085), (.205, -.02), (.205, .055), (.198, .07)], (0, 0, Z), 'body', axis='-Y', segs=40, lod=2)
    fx.cyl(.198, .006, (0, -.068, Z), 'body2', axis='-Y', segs=40)
    fx.tube(.2065, .203, .010, (0, -.045, Z), 'accent', axis='-Y', segs=40, lod=0)
    fx.push('Y', (0, .1152, Z + .08)); fx.text('CSC W37', .013, (0, 0, 0)); fx.pop()
    fx.grp('VENTILATION')
    fx.push('Y', (0, .115, Z)); fx.fan(.058); fx.pop()
    for k in range(12):
        a = 2 * pi * (k + .5) / 12
        fx.rod((.105 * cos(a), .114, Z + .105 * sin(a)), (.17 * cos(a), .088, Z + .17 * sin(a)), .004, 'aludark', segs=6, lod=0)
    i = 0
    for q in range(-3, 4):
        for r in range(max(-3, -q - 3), min(3, -q + 3) + 1):
            i += 1; x = p * (q + r / 2); zz = Z + p * sqrt(3) / 2 * r; ring = max(abs(q), abs(r), abs(q + r))
            fx.grp('OPTICS'); fx.tube(.0272, .0246, .004, (x, -.072, zz), 'black', axis='-Y', segs=14, lod=0)
            fx.cell('CELL_%02d' % i, ring); fx.lens(.0246, .009, (x, -.071, zz), m='led', segs=14); fx.endcell()
            fx.emit('EMIT_%02d' % i, (x, -.081, zz))
    fx.mark('ZOOM_CARRIAGE', (0, -.03, Z))
    fx.focus((0, -.07, Z), .25)


# ------------------------------------------------------------------ 12. LASERCORE L1
def lasercore_l1(fx):
    Z = .165
    fx.pivot('ROOT', None, (0, 0, 0)); fx.pivot('TILT', 'ROOT', (0, 0, Z))
    fx.to('YOKE', 'ROOT')
    fx.box((.40, .08, .006), (0, 0, .011), 'steel', bevel=.001, lod=2)
    for sx in (-1, 1):
        fx.box((.006, .08, .21), (sx * .197, 0, .113), 'steel', bevel=.001, lod=2)
        fx.box((.05, .10, .008), (sx * .15, 0, .004), 'rubber', bevel=.002)
        fx.cyl(.026, .016, (sx * .208, 0, Z), 'plastic', axis='X', segs=8, smooth=False); fx.cyl(.013, .004, (sx * .217, 0, Z), 'accent', axis='X', segs=12, lod=0)
        fx.cyl(.012, .026, (sx * .182, 0, Z), 'steel', axis='X', segs=10)
    fx.cyl(.016, .006, (0, 0, .017), 'steel', segs=12, lod=0); fx.mount('MOUNT_CLAMP', (0, 0, .008)); fx.mount('MOUNT_SAFETY', (0, .17, Z + .08))
    fx.to('HEAD', 'TILT')
    fx.box((.34, .353, .168), (0, 0, Z), 'body2', bevel=.008, lod=2)
    for sx in (-1, 1):
        for k in range(5): fx.box((.004, .33, .008), (sx * .171, 0, Z + (k - 2) * .03), 'aludark', bevel=0, lod=0)
    fx.box((.26, .25, .004), (0, .02, Z + .086), 'aludark', bevel=.001)
    fx.bolts([(sx * .12, .02 + sy * .115, Z + .089) for sx in (-1, 0, 1) for sy in (-1, 1)], .004, 'Z')
    fx.push('Z', (0, .02, Z + .088)); fx.text('CSC', .04, (0, .03, 0)); fx.text('LASERCORE L1', .015, (0, -.02, 0)); fx.pop()
    fx.tube(.011, .007, .004, (.15, .18, Z + .06), 'steel', axis='X', segs=12, lod=0)
    for sx in (-1, 1): fx.handle((sx * .15, -.1765, Z - .045), (sx * .15, -.1765, Z + .045), (0, -.03, 0), .006)
    fx.box((.30, .003, .004), (0, -.1775, Z - .07), 'accent', bevel=0, lod=0)
    fx.push('-Y', (0, -.1765, Z)); fx.text('LASER APERTURE', .007, (-.05, -.036, 0)); fx.text('CSC', .026, (.085, .0, 0)); fx.pop()
    fx.cyl(.006, .005, (.06, -.178, Z + .045), 'ledred', axis='-Y', segs=10); fx.cyl(.006, .005, (.085, -.178, Z + .045), 'ledgreen', axis='-Y', segs=10)
    fx.to('OPTICS', 'TILT')
    fx.box((.13, .008, .062), (-.05, -.1785, Z + .01), 'aludark', bevel=.002, lod=2)
    fx.box((.112, .004, .044), (-.05, -.1815, Z + .01), 'lens', bevel=0)
    fx.bolts([(-.05 + sx * .06, -.183, Z + .01 + sz * .026) for sx in (-1, 1) for sz in (-1, 1)], .003, '-Y')
    fx.emit('EMIT_APERTURE', (-.05, -.184, Z + .01))
    for k in range(5): fx.emit('EMIT_BEAM_%d' % (k + 1), (-.05 + (k - 2) * .02, -.184, Z + .01))
    for n, pz in (('GALVO_SCANNER', (-.05, -.13, Z + .01)), ('SAFETY_SHUTTER', (-.05, -.16, Z + .01)), ('SOURCE_R', (-.08, .08, Z)), ('SOURCE_G', (-.02, .08, Z)), ('SOURCE_B', (.04, .08, Z))): fx.mark(n, pz)
    fx.grp('CONNECTORS'); fx.push('Y', (0, .1765, Z - .045)); fx.connectors(['pcin', 'rj', 'rj', 'xlr', 'xlr', 'estop', 'key'], pitch=.036); fx.pop()
    fx.to('HEAD', 'TILT'); fx.push('Y', (0, .1765, Z + .035)); fx.display(.05, .03, (-.012, 0, 0), buttons=2); fx.pop()
    fx.grp('VENTILATION')
    for sx in (-1, 1):
        fx.push('Y', (sx * .11, .1765, Z + .03)); fx.fan(.036); fx.pop()
    fx.focus((-.03, -.18, Z), .15)


# ------------------------------------------------------------------ 13. FLOODLIGHT F8
def floodlight_f8(fx):
    Z = .215
    fx.pivot('ROOT', None, (0, 0, 0)); fx.pivot('TILT', 'ROOT', (0, 0, Z))
    fx.to('YOKE', 'ROOT')
    fx.box((.74, .10, .008), (0, 0, .012), 'steel', bevel=.001, lod=2)
    for sx in (-1, 1):
        fx.box((.008, .10, .255), (sx * .366, 0, .1355), 'steel', bevel=.001, lod=2)
        fx.box((.06, .14, .008), (sx * .30, 0, .004), 'rubber', bevel=.002)
        fx.cyl(.05, .005, (sx * .3725, 0, Z), 'steel', axis='X', segs=24); fx.cyl(.052, .002, (sx * .374, 0, Z), 'accent', axis='X', segs=24, lod=0)
        fx.cyl(.028, .018, (sx * .384, 0, Z), 'plastic', axis='X', segs=8, smooth=False)
        fx.cyl(.014, .018, (sx * .356, 0, Z), 'steel', axis='X', segs=10)
    for i, x in enumerate((-.20, .20)):
        fx.cyl(.016, .006, (x, 0, .019), 'steel', segs=12, lod=0); fx.mount('MOUNT_CLAMP_%d' % (i + 1), (x, 0, .008))
    fx.mount('MOUNT_SAFETY', (0, .05, Z + .16))
    fx.to('HEAD', 'TILT')
    fx.box((.70, .11, .335), (0, 0, Z), 'body', bevel=.010, lod=2)
    fx.box((.66, .004, .005), (0, -.057, Z), 'accent', bevel=0, lod=0)
    fx.handle((-.12, 0, Z + .1675), (.12, 0, Z + .1675), (0, 0, .034), .009, 'body2')
    fx.push('Z', (.23, 0, Z + .1675)); fx.text('CSC FLOODLIGHT F8', .014, (0, 0, 0)); fx.pop()
    fx.box((.18, .06, .21), (0, .085, Z), 'body2', bevel=.006, lod=2)
    fx.push('Y', (0, .115, Z + .05)); fx.display(.05, .026, (-.012, 0, 0), buttons=2); fx.pop()
    fx.cyl(.008, .012, (.06, .085, Z - .11), 'steel', segs=10, lod=0)
    fx.grp('CONNECTORS'); fx.push('Y', (0, .115, Z - .045)); fx.connectors(['pcin', 'pcout', 'xlr', 'xlr'], pitch=.036); fx.pop()
    fx.grp('VENTILATION')
    for i in range(33):
        x = (i - 16) * .021
        if abs(x) > .095: fx.box((.004, .055, .31), (x, .0825, Z), 'aludark', bevel=0, lod=0)
    idx = 0
    for dz in (.082, -.082):
        for ix in range(4):
            idx += 1; x = (ix - 1.5) * .168; z = Z + dz
            fx.grp('HEAD'); fx.box((.155, .006, .148), (x, -.057, z), 'body2', bevel=.002)
            fx.bolts([(x + sx * .07, -.061, z + sz * .066) for sx in (-1, 1) for sz in (-1, 1)], .003, '-Y')
            for a in (-1, 0, 1):
                for b in (-.5, .5):
                    fx.grp('OPTICS'); fx.tube(.024, .0212, .004, (x + a * .047, -.0615, z + b * .064), 'reflector', axis='-Y', segs=14, lod=0)
                    fx.cellkey = ('LED_CELLS', 'TILT', 'CELL_%02d' % idx, idx)
                    fx.lens(.0212, .008, (x + a * .047, -.0605, z + b * .064), m='led', segs=14); fx.endcell()
            fx.ncells += 1
            fx.emit('EMIT_%02d' % idx, (x, -.07, z))
    for i, (sx, sz) in enumerate(((-1, 1), (1, 1), (1, -1), (-1, -1))): fx.mark('BARNDOOR_MOUNT_%d' % (i + 1), (sx * .34, -.055, Z + sz * .16))
    fx.focus((-.17, -.06, Z + .05), .26)


# ================================================================== 1.2.0 : refonte des quatre projecteurs historiques
# Meme langage de forme que les 13 nouveaux appareils. Les hauteurs d'axe TILT (0.361 / 0.322 / 0.396 / 0.222) et l'ordre des
# cellules sont ceux des anciens modeles : le moteur lumiere du mod (pivots, visee, zones DMX) reste valable sans changement.

# ------------------------------------------------------------------ 14. VORTEX X1 (Beam / Spot / Wash hybride)
def vortex_x1(fx):
    Z = .361
    fx.pivot('ROOT', None, (0, 0, 0)); fx.pivot('PAN', 'ROOT', (0, 0, .115)); fx.pivot('TILT', 'PAN', (0, 0, Z))
    base_mh(fx, .36, .262, .115, 'VORTEX X1')
    for sx in (-1, 1): fx.handle((sx * .18, -.075, .082), (sx * .18, .075, .082), (sx * .026, 0, 0), .007)
    yoke_mh(fx, .178, .036, [(-.08, .135), (.08, .135), (.068, Z - .03), (.048, Z + .062), (-.048, Z + .062), (-.068, Z - .03)], Z, .052, (.16, .042), .115, .115, vent=False)
    for sx in (-1, 1):
        fx.cyl(.028, .036, (sx * .144, 0, Z), 'steel', axis='X', segs=16)
        fx.box((.003, .012, .15), (sx * .1975, .028, .245), 'accent', bevel=0, lod=0)
    fx.to('HEAD', 'TILT')
    # corps long et fusele : nez conique vers la grande lentille, ventre carre arrondi, capot arriere bombe
    fx.loft([(.214, .072, .072, 2.3), (.196, .108, .108, 3), (.12, .126, .126, 4), (-.05, .126, .126, 4), (-.12, .116, .116, 3.2),
             (-.19, .102, .102, 2.4), (-.222, .094, .094, 2.1)], 'body', loc=(0, 0, Z), segs=36, lod=2)
    fx.loft([(.105, .1295, .1295, 4), (.015, .1295, .1295, 4)], 'body2', loc=(0, 0, Z), segs=36)
    fx.loft([(-.046, .1285, .1285, 4), (-.056, .1285, .1285, 4)], 'accent', loc=(0, 0, Z), segs=36, lod=0)
    for sx in (-1, 1):
        fx.box((.006, .085, .012), (sx * .121, -.125, Z + .03), 'body2', bevel=.002, lod=0)
        fx.box((.006, .085, .012), (sx * .121, -.125, Z - .03), 'body2', bevel=.002, lod=0)
    fx.box((.07, .05, .010), (0, -.12, Z + .1165), 'body2', bevel=.003)
    fx.bolts([(sx * .026, -.12 + sy * .017, Z + .1225) for sx in (-1, 1) for sy in (-1, 1)], .003, 'Z')
    fx.grp('VENTILATION')
    for ax, sx in (('X', 1), ('-X', -1)):
        fx.push(ax, (sx * .1295, .06, Z)); fx.vent(.075, .13, n=9); fx.pop()
    fx.push('Z', (0, .15, Z + .119)); fx.vent(.10, .05, n=4); fx.pop()
    fx.push('Y', (0, .214, Z)); fx.fan(.05); fx.pop()
    for k in range(10):
        a = 2 * pi * (k + .5) / 10
        fx.rod((.064 * cos(a), .212, Z + .064 * sin(a)), (.103 * cos(a), .196, Z + .103 * sin(a)), .004, 'aludark', segs=6, lod=0)
    fx.grp('HEAD')
    fx.push('Z', (0, .06, Z + .1297)); fx.text('CSC', .03, (0, .014, 0)); fx.text('VORTEX X1', .0115, (0, -.014, 0)); fx.pop()
    fx.to('OPTICS', 'TILT')
    fx.tube(.098, .076, .026, (0, -.233, Z), 'body2', axis='-Y', segs=36, lod=2)
    fx.tube(.1005, .0955, .005, (0, -.2435, Z), 'accent', axis='-Y', segs=36, lod=0)
    fx.lathe([(.076, 0), (.071, -.028)], (0, -.246, Z), 'black', axis='-Y', segs=36, lod=2)
    fx.lens(.071, .022, (0, -.218, Z), segs=36, lod=2)
    fx.emit('EMIT_MAIN', (0, -.246, Z))
    for n, y in (('ZOOM_GROUP', -.17), ('FOCUS_GROUP', -.11), ('PRISM_3F', -.06), ('IRIS', -.02), ('GOBO_ROT', .01), ('GOBO_STATIC', .04),
                 ('COLOR_MIX_RGB', .08), ('HYBRID_SOURCE', .15)): fx.mark(n, (0, y, Z))
    fx.focus((0, -.21, Z), .17)


# ------------------------------------------------------------------ 15-16. LUMORA W7 / W19 (Wash a lentilles en couronnes)
_ORDER_W7 = [(-0.034, 0.116), (0.032, 0.116), (-0.087, 0.086), (0.085, 0.087), (-0.034, 0.054), (0.033, 0.056), (0.115, 0.035),
             (-0.116, 0.034), (0.063, 0.0), (-0.062, 0.001), (-0.115, -0.034), (0.115, -0.033), (0.033, -0.054), (-0.033, -0.054),
             (-0.086, -0.088), (0.086, -0.085), (-0.034, -0.115), (0.034, -0.114)]
_ORDER_W19 = [(-0.03, 0.158), (0.029, 0.158), (-0.082, 0.139), (0.081, 0.141), (-0.123, 0.103), (0.123, 0.105), (0.029, 0.105),
              (-0.03, 0.104), (-0.077, 0.077), (0.077, 0.079), (0.152, 0.058), (-0.15, 0.056), (-0.03, 0.05), (0.031, 0.05),
              (0.105, 0.032), (-0.104, 0.03), (-0.055, 0.0), (0.057, 0.0), (-0.16, -0.0), (0.161, 0.002), (0.105, -0.029),
              (-0.103, -0.031), (0.031, -0.049), (-0.028, -0.049), (-0.149, -0.056), (0.151, -0.055), (-0.077, -0.078),
              (0.078, -0.077), (-0.122, -0.105), (0.125, -0.103), (-0.029, -0.104), (0.032, -0.103), (-0.08, -0.14), (0.084, -0.139),
              (-0.028, -0.158), (0.032, -0.158)]


def lumora(fx, tag, Z, R, base, yoke, prof, yf, rings, lr, cr, order, hubs):
    """Wash : couronnes concentriques de lentilles + grande lentille centrale (effet fleur), numerotees comme l'ancien modele."""
    zb = base[2]
    fx.pivot('ROOT', None, (0, 0, 0)); fx.pivot('PAN', 'ROOT', (0, 0, zb)); fx.pivot('TILT', 'PAN', (0, 0, Z))
    base_mh(fx, base[0], base[1], zb, 'LUMORA ' + tag)
    for sy in (-1, 1): fx.handle((-.08, sy * base[1] / 2, zb - .018), (.08, sy * base[1] / 2, zb - .018), (0, sy * .022, 0), .007)
    half, th, wy, hub, turn = yoke
    yoke_mh(fx, half, th, [(-wy, zb + .02), (wy, zb + .02), (wy * .88, Z - .03), (wy * .62, Z + hub + .008), (-wy * .62, Z + hub + .008), (-wy * .88, Z - .03)],
            Z, hub, (wy * 2, .036), turn, zb)
    for sx in (-1, 1): fx.cyl(.026, half - th / 2 - R + .012, (sx * (R + half - th / 2) / 2, 0, Z), 'steel', axis='X', segs=16)
    fx.to('HEAD', 'TILT')
    fx.lathe(prof, (0, 0, Z), 'body', axis='-Y', segs=40, lod=2)
    fx.cyl(prof[-1][0], .006, (0, yf + .003, Z), 'body2', axis='-Y', segs=40, lod=2)
    fx.tube(R + .0015, R - .002, .010, (0, -.02, Z), 'accent', axis='-Y', segs=40, lod=0)
    yr = -prof[0][1]
    fx.push('Y', (0, yr + .0002, Z + hubs * 1.45)); fx.text('CSC ' + tag, .013, (0, 0, 0)); fx.pop()
    fx.grp('VENTILATION')
    fx.push('Y', (0, yr, Z)); fx.fan(hubs); fx.pop()
    r0 = prof[1][0]; r1 = prof[2][0]; y1 = -prof[2][1]
    for k in range(12):
        a = 2 * pi * (k + .5) / 12
        fx.rod((r0 * cos(a), yr - .001, Z + r0 * sin(a)), ((r1 - .006) * cos(a), y1 + .004, Z + (r1 - .006) * sin(a)), .004, 'aludark', segs=6, lod=0)
    for ax, sx in (('X', 1), ('-X', -1)):
        fx.push(ax, (sx * (R + .0005), .035, Z)); fx.vent(.045, R * .5, n=6); fx.pop()
    pts = []
    for ri, (rr, n, ph) in enumerate(rings):
        for k in range(n):
            a = radians(ph) + 2 * pi * k / n
            pts.append((rr * cos(a), rr * sin(a), ri + 1))
    used = set(); seq = []
    for ox, oz in order:
        j = min((q for q in range(len(pts)) if q not in used), key=lambda q: (pts[q][0] - ox) ** 2 + (pts[q][1] - oz) ** 2)
        used.add(j); seq.append(pts[j])
    seq.append((0, 0, 0))
    for i, (x, dz, ring) in enumerate(seq):
        r = cr if ring == 0 else lr; zz = Z + dz
        fx.grp('OPTICS'); fx.tube(r + .0028, r, .004, (x, yf - .001, zz), 'aludark' if ring == 0 else 'black', axis='-Y', segs=20 if ring == 0 else 14, lod=0)
        fx.cell('CELL_%02d' % (i + 1), ring); fx.lens(r, .011 if ring == 0 else .009, (x, yf, zz), m='led', segs=20 if ring == 0 else 14, lod=2); fx.endcell()
        fx.emit('EMIT_%02d' % (i + 1), (x, yf - .01, zz))
    fx.mark('ZOOM_CARRIAGE', (0, yf + .04, Z)); fx.mark('FLOWER_EFFECT', (0, yf + .02, Z))
    fx.focus((0, yf, Z), R * 1.25)


def lumora_w7(fx):
    Z = .322; R = .156
    lumora(fx, 'W7', Z, R, (.34, .25, .105), (.183, .032, .075, .05, .115),
           [(0, -.118), (.075, -.118), (.125, -.098), (.15, -.05), (R, 0), (R, .07), (.15, .082)], -.083,
           [(.064, 6, 0), (.121, 12, 15)], .027, .033, _ORDER_W7, .052)


def lumora_w19(fx):
    Z = .396; R = .197
    lumora(fx, 'W19', Z, R, (.40, .27, .112), (.226, .04, .09, .06, .13),
           [(0, -.125), (.10, -.125), (.165, -.098), (.192, -.04), (R, .01), (R, .075), (.191, .088)], -.089,
           [(.057, 6, 0), (.109, 12, 15), (.161, 18, 0)], .0235, .0285, _ORDER_W19, .058)


# ------------------------------------------------------------------ 17. PULSESTRIKE M (Strobe / Blinder motorise)
def pulsestrike_m(fx):
    Z = .222; yf = -.0525
    fx.pivot('ROOT', None, (0, 0, 0)); fx.pivot('TILT', 'ROOT', (0, 0, Z))
    fx.to('BASE', 'ROOT')
    fx.box((.47, .16, .055), (0, 0, .0375), 'body', bevel=.008, lod=2)
    for sx in (-1, 1):
        for sy in (-1, 1): fx.cyl(.013, .01, (sx * .20, sy * .055, .005), 'rubber', segs=10)
        fx.box((.026, .12, .205), (sx * .2135, 0, .1575), 'body', bevel=.007, lod=2)
        fx.cyl(.038, .008, (sx * .2305, 0, Z), 'body2', axis='X', segs=24); fx.cyl(.024, .004, (sx * .2355, 0, Z), 'accent', axis='X', segs=24, lod=0)
        fx.cyl(.02, .012, (sx * .1975, 0, Z), 'steel', axis='X', segs=12)
        fx.box((.004, .08, .09), (sx * .2275, 0, .12), 'body2', bevel=.002, lod=0)
        fx.bolts([(sx * .2295, yy * .03, .12 + zz * .035) for yy in (-1, 1) for zz in (-1, 1)], .0035, 'X')
        fx.mount('MOUNT_OMEGA_%d' % (1 if sx < 0 else 2), (sx * .13, 0, .01))
        fx.cyl(.013, .004, (sx * .13, 0, .008), 'steel', segs=12, lod=0)
    fx.mount('MOUNT_SAFETY', (0, .06, .02))
    fx.box((.36, .003, .004), (0, -.0805, .02), 'accent', bevel=0, lod=0)
    fx.push('-Y', (0, -.08, .04)); fx.display(.05, .022, (-.14, 0, 0)); fx.text('CSC', .02, (.05, 0, 0)); fx.text('PULSESTRIKE M', .008, (.135, 0, 0)); fx.pop()
    fx.grp('CONNECTORS'); fx.push('Y', (0, .08, .0375)); fx.connectors(['pcin', 'pcout', 'xlr', 'xlr', 'rj'], pitch=.036); fx.pop()
    fx.grp('VENTILATION')
    for sx in (-1, 1):
        fx.push('Y', (sx * .165, .08, .0375)); fx.vent(.07, .03, n=3); fx.pop()
    fx.to('HEAD', 'TILT')
    fx.box((.386, .105, .16), (0, 0, Z), 'body', bevel=.007, lod=2)
    fx.box((.378, .004, .152), (0, yf - .0005, Z), 'black', bevel=0, lod=2)
    for dz in (-.0205, .0205): fx.box((.378, .009, .004), (0, yf - .003, Z + dz), 'body2', bevel=0)
    for dz in (-.0755, .0755): fx.box((.386, .012, .006), (0, yf - .004, Z + dz), 'body2', bevel=.001)
    for sx in (-1, 1): fx.box((.006, .012, .157), (sx * .19, yf - .004, Z), 'body2', bevel=.001)
    fx.box((.366, .004, .035), (0, yf - .002, Z), 'reflector', bevel=0)
    fx.box((.366, .0045, .003), (0, yf - .0025, Z), 'body2', bevel=0, lod=0)
    fx.box((.34, .003, .004), (0, yf - .0015, Z + .0805), 'accent', bevel=0, lod=0)
    fx.push('Z', (0, 0, Z + .08)); fx.text('CSC PULSESTRIKE M', .012, (0, 0, 0)); fx.pop()
    fx.grp('VENTILATION')
    fx.box((.12, .02, .115), (0, .0625, Z), 'body2', bevel=.003)
    fx.push('Y', (0, .0725, Z)); fx.fan(.042); fx.pop()
    for i in range(15):
        if abs(i - 7) > 2: fx.box((.004, .02, .14), ((i - 7) * .025, .0625, Z), 'aludark', bevel=0, lod=0)
    for ax, sx in (('Z', 1), ('-Z', -1)):
        fx.push(ax, (0, .012, Z + sx * .08)); fx.vent(.26, .03, n=3); fx.pop()
    fx.grp('LED_CELLS')
    for row, dz in enumerate((-.0105, .0105)):
        for i in range(14):
            fx.cell('STROBE_%02d' % (row * 14 + i + 1), 0)
            fx.box((.0242, .0065, .0135), ((i - 6.5) * .0258, yf - .0052, Z + dz), 'diffuser', bevel=.0015, lod=2); fx.endcell()
    for row, dz in enumerate((-.0455, .0455)):
        for i in range(7):
            cx = (i - 3) * .0504
            fx.cell('RGB_%02d' % (row * 7 + i + 1), row + 1)
            if fx.lod: fx.box((.046, .003, .0435), (cx, yf - .0035, Z + dz), 'led', bevel=0, lod=2)
            for a in range(4):
                for b in range(4): fx.box((.0085, .003, .0088), (cx + (a - 1.5) * .0118, yf - .0038, Z + dz + (b - 1.5) * .0116), 'led', bevel=0, lod=0)
            fx.endcell()
    fx.emit('EMIT_STROBE', (0, yf - .01, Z)); fx.emit('EMIT_RGB_TOP', (0, yf - .006, Z + .0455)); fx.emit('EMIT_RGB_BOTTOM', (0, yf - .006, Z - .0455))
    fx.mark('ZOOM_DIFFUSER', (0, yf + .02, Z))
    fx.focus((-.06, yf, Z), .2)


LEGACY4 = [('CSC_VORTEX_X1', vortex_x1), ('CSC_LUMORA_W7', lumora_w7), ('CSC_LUMORA_W19', lumora_w19), ('CSC_PULSESTRIKE_M', pulsestrike_m)]

MODELS = [('CSC_PHANTOM_S1', phantom_s1), ('CSC_PROFILE_P1', profile_p1), ('CSC_RAZOR_B1', razor_b1), ('CSC_FLASHCORE_FX', flashcore_fx),
          ('CSC_PIXELBAR_B16', pixelbar_b16), ('CSC_PIXELTUBE_T1', pixeltube_t1), ('CSC_BLINDER_X2', blinder_x2), ('CSC_BLINDER_X4', blinder_x4),
          ('CSC_BLINDER_X8', blinder_x8), ('CSC_FOLLOWSPOT_F1', followspot_f1), ('CSC_PIXELWASH_W37', pixelwash_w37),
          ('CSC_LASERCORE_L1', lasercore_l1), ('CSC_FLOODLIGHT_F8', floodlight_f8)] + LEGACY4
