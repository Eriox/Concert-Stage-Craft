# CSC fixtures — DMX charts

Generated from the DMX tables of the mod (`DmxMode.java`, the same tables the game decodes with). Channel numbers are relative to the fixture address.
Virtual Matrix: see `EXTERNAL_SHOW_CONTROL_FR.md`.

## CSC VORTEX X1 — Beam / Spot

### Full — 18 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Pan | 128 | 0-255 = -270° to +270° (128 = straight ahead) |
| 2 | Pan fine | 0 | fine byte of Pan |
| 3 | Tilt | 128 | 0-255 = -135° to +135° (128 = straight, along the base axis) |
| 4 | Tilt fine | 0 | fine byte of Tilt |
| 5 | Dimmer | 0 | 0-255 = 0-100 % |
| 6 | Dimmer fine | 0 | fine byte of Dimmer |
| 7 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 20 Hz (capped by the desk safety) |
| 8 | Red | 255 | 0-255 = 0-100 % |
| 9 | Green | 255 | 0-255 = 0-100 % |
| 10 | Blue | 255 | 0-255 = 0-100 % |
| 11 | Zoom | 89 | 0-255 = 2° to 32° |
| 12 | Zoom fine | 0 | fine byte of Zoom |
| 13 | Focus | 0 | 0-255 = 0-100 % |
| 14 | Iris | 255 | 0 = smallest, 255 = open |
| 15 | Gobo | 0 | 0-27 open · 28-55 gobo 1 · 56-83 gobo 2 · 84-111 gobo 3 · 112-139 gobo 4 · 140-167 gobo 5 · 168-195 gobo 6 · 196-223 gobo 7 · 224-255 gobo 8 |
| 16 | Gobo rotation | 128 | 0-123 one way 220°/s to slow · 124-131 stop · 132-255 other way slow to 220°/s |
| 17 | Prism | 0 | 0-127 out · 128-255 3-facet prism in |
| 18 | Prism rotation | 128 | 0-123 one way 160°/s to slow · 124-131 stop · 132-255 other way slow to 160°/s |

### Compact — 10 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Pan | 128 | 0-255 = -270° to +270° (128 = straight ahead) |
| 2 | Tilt | 128 | 0-255 = -135° to +135° (128 = straight, along the base axis) |
| 3 | Dimmer | 0 | 0-255 = 0-100 % |
| 4 | Strobe / iris / focus | 0 | 0-7 Open · 8-127 Strobe (1 Hz to 20 Hz) · 128-191 Iris open to closed · 192-255 Focus |
| 5 | Red | 255 | 0-255 = 0-100 % |
| 6 | Green | 255 | 0-255 = 0-100 % |
| 7 | Blue | 255 | 0-255 = 0-100 % |
| 8 | Zoom | 89 | 0-255 = 2° to 32° |
| 9 | Gobo / rotation | 0 | 0-27 Open · 28-32 Gobo 1 Flower · 33-43 Gobo 1 Flower rotating, slow to fast (20 to 220 °/s) · 44-55 Gobo 1 Flower rotating other way, slow to fast (18 to 220 °/s) · 56-60 Gobo 2 Dots · 61-71 Gobo 2 Dots rotating, slow to fast (20 to 220 °/s) · 72-83 Gobo 2 Dots rotating other way, slow to fast (18 to 220 °/s) · 84-88 Gobo 3 Swirl · 89-99 Gobo 3 Swirl rotating, slow to fast (20 to 220 °/s) · 100-111 Gobo 3 Swirl rotating other way, slow to fast (18 to 220 °/s) · 112-116 Gobo 4 Target · 117-127 Gobo 4 Target rotating, slow to fast (20 to 220 °/s) · 128-139 Gobo 4 Target rotating other way, slow to fast (18 to 220 °/s) · 140-144 Gobo 5 Starburst · 145-155 Gobo 5 Starburst rotating, slow to fast (20 to 220 °/s) · 156-167 Gobo 5 Starburst rotating other way, slow to fast (18 to 220 °/s) · 168-172 Gobo 6 Bars · 173-183 Gobo 6 Bars rotating, slow to fast (20 to 220 °/s) · 184-195 Gobo 6 Bars rotating other way, slow to fast (18 to 220 °/s) · 196-200 Gobo 7 Waves · 201-211 Gobo 7 Waves rotating, slow to fast (20 to 220 °/s) · 212-223 Gobo 7 Waves rotating other way, slow to fast (18 to 220 °/s) · 224-228 Gobo 8 Clover · 229-239 Gobo 8 Clover rotating, slow to fast (20 to 220 °/s) · 240-255 Gobo 8 Clover rotating other way, slow to fast (18 to 220 °/s) |
| 10 | Gobo rotation | 128 | 0-123 one way 220°/s to slow · 124-131 stop · 132-255 other way slow to 220°/s |

Not reachable in this mode: Prism; Prism rot; 16-bit position (8-bit pan / tilt); Strobe with iris at the same time (one range at a time).

### Ultra Compact — 8 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Pan | 128 | 0-255 = -270° to +270° (128 = straight ahead) |
| 2 | Tilt | 128 | 0-255 = -135° to +135° (128 = straight, along the base axis) |
| 3 | Dimmer | 0 | 0-255 = 0-100 % |
| 4 | Red | 255 | 0-255 = 0-100 % |
| 5 | Green | 255 | 0-255 = 0-100 % |
| 6 | Blue | 255 | 0-255 = 0-100 % |
| 7 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 20 Hz (capped by the desk safety) |
| 8 | Gobo / rotation | 0 | 0-27 Open · 28-32 Gobo 1 Flower · 33-43 Gobo 1 Flower rotating, slow to fast (20 to 220 °/s) · 44-55 Gobo 1 Flower rotating other way, slow to fast (18 to 220 °/s) · 56-60 Gobo 2 Dots · 61-71 Gobo 2 Dots rotating, slow to fast (20 to 220 °/s) · 72-83 Gobo 2 Dots rotating other way, slow to fast (18 to 220 °/s) · 84-88 Gobo 3 Swirl · 89-99 Gobo 3 Swirl rotating, slow to fast (20 to 220 °/s) · 100-111 Gobo 3 Swirl rotating other way, slow to fast (18 to 220 °/s) · 112-116 Gobo 4 Target · 117-127 Gobo 4 Target rotating, slow to fast (20 to 220 °/s) · 128-139 Gobo 4 Target rotating other way, slow to fast (18 to 220 °/s) · 140-144 Gobo 5 Starburst · 145-155 Gobo 5 Starburst rotating, slow to fast (20 to 220 °/s) · 156-167 Gobo 5 Starburst rotating other way, slow to fast (18 to 220 °/s) · 168-172 Gobo 6 Bars · 173-183 Gobo 6 Bars rotating, slow to fast (20 to 220 °/s) · 184-195 Gobo 6 Bars rotating other way, slow to fast (18 to 220 °/s) · 196-200 Gobo 7 Waves · 201-211 Gobo 7 Waves rotating, slow to fast (20 to 220 °/s) · 212-223 Gobo 7 Waves rotating other way, slow to fast (18 to 220 °/s) · 224-228 Gobo 8 Clover · 229-239 Gobo 8 Clover rotating, slow to fast (20 to 220 °/s) · 240-255 Gobo 8 Clover rotating other way, slow to fast (18 to 220 °/s) |

Not reachable in this mode: Zoom; Focus; Iris; Prism; Prism rot.

## CSC LUMORA W7 — Wash (compact)

### Full — 65 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Pan | 128 | 0-255 = -270° to +270° (128 = straight ahead) |
| 2 | Pan fine | 0 | fine byte of Pan |
| 3 | Tilt | 128 | 0-255 = -135° to +135° (128 = straight, along the base axis) |
| 4 | Tilt fine | 0 | fine byte of Tilt |
| 5 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 20 Hz (capped by the desk safety) |
| 6 | White | 0 | 0-255 = 0-100 % |
| 7 | CTO | 0 | 0-255 = 6500 K to 2700 K |
| 8 | Zoom | 89 | 0-255 = 8° to 50° |
| 9 | Cell 1 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 10 | Cell 1 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 11 | Cell 1 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 12 | Cell 2 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 13 | Cell 2 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 14 | Cell 2 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 15 | Cell 3 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 16 | Cell 3 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 17 | Cell 3 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 18 | Cell 4 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 19 | Cell 4 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 20 | Cell 4 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 21 | Cell 5 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 22 | Cell 5 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 23 | Cell 5 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 24 | Cell 6 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 25 | Cell 6 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 26 | Cell 6 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 27 | Cell 7 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 28 | Cell 7 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 29 | Cell 7 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 30 | Cell 8 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 31 | Cell 8 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 32 | Cell 8 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 33 | Cell 9 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 34 | Cell 9 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 35 | Cell 9 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 36 | Cell 10 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 37 | Cell 10 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 38 | Cell 10 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 39 | Cell 11 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 40 | Cell 11 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 41 | Cell 11 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 42 | Cell 12 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 43 | Cell 12 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 44 | Cell 12 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 45 | Cell 13 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 46 | Cell 13 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 47 | Cell 13 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 48 | Cell 14 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 49 | Cell 14 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 50 | Cell 14 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 51 | Cell 15 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 52 | Cell 15 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 53 | Cell 15 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 54 | Cell 16 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 55 | Cell 16 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 56 | Cell 16 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 57 | Cell 17 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 58 | Cell 17 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 59 | Cell 17 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 60 | Cell 18 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 61 | Cell 18 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 62 | Cell 18 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 63 | Cell 19 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 64 | Cell 19 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 65 | Cell 19 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |

### Compact — 9 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Pan | 128 | 0-255 = -270° to +270° (128 = straight ahead) |
| 2 | Tilt | 128 | 0-255 = -135° to +135° (128 = straight, along the base axis) |
| 3 | Dimmer | 0 | 0-255 = 0-100 % |
| 4 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 20 Hz (capped by the desk safety) |
| 5 | Red | 255 | 0-255 = 0-100 % |
| 6 | Green | 255 | 0-255 = 0-100 % |
| 7 | Blue | 255 | 0-255 = 0-100 % |
| 8 | CTO | 0 | 0-255 = 6500 K to 2700 K |
| 9 | Zoom | 89 | 0-255 = 8° to 50° |

Not reachable in this mode: W; Per cell / per zone control (19 zones); 16-bit position (8-bit pan / tilt).

### Ultra Compact — 6 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Pan | 128 | 0-255 = -270° to +270° (128 = straight ahead) |
| 2 | Tilt | 128 | 0-255 = -135° to +135° (128 = straight, along the base axis) |
| 3 | Dimmer | 0 | 0-255 = 0-100 % |
| 4 | Red | 255 | 0-255 = 0-100 % |
| 5 | Green | 255 | 0-255 = 0-100 % |
| 6 | Blue | 255 | 0-255 = 0-100 % |

Not reachable in this mode: W; CTO; Zoom; Strobe; Per cell / per zone control (19 zones).

## CSC LUMORA W19 — Wash (high power)

### Full — 119 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Pan | 128 | 0-255 = -270° to +270° (128 = straight ahead) |
| 2 | Pan fine | 0 | fine byte of Pan |
| 3 | Tilt | 128 | 0-255 = -135° to +135° (128 = straight, along the base axis) |
| 4 | Tilt fine | 0 | fine byte of Tilt |
| 5 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 20 Hz (capped by the desk safety) |
| 6 | White | 0 | 0-255 = 0-100 % |
| 7 | CTO | 0 | 0-255 = 6500 K to 2700 K |
| 8 | Zoom | 89 | 0-255 = 6° to 60° |
| 9 | Cell 1 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 10 | Cell 1 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 11 | Cell 1 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 12 | Cell 2 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 13 | Cell 2 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 14 | Cell 2 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 15 | Cell 3 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 16 | Cell 3 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 17 | Cell 3 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 18 | Cell 4 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 19 | Cell 4 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 20 | Cell 4 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 21 | Cell 5 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 22 | Cell 5 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 23 | Cell 5 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 24 | Cell 6 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 25 | Cell 6 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 26 | Cell 6 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 27 | Cell 7 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 28 | Cell 7 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 29 | Cell 7 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 30 | Cell 8 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 31 | Cell 8 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 32 | Cell 8 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 33 | Cell 9 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 34 | Cell 9 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 35 | Cell 9 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 36 | Cell 10 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 37 | Cell 10 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 38 | Cell 10 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 39 | Cell 11 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 40 | Cell 11 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 41 | Cell 11 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 42 | Cell 12 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 43 | Cell 12 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 44 | Cell 12 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 45 | Cell 13 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 46 | Cell 13 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 47 | Cell 13 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 48 | Cell 14 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 49 | Cell 14 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 50 | Cell 14 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 51 | Cell 15 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 52 | Cell 15 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 53 | Cell 15 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 54 | Cell 16 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 55 | Cell 16 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 56 | Cell 16 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 57 | Cell 17 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 58 | Cell 17 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 59 | Cell 17 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 60 | Cell 18 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 61 | Cell 18 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 62 | Cell 18 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 63 | Cell 19 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 64 | Cell 19 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 65 | Cell 19 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 66 | Cell 20 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 67 | Cell 20 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 68 | Cell 20 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 69 | Cell 21 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 70 | Cell 21 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 71 | Cell 21 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 72 | Cell 22 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 73 | Cell 22 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 74 | Cell 22 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 75 | Cell 23 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 76 | Cell 23 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 77 | Cell 23 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 78 | Cell 24 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 79 | Cell 24 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 80 | Cell 24 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 81 | Cell 25 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 82 | Cell 25 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 83 | Cell 25 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 84 | Cell 26 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 85 | Cell 26 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 86 | Cell 26 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 87 | Cell 27 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 88 | Cell 27 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 89 | Cell 27 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 90 | Cell 28 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 91 | Cell 28 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 92 | Cell 28 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 93 | Cell 29 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 94 | Cell 29 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 95 | Cell 29 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 96 | Cell 30 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 97 | Cell 30 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 98 | Cell 30 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 99 | Cell 31 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 100 | Cell 31 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 101 | Cell 31 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 102 | Cell 32 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 103 | Cell 32 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 104 | Cell 32 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 105 | Cell 33 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 106 | Cell 33 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 107 | Cell 33 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 108 | Cell 34 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 109 | Cell 34 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 110 | Cell 34 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 111 | Cell 35 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 112 | Cell 35 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 113 | Cell 35 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 114 | Cell 36 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 115 | Cell 36 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 116 | Cell 36 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 117 | Cell 37 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 118 | Cell 37 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 119 | Cell 37 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |

### Compact — 9 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Pan | 128 | 0-255 = -270° to +270° (128 = straight ahead) |
| 2 | Tilt | 128 | 0-255 = -135° to +135° (128 = straight, along the base axis) |
| 3 | Dimmer | 0 | 0-255 = 0-100 % |
| 4 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 20 Hz (capped by the desk safety) |
| 5 | Red | 255 | 0-255 = 0-100 % |
| 6 | Green | 255 | 0-255 = 0-100 % |
| 7 | Blue | 255 | 0-255 = 0-100 % |
| 8 | CTO | 0 | 0-255 = 6500 K to 2700 K |
| 9 | Zoom | 89 | 0-255 = 6° to 60° |

Not reachable in this mode: W; Per cell / per zone control (37 zones); 16-bit position (8-bit pan / tilt).

### Ultra Compact — 6 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Pan | 128 | 0-255 = -270° to +270° (128 = straight ahead) |
| 2 | Tilt | 128 | 0-255 = -135° to +135° (128 = straight, along the base axis) |
| 3 | Dimmer | 0 | 0-255 = 0-100 % |
| 4 | Red | 255 | 0-255 = 0-100 % |
| 5 | Green | 255 | 0-255 = 0-100 % |
| 6 | Blue | 255 | 0-255 = 0-100 % |

Not reachable in this mode: W; CTO; Zoom; Strobe; Per cell / per zone control (37 zones).

## CSC PULSESTRIKE M — Strobe / Blinder

### Full — 77 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Tilt | 128 | 0-255 = -95° to +95° (128 = straight, along the base axis) |
| 2 | Tilt fine | 0 | fine byte of Tilt |
| 3 | Zoom | 255 | 0-255 = 20° to 70° |
| 4 | Focus | 0 | 0-255 = 0-100 % |
| 5 | CTO (white) | 0 | 0-255 = 6500 K to 2700 K |
| 6 | White bar dimmer | 0 | 0-255 = 0-100 % |
| 7 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 8 | Top cell 1 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 9 | Top cell 1 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 10 | Top cell 1 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 11 | Top cell 2 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 12 | Top cell 2 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 13 | Top cell 2 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 14 | Top cell 3 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 15 | Top cell 3 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 16 | Top cell 3 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 17 | Top cell 4 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 18 | Top cell 4 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 19 | Top cell 4 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 20 | Top cell 5 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 21 | Top cell 5 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 22 | Top cell 5 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 23 | Top cell 6 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 24 | Top cell 6 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 25 | Top cell 6 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 26 | Top cell 7 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 27 | Top cell 7 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 28 | Top cell 7 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 29 | Centre segment 1 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 30 | Centre segment 2 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 31 | Centre segment 3 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 32 | Centre segment 4 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 33 | Centre segment 5 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 34 | Centre segment 6 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 35 | Centre segment 7 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 36 | Centre segment 8 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 37 | Centre segment 9 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 38 | Centre segment 10 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 39 | Centre segment 11 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 40 | Centre segment 12 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 41 | Centre segment 13 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 42 | Centre segment 14 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 43 | Centre segment 15 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 44 | Centre segment 16 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 45 | Centre segment 17 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 46 | Centre segment 18 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 47 | Centre segment 19 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 48 | Centre segment 20 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 49 | Centre segment 21 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 50 | Centre segment 22 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 51 | Centre segment 23 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 52 | Centre segment 24 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 53 | Centre segment 25 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 54 | Centre segment 26 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 55 | Centre segment 27 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 56 | Centre segment 28 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 57 | Bottom cell 1 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 58 | Bottom cell 1 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 59 | Bottom cell 1 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 60 | Bottom cell 2 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 61 | Bottom cell 2 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 62 | Bottom cell 2 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 63 | Bottom cell 3 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 64 | Bottom cell 3 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 65 | Bottom cell 3 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 66 | Bottom cell 4 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 67 | Bottom cell 4 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 68 | Bottom cell 4 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 69 | Bottom cell 5 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 70 | Bottom cell 5 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 71 | Bottom cell 5 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 72 | Bottom cell 6 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 73 | Bottom cell 6 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 74 | Bottom cell 6 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 75 | Bottom cell 7 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 76 | Bottom cell 7 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 77 | Bottom cell 7 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |

### Compact — 9 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Tilt | 128 | 0-255 = -95° to +95° (128 = straight, along the base axis) |
| 2 | Zoom | 255 | 0-255 = 20° to 70° |
| 3 | LED bars dimmer | 0 | 0-255 = 0-100 % |
| 4 | LED bars red | 255 | 0-255 = 0-100 % |
| 5 | LED bars green | 255 | 0-255 = 0-100 % |
| 6 | LED bars blue | 255 | 0-255 = 0-100 % |
| 7 | LED bars strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 8 | White bar dimmer | 0 | 0-255 = 0-100 % |
| 9 | White bar strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |

Not reachable in this mode: CTO; Focus; Top and bottom LED bars separately (one instance for both); Per cell / per zone control (42 zones); 16-bit position (8-bit pan / tilt).

### Ultra Compact — 6 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Tilt | 128 | 0-255 = -95° to +95° (128 = straight, along the base axis) |
| 2 | Dimmer | 0 | 0-255 = 0-100 % |
| 3 | Red | 255 | 0-255 = 0-100 % |
| 4 | Green | 255 | 0-255 = 0-100 % |
| 5 | Blue | 255 | 0-255 = 0-100 % |
| 6 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |

Not reachable in this mode: W; CTO; Zoom; Focus; Top dim; Top R; Top G; Top B; Top strobe; Centre dim; Centre strobe; Bottom dim; Bottom R; Bottom G; Bottom B; Bottom strobe; Per cell / per zone control (42 zones).

## CSC PHANTOM S1 — Moving Spot

### Full — 25 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Pan | 128 | 0-255 = -270° to +270° (128 = straight ahead) |
| 2 | Pan fine | 0 | fine byte of Pan |
| 3 | Tilt | 128 | 0-255 = -135° to +135° (128 = straight, along the base axis) |
| 4 | Tilt fine | 0 | fine byte of Tilt |
| 5 | Dimmer | 0 | 0-255 = 0-100 % |
| 6 | Dimmer fine | 0 | fine byte of Dimmer |
| 7 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 8 | Red | 255 | 0-255 = 0-100 % |
| 9 | Green | 255 | 0-255 = 0-100 % |
| 10 | Blue | 255 | 0-255 = 0-100 % |
| 11 | Zoom | 89 | 0-255 = 5° to 45° |
| 12 | Focus | 0 | 0-255 = 0-100 % |
| 13 | Iris | 255 | 0 = smallest, 255 = open |
| 14 | Gobo | 0 | 0-27 open · 28-55 gobo 1 · 56-83 gobo 2 · 84-111 gobo 3 · 112-139 gobo 4 · 140-167 gobo 5 · 168-195 gobo 6 · 196-223 gobo 7 · 224-255 gobo 8 |
| 15 | Gobo rotation | 128 | 0-123 one way 220°/s to slow · 124-131 stop · 132-255 other way slow to 220°/s |
| 16 | Prism | 0 | 0-127 out · 128-255 3-facet prism in |
| 17 | Prism rotation | 128 | 0-123 one way 160°/s to slow · 124-131 stop · 132-255 other way slow to 160°/s |
| 18 | Frost | 0 | 0-255 = 0-100 % |
| 19 | Motor speed | 0 | 0-255 = 0-100 % |
| 20 | Gobo 2 | 0 | 0-27 open · 28-55 gobo 1 · 56-83 gobo 2 · 84-111 gobo 3 · 112-139 gobo 4 · 140-167 gobo 5 · 168-195 gobo 6 · 196-223 gobo 7 · 224-255 gobo 8 |
| 21 | Gobo 2 rot | 128 | 0-123 one way 220°/s to slow · 124-131 stop · 132-255 other way slow to 220°/s |
| 22 | Animation | 0 | 0-255 = 0-100 % |
| 23 | Prism 2 | 0 | 0-127 out · 128-255 6-facet prism in |
| 24 | Prism 2 rot | 128 | 0-123 one way 160°/s to slow · 124-131 stop · 132-255 other way slow to 160°/s |
| 25 | Shutter | 255 | 0 = closed, 255 = open (linear transmission) |

### Compact — 10 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Pan | 128 | 0-255 = -270° to +270° (128 = straight ahead) |
| 2 | Tilt | 128 | 0-255 = -135° to +135° (128 = straight, along the base axis) |
| 3 | Dimmer | 0 | 0-255 = 0-100 % |
| 4 | Strobe / iris / focus | 0 | 0-7 Open · 8-127 Strobe (1 Hz to 25 Hz) · 128-191 Iris open to closed · 192-255 Focus |
| 5 | Red | 255 | 0-255 = 0-100 % |
| 6 | Green | 255 | 0-255 = 0-100 % |
| 7 | Blue | 255 | 0-255 = 0-100 % |
| 8 | Zoom | 89 | 0-255 = 5° to 45° |
| 9 | Gobos 1 + 2 / rotation | 0 | 0-14 Open · 15-17 Wheel 1 gobo 1 Tunnel · 18-23 Wheel 1 gobo 1 Tunnel rotating, slow to fast (37 to 220 °/s) · 24-29 Wheel 1 gobo 1 Tunnel rotating other way, slow to fast (37 to 220 °/s) · 30-32 Wheel 1 gobo 2 Sun · 33-38 Wheel 1 gobo 2 Sun rotating, slow to fast (37 to 220 °/s) · 39-44 Wheel 1 gobo 2 Sun rotating other way, slow to fast (37 to 220 °/s) · 45-47 Wheel 1 gobo 3 Honeycomb · 48-53 Wheel 1 gobo 3 Honeycomb rotating, slow to fast (37 to 220 °/s) · 54-59 Wheel 1 gobo 3 Honeycomb rotating other way, slow to fast (37 to 220 °/s) · 60-62 Wheel 1 gobo 4 Orbit · 63-68 Wheel 1 gobo 4 Orbit rotating, slow to fast (37 to 220 °/s) · 69-74 Wheel 1 gobo 4 Orbit rotating other way, slow to fast (37 to 220 °/s) · 75-77 Wheel 1 gobo 5 Flames · 78-83 Wheel 1 gobo 5 Flames rotating, slow to fast (37 to 220 °/s) · 84-89 Wheel 1 gobo 5 Flames rotating other way, slow to fast (37 to 220 °/s) · 90-92 Wheel 1 gobo 6 Half moon · 93-98 Wheel 1 gobo 6 Half moon rotating, slow to fast (37 to 220 °/s) · 99-104 Wheel 1 gobo 6 Half moon rotating other way, slow to fast (37 to 220 °/s) · 105-107 Wheel 1 gobo 7 Propeller · 108-113 Wheel 1 gobo 7 Propeller rotating, slow to fast (37 to 220 °/s) · 114-119 Wheel 1 gobo 7 Propeller rotating other way, slow to fast (37 to 220 °/s) · 120-122 Wheel 1 gobo 8 Triangles · 123-128 Wheel 1 gobo 8 Triangles rotating, slow to fast (37 to 220 °/s) · 129-134 Wheel 1 gobo 8 Triangles rotating other way, slow to fast (37 to 220 °/s) · 135-137 Wheel 2 gobo 1 Six rays · 138-143 Wheel 2 gobo 1 Six rays rotating, slow to fast (37 to 220 °/s) · 144-149 Wheel 2 gobo 1 Six rays rotating other way, slow to fast (37 to 220 °/s) · 150-152 Wheel 2 gobo 2 Ring · 153-158 Wheel 2 gobo 2 Ring rotating, slow to fast (37 to 220 °/s) · 159-164 Wheel 2 gobo 2 Ring rotating other way, slow to fast (37 to 220 °/s) · 165-167 Wheel 2 gobo 3 Cross · 168-173 Wheel 2 gobo 3 Cross rotating, slow to fast (37 to 220 °/s) · 174-179 Wheel 2 gobo 3 Cross rotating other way, slow to fast (37 to 220 °/s) · 180-182 Wheel 2 gobo 4 Bars · 183-188 Wheel 2 gobo 4 Bars rotating, slow to fast (37 to 220 °/s) · 189-194 Wheel 2 gobo 4 Bars rotating other way, slow to fast (37 to 220 °/s) · 195-197 Wheel 2 gobo 5 Fine star · 198-203 Wheel 2 gobo 5 Fine star rotating, slow to fast (37 to 220 °/s) · 204-209 Wheel 2 gobo 5 Fine star rotating other way, slow to fast (37 to 220 °/s) · 210-212 Wheel 2 gobo 6 Square · 213-218 Wheel 2 gobo 6 Square rotating, slow to fast (37 to 220 °/s) · 219-224 Wheel 2 gobo 6 Square rotating other way, slow to fast (37 to 220 °/s) · 225-227 Wheel 2 gobo 7 Diagonals · 228-233 Wheel 2 gobo 7 Diagonals rotating, slow to fast (37 to 220 °/s) · 234-239 Wheel 2 gobo 7 Diagonals rotating other way, slow to fast (37 to 220 °/s) · 240-242 Wheel 2 gobo 8 Rings · 243-248 Wheel 2 gobo 8 Rings rotating, slow to fast (37 to 220 °/s) · 249-255 Wheel 2 gobo 8 Rings rotating other way, slow to fast (37 to 220 °/s) |
| 10 | Gobo rotation | 128 | 0-123 one way 220°/s to slow · 124-131 stop · 132-255 other way slow to 220°/s |

Not reachable in this mode: Prism; Prism rot; Frost; Motor speed; Animation; Prism 2; Prism 2 rot; Shutter; 16-bit position (8-bit pan / tilt); Strobe with iris at the same time (one range at a time); Both gobo wheels at the same time (one gobo at a time).

### Ultra Compact — 8 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Pan | 128 | 0-255 = -270° to +270° (128 = straight ahead) |
| 2 | Tilt | 128 | 0-255 = -135° to +135° (128 = straight, along the base axis) |
| 3 | Dimmer | 0 | 0-255 = 0-100 % |
| 4 | Red | 255 | 0-255 = 0-100 % |
| 5 | Green | 255 | 0-255 = 0-100 % |
| 6 | Blue | 255 | 0-255 = 0-100 % |
| 7 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 8 | Gobos 1 + 2 / rotation | 0 | 0-14 Open · 15-17 Wheel 1 gobo 1 Tunnel · 18-23 Wheel 1 gobo 1 Tunnel rotating, slow to fast (37 to 220 °/s) · 24-29 Wheel 1 gobo 1 Tunnel rotating other way, slow to fast (37 to 220 °/s) · 30-32 Wheel 1 gobo 2 Sun · 33-38 Wheel 1 gobo 2 Sun rotating, slow to fast (37 to 220 °/s) · 39-44 Wheel 1 gobo 2 Sun rotating other way, slow to fast (37 to 220 °/s) · 45-47 Wheel 1 gobo 3 Honeycomb · 48-53 Wheel 1 gobo 3 Honeycomb rotating, slow to fast (37 to 220 °/s) · 54-59 Wheel 1 gobo 3 Honeycomb rotating other way, slow to fast (37 to 220 °/s) · 60-62 Wheel 1 gobo 4 Orbit · 63-68 Wheel 1 gobo 4 Orbit rotating, slow to fast (37 to 220 °/s) · 69-74 Wheel 1 gobo 4 Orbit rotating other way, slow to fast (37 to 220 °/s) · 75-77 Wheel 1 gobo 5 Flames · 78-83 Wheel 1 gobo 5 Flames rotating, slow to fast (37 to 220 °/s) · 84-89 Wheel 1 gobo 5 Flames rotating other way, slow to fast (37 to 220 °/s) · 90-92 Wheel 1 gobo 6 Half moon · 93-98 Wheel 1 gobo 6 Half moon rotating, slow to fast (37 to 220 °/s) · 99-104 Wheel 1 gobo 6 Half moon rotating other way, slow to fast (37 to 220 °/s) · 105-107 Wheel 1 gobo 7 Propeller · 108-113 Wheel 1 gobo 7 Propeller rotating, slow to fast (37 to 220 °/s) · 114-119 Wheel 1 gobo 7 Propeller rotating other way, slow to fast (37 to 220 °/s) · 120-122 Wheel 1 gobo 8 Triangles · 123-128 Wheel 1 gobo 8 Triangles rotating, slow to fast (37 to 220 °/s) · 129-134 Wheel 1 gobo 8 Triangles rotating other way, slow to fast (37 to 220 °/s) · 135-137 Wheel 2 gobo 1 Six rays · 138-143 Wheel 2 gobo 1 Six rays rotating, slow to fast (37 to 220 °/s) · 144-149 Wheel 2 gobo 1 Six rays rotating other way, slow to fast (37 to 220 °/s) · 150-152 Wheel 2 gobo 2 Ring · 153-158 Wheel 2 gobo 2 Ring rotating, slow to fast (37 to 220 °/s) · 159-164 Wheel 2 gobo 2 Ring rotating other way, slow to fast (37 to 220 °/s) · 165-167 Wheel 2 gobo 3 Cross · 168-173 Wheel 2 gobo 3 Cross rotating, slow to fast (37 to 220 °/s) · 174-179 Wheel 2 gobo 3 Cross rotating other way, slow to fast (37 to 220 °/s) · 180-182 Wheel 2 gobo 4 Bars · 183-188 Wheel 2 gobo 4 Bars rotating, slow to fast (37 to 220 °/s) · 189-194 Wheel 2 gobo 4 Bars rotating other way, slow to fast (37 to 220 °/s) · 195-197 Wheel 2 gobo 5 Fine star · 198-203 Wheel 2 gobo 5 Fine star rotating, slow to fast (37 to 220 °/s) · 204-209 Wheel 2 gobo 5 Fine star rotating other way, slow to fast (37 to 220 °/s) · 210-212 Wheel 2 gobo 6 Square · 213-218 Wheel 2 gobo 6 Square rotating, slow to fast (37 to 220 °/s) · 219-224 Wheel 2 gobo 6 Square rotating other way, slow to fast (37 to 220 °/s) · 225-227 Wheel 2 gobo 7 Diagonals · 228-233 Wheel 2 gobo 7 Diagonals rotating, slow to fast (37 to 220 °/s) · 234-239 Wheel 2 gobo 7 Diagonals rotating other way, slow to fast (37 to 220 °/s) · 240-242 Wheel 2 gobo 8 Rings · 243-248 Wheel 2 gobo 8 Rings rotating, slow to fast (37 to 220 °/s) · 249-255 Wheel 2 gobo 8 Rings rotating other way, slow to fast (37 to 220 °/s) |

Not reachable in this mode: Zoom; Focus; Iris; Prism; Prism rot; Frost; Motor speed; Animation; Prism 2; Prism 2 rot; Shutter; Both gobo wheels at the same time (one gobo at a time).

## CSC PROFILE P1 — Moving Profile

### Full — 24 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Pan | 128 | 0-255 = -270° to +270° (128 = straight ahead) |
| 2 | Pan fine | 0 | fine byte of Pan |
| 3 | Tilt | 128 | 0-255 = -135° to +135° (128 = straight, along the base axis) |
| 4 | Tilt fine | 0 | fine byte of Tilt |
| 5 | Dimmer | 0 | 0-255 = 0-100 % |
| 6 | Dimmer fine | 0 | fine byte of Dimmer |
| 7 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 8 | Red | 255 | 0-255 = 0-100 % |
| 9 | Green | 255 | 0-255 = 0-100 % |
| 10 | Blue | 255 | 0-255 = 0-100 % |
| 11 | Zoom | 89 | 0-255 = 7° to 50° |
| 12 | Focus | 0 | 0-255 = 0-100 % |
| 13 | Iris | 255 | 0 = smallest, 255 = open |
| 14 | Gobo | 0 | 0-27 open · 28-55 gobo 1 · 56-83 gobo 2 · 84-111 gobo 3 · 112-139 gobo 4 · 140-167 gobo 5 · 168-195 gobo 6 · 196-223 gobo 7 · 224-255 gobo 8 |
| 15 | Gobo rotation | 128 | 0-123 one way 220°/s to slow · 124-131 stop · 132-255 other way slow to 220°/s |
| 16 | Prism | 0 | 0-127 out · 128-255 3-facet prism in |
| 17 | Prism rotation | 128 | 0-123 one way 160°/s to slow · 124-131 stop · 132-255 other way slow to 160°/s |
| 18 | Frost | 0 | 0-255 = 0-100 % |
| 19 | Blade 1 | 0 | 0-255 = 0-100 % |
| 20 | Blade 2 | 0 | 0-255 = 0-100 % |
| 21 | Blade 3 | 0 | 0-255 = 0-100 % |
| 22 | Blade 4 | 0 | 0-255 = 0-100 % |
| 23 | Frame rotation | 128 | 0-255 = -90° to +90° |
| 24 | Motor speed | 0 | 0-255 = 0-100 % |

### Compact — 10 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Pan | 128 | 0-255 = -270° to +270° (128 = straight ahead) |
| 2 | Tilt | 128 | 0-255 = -135° to +135° (128 = straight, along the base axis) |
| 3 | Dimmer | 0 | 0-255 = 0-100 % |
| 4 | Strobe / iris / focus | 0 | 0-7 Open · 8-127 Strobe (1 Hz to 25 Hz) · 128-191 Iris open to closed · 192-255 Focus |
| 5 | Red | 255 | 0-255 = 0-100 % |
| 6 | Green | 255 | 0-255 = 0-100 % |
| 7 | Blue | 255 | 0-255 = 0-100 % |
| 8 | Zoom | 89 | 0-255 = 7° to 50° |
| 9 | Gobo / rotation | 0 | 0-27 Open · 28-32 Gobo 1 Window · 33-43 Gobo 1 Window rotating, slow to fast (20 to 220 °/s) · 44-55 Gobo 1 Window rotating other way, slow to fast (18 to 220 °/s) · 56-60 Gobo 2 Leaves · 61-71 Gobo 2 Leaves rotating, slow to fast (20 to 220 °/s) · 72-83 Gobo 2 Leaves rotating other way, slow to fast (18 to 220 °/s) · 84-88 Gobo 3 Clouds · 89-99 Gobo 3 Clouds rotating, slow to fast (20 to 220 °/s) · 100-111 Gobo 3 Clouds rotating other way, slow to fast (18 to 220 °/s) · 112-116 Gobo 4 Blinds · 117-127 Gobo 4 Blinds rotating, slow to fast (20 to 220 °/s) · 128-139 Gobo 4 Blinds rotating other way, slow to fast (18 to 220 °/s) · 140-144 Gobo 5 Arch window · 145-155 Gobo 5 Arch window rotating, slow to fast (20 to 220 °/s) · 156-167 Gobo 5 Arch window rotating other way, slow to fast (18 to 220 °/s) · 168-172 Gobo 6 Star field · 173-183 Gobo 6 Star field rotating, slow to fast (20 to 220 °/s) · 184-195 Gobo 6 Star field rotating other way, slow to fast (18 to 220 °/s) · 196-200 Gobo 7 Water · 201-211 Gobo 7 Water rotating, slow to fast (20 to 220 °/s) · 212-223 Gobo 7 Water rotating other way, slow to fast (18 to 220 °/s) · 224-228 Gobo 8 Cells · 229-239 Gobo 8 Cells rotating, slow to fast (20 to 220 °/s) · 240-255 Gobo 8 Cells rotating other way, slow to fast (18 to 220 °/s) |
| 10 | Gobo rotation | 128 | 0-123 one way 220°/s to slow · 124-131 stop · 132-255 other way slow to 220°/s |

Not reachable in this mode: Prism; Prism rot; Frost; Blade 1; Blade 2; Blade 3; Blade 4; Frame rotation; Motor speed; 16-bit position (8-bit pan / tilt); Strobe with iris at the same time (one range at a time).

### Ultra Compact — 8 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Pan | 128 | 0-255 = -270° to +270° (128 = straight ahead) |
| 2 | Tilt | 128 | 0-255 = -135° to +135° (128 = straight, along the base axis) |
| 3 | Dimmer | 0 | 0-255 = 0-100 % |
| 4 | Red | 255 | 0-255 = 0-100 % |
| 5 | Green | 255 | 0-255 = 0-100 % |
| 6 | Blue | 255 | 0-255 = 0-100 % |
| 7 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 8 | Gobo / rotation | 0 | 0-27 Open · 28-32 Gobo 1 Window · 33-43 Gobo 1 Window rotating, slow to fast (20 to 220 °/s) · 44-55 Gobo 1 Window rotating other way, slow to fast (18 to 220 °/s) · 56-60 Gobo 2 Leaves · 61-71 Gobo 2 Leaves rotating, slow to fast (20 to 220 °/s) · 72-83 Gobo 2 Leaves rotating other way, slow to fast (18 to 220 °/s) · 84-88 Gobo 3 Clouds · 89-99 Gobo 3 Clouds rotating, slow to fast (20 to 220 °/s) · 100-111 Gobo 3 Clouds rotating other way, slow to fast (18 to 220 °/s) · 112-116 Gobo 4 Blinds · 117-127 Gobo 4 Blinds rotating, slow to fast (20 to 220 °/s) · 128-139 Gobo 4 Blinds rotating other way, slow to fast (18 to 220 °/s) · 140-144 Gobo 5 Arch window · 145-155 Gobo 5 Arch window rotating, slow to fast (20 to 220 °/s) · 156-167 Gobo 5 Arch window rotating other way, slow to fast (18 to 220 °/s) · 168-172 Gobo 6 Star field · 173-183 Gobo 6 Star field rotating, slow to fast (20 to 220 °/s) · 184-195 Gobo 6 Star field rotating other way, slow to fast (18 to 220 °/s) · 196-200 Gobo 7 Water · 201-211 Gobo 7 Water rotating, slow to fast (20 to 220 °/s) · 212-223 Gobo 7 Water rotating other way, slow to fast (18 to 220 °/s) · 224-228 Gobo 8 Cells · 229-239 Gobo 8 Cells rotating, slow to fast (20 to 220 °/s) · 240-255 Gobo 8 Cells rotating other way, slow to fast (18 to 220 °/s) |

Not reachable in this mode: Zoom; Focus; Iris; Prism; Prism rot; Frost; Blade 1; Blade 2; Blade 3; Blade 4; Frame rotation; Motor speed.

## CSC RAZOR B1 — Moving Beam

### Full — 19 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Pan | 128 | 0-255 = -270° to +270° (128 = straight ahead) |
| 2 | Pan fine | 0 | fine byte of Pan |
| 3 | Tilt | 128 | 0-255 = -135° to +135° (128 = straight, along the base axis) |
| 4 | Tilt fine | 0 | fine byte of Tilt |
| 5 | Dimmer | 0 | 0-255 = 0-100 % |
| 6 | Dimmer fine | 0 | fine byte of Dimmer |
| 7 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 8 | Red | 255 | 0-255 = 0-100 % |
| 9 | Green | 255 | 0-255 = 0-100 % |
| 10 | Blue | 255 | 0-255 = 0-100 % |
| 11 | Zoom | 89 | 0-255 = 1° to 4° |
| 12 | Focus | 0 | 0-255 = 0-100 % |
| 13 | Gobo | 0 | 0-27 open · 28-55 gobo 1 · 56-83 gobo 2 · 84-111 gobo 3 · 112-139 gobo 4 · 140-167 gobo 5 · 168-195 gobo 6 · 196-223 gobo 7 · 224-255 gobo 8 |
| 14 | Prism | 0 | 0-127 out · 128-255 8-facet prism in |
| 15 | Prism rotation | 128 | 0-123 one way 160°/s to slow · 124-131 stop · 132-255 other way slow to 160°/s |
| 16 | Color wheel | 0 | 0-255 = 0-100 % |
| 17 | Frost | 0 | 0-255 = 0-100 % |
| 18 | Motor speed | 0 | 0-255 = 0-100 % |
| 19 | Gobo rotation | 128 | 0-123 one way 220°/s to slow · 124-131 stop · 132-255 other way slow to 220°/s |

### Compact — 10 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Pan | 128 | 0-255 = -270° to +270° (128 = straight ahead) |
| 2 | Tilt | 128 | 0-255 = -135° to +135° (128 = straight, along the base axis) |
| 3 | Dimmer | 0 | 0-255 = 0-100 % |
| 4 | Strobe / focus | 0 | 0-7 Open · 8-127 Strobe (1 Hz to 25 Hz) · 128-255 Focus |
| 5 | Red | 255 | 0-255 = 0-100 % |
| 6 | Green | 255 | 0-255 = 0-100 % |
| 7 | Blue | 255 | 0-255 = 0-100 % |
| 8 | Zoom | 89 | 0-255 = 1° to 4° |
| 9 | Gobo / rotation | 0 | 0-27 Open · 28-32 Gobo 1 Pinhole · 33-43 Gobo 1 Pinhole rotating, slow to fast (20 to 220 °/s) · 44-55 Gobo 1 Pinhole rotating other way, slow to fast (18 to 220 °/s) · 56-60 Gobo 2 Ring · 61-71 Gobo 2 Ring rotating, slow to fast (20 to 220 °/s) · 72-83 Gobo 2 Ring rotating other way, slow to fast (18 to 220 °/s) · 84-88 Gobo 3 Triangle · 89-99 Gobo 3 Triangle rotating, slow to fast (20 to 220 °/s) · 100-111 Gobo 3 Triangle rotating other way, slow to fast (18 to 220 °/s) · 112-116 Gobo 4 Three dots · 117-127 Gobo 4 Three dots rotating, slow to fast (20 to 220 °/s) · 128-139 Gobo 4 Three dots rotating other way, slow to fast (18 to 220 °/s) · 140-144 Gobo 5 Star · 145-155 Gobo 5 Star rotating, slow to fast (20 to 220 °/s) · 156-167 Gobo 5 Star rotating other way, slow to fast (18 to 220 °/s) · 168-172 Gobo 6 Slit · 173-183 Gobo 6 Slit rotating, slow to fast (20 to 220 °/s) · 184-195 Gobo 6 Slit rotating other way, slow to fast (18 to 220 °/s) · 196-200 Gobo 7 Double ring · 201-211 Gobo 7 Double ring rotating, slow to fast (20 to 220 °/s) · 212-223 Gobo 7 Double ring rotating other way, slow to fast (18 to 220 °/s) · 224-228 Gobo 8 Six dots · 229-239 Gobo 8 Six dots rotating, slow to fast (20 to 220 °/s) · 240-255 Gobo 8 Six dots rotating other way, slow to fast (18 to 220 °/s) |
| 10 | Gobo rotation | 128 | 0-123 one way 220°/s to slow · 124-131 stop · 132-255 other way slow to 220°/s |

Not reachable in this mode: Prism; Prism rot; Color wheel; Frost; Motor speed; 16-bit position (8-bit pan / tilt); Strobe with focus at the same time (one range at a time).

### Ultra Compact — 8 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Pan | 128 | 0-255 = -270° to +270° (128 = straight ahead) |
| 2 | Tilt | 128 | 0-255 = -135° to +135° (128 = straight, along the base axis) |
| 3 | Dimmer | 0 | 0-255 = 0-100 % |
| 4 | Red | 255 | 0-255 = 0-100 % |
| 5 | Green | 255 | 0-255 = 0-100 % |
| 6 | Blue | 255 | 0-255 = 0-100 % |
| 7 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 8 | Gobo / rotation | 0 | 0-27 Open · 28-32 Gobo 1 Pinhole · 33-43 Gobo 1 Pinhole rotating, slow to fast (20 to 220 °/s) · 44-55 Gobo 1 Pinhole rotating other way, slow to fast (18 to 220 °/s) · 56-60 Gobo 2 Ring · 61-71 Gobo 2 Ring rotating, slow to fast (20 to 220 °/s) · 72-83 Gobo 2 Ring rotating other way, slow to fast (18 to 220 °/s) · 84-88 Gobo 3 Triangle · 89-99 Gobo 3 Triangle rotating, slow to fast (20 to 220 °/s) · 100-111 Gobo 3 Triangle rotating other way, slow to fast (18 to 220 °/s) · 112-116 Gobo 4 Three dots · 117-127 Gobo 4 Three dots rotating, slow to fast (20 to 220 °/s) · 128-139 Gobo 4 Three dots rotating other way, slow to fast (18 to 220 °/s) · 140-144 Gobo 5 Star · 145-155 Gobo 5 Star rotating, slow to fast (20 to 220 °/s) · 156-167 Gobo 5 Star rotating other way, slow to fast (18 to 220 °/s) · 168-172 Gobo 6 Slit · 173-183 Gobo 6 Slit rotating, slow to fast (20 to 220 °/s) · 184-195 Gobo 6 Slit rotating other way, slow to fast (18 to 220 °/s) · 196-200 Gobo 7 Double ring · 201-211 Gobo 7 Double ring rotating, slow to fast (20 to 220 °/s) · 212-223 Gobo 7 Double ring rotating other way, slow to fast (18 to 220 °/s) · 224-228 Gobo 8 Six dots · 229-239 Gobo 8 Six dots rotating, slow to fast (20 to 220 °/s) · 240-255 Gobo 8 Six dots rotating other way, slow to fast (18 to 220 °/s) |

Not reachable in this mode: Zoom; Focus; Prism; Prism rot; Color wheel; Frost; Motor speed.

## CSC FLASHCORE FX — Hybrid Strobe

### Full — 55 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Tilt | 128 | 0-255 = -95° to +95° (128 = straight, along the base axis) |
| 2 | Tilt fine | 0 | fine byte of Tilt |
| 3 | Motor speed | 0 | 0-255 = 0-100 % |
| 4 | Zoom | 89 | 0-255 = 30° to 80° |
| 5 | CTO (white) | 0 | 0-255 = 6500 K to 2700 K |
| 6 | White bar dimmer | 0 | 0-255 = 0-100 % |
| 7 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 8 | Top cell 1 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 9 | Top cell 1 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 10 | Top cell 1 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 11 | Top cell 2 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 12 | Top cell 2 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 13 | Top cell 2 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 14 | Top cell 3 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 15 | Top cell 3 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 16 | Top cell 3 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 17 | Top cell 4 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 18 | Top cell 4 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 19 | Top cell 4 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 20 | Top cell 5 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 21 | Top cell 5 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 22 | Top cell 5 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 23 | Top cell 6 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 24 | Top cell 6 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 25 | Top cell 6 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 26 | Centre segment 1 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 27 | Centre segment 2 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 28 | Centre segment 3 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 29 | Centre segment 4 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 30 | Centre segment 5 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 31 | Centre segment 6 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 32 | Centre segment 7 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 33 | Centre segment 8 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 34 | Centre segment 9 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 35 | Centre segment 10 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 36 | Centre segment 11 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 37 | Centre segment 12 | 0 | 0-255 = 0-100 % (the segment, times the white bar dimmer) |
| 38 | Bottom cell 1 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 39 | Bottom cell 1 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 40 | Bottom cell 1 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 41 | Bottom cell 2 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 42 | Bottom cell 2 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 43 | Bottom cell 2 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 44 | Bottom cell 3 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 45 | Bottom cell 3 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 46 | Bottom cell 3 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 47 | Bottom cell 4 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 48 | Bottom cell 4 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 49 | Bottom cell 4 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 50 | Bottom cell 5 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 51 | Bottom cell 5 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 52 | Bottom cell 5 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 53 | Bottom cell 6 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 54 | Bottom cell 6 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 55 | Bottom cell 6 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |

### Compact — 9 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Tilt | 128 | 0-255 = -95° to +95° (128 = straight, along the base axis) |
| 2 | Zoom | 89 | 0-255 = 30° to 80° |
| 3 | LED bars dimmer | 0 | 0-255 = 0-100 % |
| 4 | LED bars red | 255 | 0-255 = 0-100 % |
| 5 | LED bars green | 255 | 0-255 = 0-100 % |
| 6 | LED bars blue | 255 | 0-255 = 0-100 % |
| 7 | LED bars strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 8 | White bar dimmer | 0 | 0-255 = 0-100 % |
| 9 | White bar strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |

Not reachable in this mode: CTO; Motor speed; Top and bottom LED bars separately (one instance for both); Per cell / per zone control (24 zones); 16-bit position (8-bit pan / tilt).

### Ultra Compact — 5 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Tilt | 128 | 0-255 = -95° to +95° (128 = straight, along the base axis) |
| 2 | Dimmer | 0 | 0-255 = 0-100 % |
| 3 | Red | 255 | 0-255 = 0-100 % |
| 4 | Green | 255 | 0-255 = 0-100 % |
| 5 | Blue | 255 | 0-255 = 0-100 % |

Not reachable in this mode: W; CTO; Zoom; Strobe; Motor speed; Top dim; Top R; Top G; Top B; Top strobe; Centre dim; Centre strobe; Bottom dim; Bottom R; Bottom G; Bottom B; Bottom strobe; Per cell / per zone control (24 zones).

## CSC PIXELBAR B16 — Motorized LED Bar

### Full — 69 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Tilt | 128 | 0-255 = -95° to +95° (128 = straight, along the base axis) |
| 2 | Tilt fine | 0 | fine byte of Tilt |
| 3 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 4 | Zoom | 89 | 0-255 = 5° to 50° |
| 5 | Motor speed | 0 | 0-255 = 0-100 % |
| 6 | Cell 1 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 7 | Cell 1 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 8 | Cell 1 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 9 | Cell 1 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 10 | Cell 2 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 11 | Cell 2 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 12 | Cell 2 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 13 | Cell 2 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 14 | Cell 3 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 15 | Cell 3 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 16 | Cell 3 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 17 | Cell 3 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 18 | Cell 4 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 19 | Cell 4 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 20 | Cell 4 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 21 | Cell 4 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 22 | Cell 5 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 23 | Cell 5 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 24 | Cell 5 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 25 | Cell 5 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 26 | Cell 6 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 27 | Cell 6 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 28 | Cell 6 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 29 | Cell 6 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 30 | Cell 7 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 31 | Cell 7 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 32 | Cell 7 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 33 | Cell 7 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 34 | Cell 8 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 35 | Cell 8 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 36 | Cell 8 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 37 | Cell 8 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 38 | Cell 9 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 39 | Cell 9 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 40 | Cell 9 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 41 | Cell 9 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 42 | Cell 10 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 43 | Cell 10 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 44 | Cell 10 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 45 | Cell 10 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 46 | Cell 11 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 47 | Cell 11 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 48 | Cell 11 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 49 | Cell 11 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 50 | Cell 12 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 51 | Cell 12 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 52 | Cell 12 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 53 | Cell 12 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 54 | Cell 13 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 55 | Cell 13 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 56 | Cell 13 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 57 | Cell 13 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 58 | Cell 14 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 59 | Cell 14 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 60 | Cell 14 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 61 | Cell 14 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 62 | Cell 15 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 63 | Cell 15 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 64 | Cell 15 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 65 | Cell 15 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 66 | Cell 16 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 67 | Cell 16 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 68 | Cell 16 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 69 | Cell 16 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |

### Compact — 8 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Tilt | 128 | 0-255 = -95° to +95° (128 = straight, along the base axis) |
| 2 | Dimmer | 0 | 0-255 = 0-100 % |
| 3 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 4 | Red | 255 | 0-255 = 0-100 % |
| 5 | Green | 255 | 0-255 = 0-100 % |
| 6 | Blue | 255 | 0-255 = 0-100 % |
| 7 | White | 0 | 0-255 = 0-100 % |
| 8 | Zoom | 89 | 0-255 = 5° to 50° |

Not reachable in this mode: Motor speed; Per cell / per zone control (16 zones); 16-bit position (8-bit pan / tilt).

### Ultra Compact — 5 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Tilt | 128 | 0-255 = -95° to +95° (128 = straight, along the base axis) |
| 2 | Dimmer | 0 | 0-255 = 0-100 % |
| 3 | Red | 255 | 0-255 = 0-100 % |
| 4 | Green | 255 | 0-255 = 0-100 % |
| 5 | Blue | 255 | 0-255 = 0-100 % |

Not reachable in this mode: W; Zoom; Strobe; Motor speed; Per cell / per zone control (16 zones).

## CSC PIXELTUBE T1 — RGB LED Tube

### Full — 50 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 2 | CTO | 0 | 0-255 = 6500 K to 2700 K |
| 3 | Cell 1 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 4 | Cell 1 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 5 | Cell 1 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 6 | Cell 2 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 7 | Cell 2 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 8 | Cell 2 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 9 | Cell 3 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 10 | Cell 3 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 11 | Cell 3 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 12 | Cell 4 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 13 | Cell 4 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 14 | Cell 4 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 15 | Cell 5 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 16 | Cell 5 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 17 | Cell 5 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 18 | Cell 6 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 19 | Cell 6 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 20 | Cell 6 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 21 | Cell 7 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 22 | Cell 7 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 23 | Cell 7 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 24 | Cell 8 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 25 | Cell 8 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 26 | Cell 8 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 27 | Cell 9 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 28 | Cell 9 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 29 | Cell 9 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 30 | Cell 10 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 31 | Cell 10 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 32 | Cell 10 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 33 | Cell 11 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 34 | Cell 11 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 35 | Cell 11 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 36 | Cell 12 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 37 | Cell 12 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 38 | Cell 12 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 39 | Cell 13 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 40 | Cell 13 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 41 | Cell 13 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 42 | Cell 14 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 43 | Cell 14 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 44 | Cell 14 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 45 | Cell 15 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 46 | Cell 15 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 47 | Cell 15 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 48 | Cell 16 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 49 | Cell 16 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 50 | Cell 16 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |

### Compact — 6 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer | 0 | 0-255 = 0-100 % |
| 2 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 3 | Red | 255 | 0-255 = 0-100 % |
| 4 | Green | 255 | 0-255 = 0-100 % |
| 5 | Blue | 255 | 0-255 = 0-100 % |
| 6 | CTO | 0 | 0-255 = 6500 K to 2700 K |

Not reachable in this mode: Per cell / per zone control (16 zones).

### Ultra Compact — 4 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer | 0 | 0-255 = 0-100 % |
| 2 | Red | 255 | 0-255 = 0-100 % |
| 3 | Green | 255 | 0-255 = 0-100 % |
| 4 | Blue | 255 | 0-255 = 0-100 % |

Not reachable in this mode: CTO; Strobe; Per cell / per zone control (16 zones).

## CSC BLINDER X2 — Dual Blinder

### Full — 7 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer | 0 | 0-255 = 0-100 % |
| 2 | Dimmer fine | 0 | fine byte of Dimmer |
| 3 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 4 | White | 0 | 0-255 = 0-100 % |
| 5 | CTO | 0 | 0-255 = 6500 K to 2700 K |
| 6 | White segment 1 | 0 | 0-255 (merged highest-takes-precedence with the main colour) |
| 7 | White segment 2 | 0 | 0-255 (merged highest-takes-precedence with the main colour) |

### Compact — 4 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer | 0 | 0-255 = 0-100 % |
| 2 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 3 | White | 0 | 0-255 = 0-100 % |
| 4 | CTO | 0 | 0-255 = 6500 K to 2700 K |

Not reachable in this mode: Per cell / per zone control (2 zones).

### Ultra Compact — 1 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer (halogen) | 0 | 0-255 = 0-100 % |

Not reachable in this mode: Strobe; Colour (fixed: halogen blinder lamp, warmer when dimmed); Per cell / per zone control (2 zones).

## CSC BLINDER X4 — Quad Blinder

### Full — 9 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer | 0 | 0-255 = 0-100 % |
| 2 | Dimmer fine | 0 | fine byte of Dimmer |
| 3 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 4 | White | 0 | 0-255 = 0-100 % |
| 5 | CTO | 0 | 0-255 = 6500 K to 2700 K |
| 6 | White segment 1 | 0 | 0-255 (merged highest-takes-precedence with the main colour) |
| 7 | White segment 2 | 0 | 0-255 (merged highest-takes-precedence with the main colour) |
| 8 | White segment 3 | 0 | 0-255 (merged highest-takes-precedence with the main colour) |
| 9 | White segment 4 | 0 | 0-255 (merged highest-takes-precedence with the main colour) |

### Compact — 4 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer | 0 | 0-255 = 0-100 % |
| 2 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 3 | White | 0 | 0-255 = 0-100 % |
| 4 | CTO | 0 | 0-255 = 6500 K to 2700 K |

Not reachable in this mode: Per cell / per zone control (4 zones).

### Ultra Compact — 1 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer (halogen) | 0 | 0-255 = 0-100 % |

Not reachable in this mode: Strobe; Colour (fixed: halogen blinder lamp, warmer when dimmed); Per cell / per zone control (4 zones).

## CSC BLINDER X8 — Mega Blinder

### Full — 13 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer | 0 | 0-255 = 0-100 % |
| 2 | Dimmer fine | 0 | fine byte of Dimmer |
| 3 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 4 | White | 0 | 0-255 = 0-100 % |
| 5 | CTO | 0 | 0-255 = 6500 K to 2700 K |
| 6 | White segment 1 | 0 | 0-255 (merged highest-takes-precedence with the main colour) |
| 7 | White segment 2 | 0 | 0-255 (merged highest-takes-precedence with the main colour) |
| 8 | White segment 3 | 0 | 0-255 (merged highest-takes-precedence with the main colour) |
| 9 | White segment 4 | 0 | 0-255 (merged highest-takes-precedence with the main colour) |
| 10 | White segment 5 | 0 | 0-255 (merged highest-takes-precedence with the main colour) |
| 11 | White segment 6 | 0 | 0-255 (merged highest-takes-precedence with the main colour) |
| 12 | White segment 7 | 0 | 0-255 (merged highest-takes-precedence with the main colour) |
| 13 | White segment 8 | 0 | 0-255 (merged highest-takes-precedence with the main colour) |

### Compact — 4 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer | 0 | 0-255 = 0-100 % |
| 2 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 3 | White | 0 | 0-255 = 0-100 % |
| 4 | CTO | 0 | 0-255 = 6500 K to 2700 K |

Not reachable in this mode: Per cell / per zone control (8 zones).

### Ultra Compact — 1 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer (halogen) | 0 | 0-255 = 0-100 % |

Not reachable in this mode: Strobe; Colour (fixed: halogen blinder lamp, warmer when dimmed); Per cell / per zone control (8 zones).

## CSC FOLLOWSPOT F1 — Followspot

### Full — 11 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer | 0 | 0-255 = 0-100 % |
| 2 | Dimmer fine | 0 | fine byte of Dimmer |
| 3 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 4 | Red | 255 | 0-255 = 0-100 % |
| 5 | Green | 255 | 0-255 = 0-100 % |
| 6 | Blue | 255 | 0-255 = 0-100 % |
| 7 | Zoom | 89 | 0-255 = 3° to 20° |
| 8 | Focus | 0 | 0-255 = 0-100 % |
| 9 | Iris | 255 | 0 = smallest, 255 = open |
| 10 | Color wheel | 0 | 0-255 = 0-100 % |
| 11 | Douser | 255 | 0 = closed, 255 = open (linear transmission) |

### Compact — 7 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer | 0 | 0-255 = 0-100 % |
| 2 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 3 | Red | 255 | 0-255 = 0-100 % |
| 4 | Green | 255 | 0-255 = 0-100 % |
| 5 | Blue | 255 | 0-255 = 0-100 % |
| 6 | Zoom | 89 | 0-255 = 3° to 20° |
| 7 | Iris | 255 | 0 = smallest, 255 = open |

Not reachable in this mode: Focus; Color wheel; Douser.

### Ultra Compact — 4 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer | 0 | 0-255 = 0-100 % |
| 2 | Red | 255 | 0-255 = 0-100 % |
| 3 | Green | 255 | 0-255 = 0-100 % |
| 4 | Blue | 255 | 0-255 = 0-100 % |

Not reachable in this mode: Zoom; Focus; Iris; Strobe; Color wheel; Douser.

## CSC PIXELWASH W37 — Pixel Wash

### Full — 156 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Pan | 128 | 0-255 = -270° to +270° (128 = straight ahead) |
| 2 | Pan fine | 0 | fine byte of Pan |
| 3 | Tilt | 128 | 0-255 = -135° to +135° (128 = straight, along the base axis) |
| 4 | Tilt fine | 0 | fine byte of Tilt |
| 5 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 6 | CTO | 0 | 0-255 = 6500 K to 2700 K |
| 7 | Zoom | 89 | 0-255 = 6° to 60° |
| 8 | Motor speed | 0 | 0-255 = 0-100 % |
| 9 | Cell 1 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 10 | Cell 1 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 11 | Cell 1 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 12 | Cell 1 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 13 | Cell 2 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 14 | Cell 2 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 15 | Cell 2 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 16 | Cell 2 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 17 | Cell 3 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 18 | Cell 3 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 19 | Cell 3 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 20 | Cell 3 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 21 | Cell 4 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 22 | Cell 4 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 23 | Cell 4 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 24 | Cell 4 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 25 | Cell 5 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 26 | Cell 5 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 27 | Cell 5 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 28 | Cell 5 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 29 | Cell 6 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 30 | Cell 6 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 31 | Cell 6 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 32 | Cell 6 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 33 | Cell 7 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 34 | Cell 7 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 35 | Cell 7 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 36 | Cell 7 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 37 | Cell 8 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 38 | Cell 8 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 39 | Cell 8 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 40 | Cell 8 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 41 | Cell 9 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 42 | Cell 9 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 43 | Cell 9 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 44 | Cell 9 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 45 | Cell 10 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 46 | Cell 10 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 47 | Cell 10 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 48 | Cell 10 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 49 | Cell 11 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 50 | Cell 11 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 51 | Cell 11 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 52 | Cell 11 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 53 | Cell 12 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 54 | Cell 12 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 55 | Cell 12 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 56 | Cell 12 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 57 | Cell 13 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 58 | Cell 13 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 59 | Cell 13 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 60 | Cell 13 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 61 | Cell 14 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 62 | Cell 14 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 63 | Cell 14 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 64 | Cell 14 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 65 | Cell 15 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 66 | Cell 15 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 67 | Cell 15 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 68 | Cell 15 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 69 | Cell 16 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 70 | Cell 16 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 71 | Cell 16 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 72 | Cell 16 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 73 | Cell 17 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 74 | Cell 17 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 75 | Cell 17 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 76 | Cell 17 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 77 | Cell 18 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 78 | Cell 18 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 79 | Cell 18 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 80 | Cell 18 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 81 | Cell 19 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 82 | Cell 19 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 83 | Cell 19 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 84 | Cell 19 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 85 | Cell 20 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 86 | Cell 20 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 87 | Cell 20 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 88 | Cell 20 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 89 | Cell 21 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 90 | Cell 21 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 91 | Cell 21 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 92 | Cell 21 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 93 | Cell 22 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 94 | Cell 22 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 95 | Cell 22 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 96 | Cell 22 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 97 | Cell 23 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 98 | Cell 23 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 99 | Cell 23 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 100 | Cell 23 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 101 | Cell 24 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 102 | Cell 24 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 103 | Cell 24 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 104 | Cell 24 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 105 | Cell 25 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 106 | Cell 25 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 107 | Cell 25 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 108 | Cell 25 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 109 | Cell 26 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 110 | Cell 26 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 111 | Cell 26 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 112 | Cell 26 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 113 | Cell 27 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 114 | Cell 27 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 115 | Cell 27 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 116 | Cell 27 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 117 | Cell 28 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 118 | Cell 28 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 119 | Cell 28 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 120 | Cell 28 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 121 | Cell 29 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 122 | Cell 29 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 123 | Cell 29 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 124 | Cell 29 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 125 | Cell 30 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 126 | Cell 30 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 127 | Cell 30 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 128 | Cell 30 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 129 | Cell 31 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 130 | Cell 31 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 131 | Cell 31 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 132 | Cell 31 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 133 | Cell 32 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 134 | Cell 32 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 135 | Cell 32 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 136 | Cell 32 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 137 | Cell 33 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 138 | Cell 33 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 139 | Cell 33 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 140 | Cell 33 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 141 | Cell 34 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 142 | Cell 34 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 143 | Cell 34 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 144 | Cell 34 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 145 | Cell 35 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 146 | Cell 35 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 147 | Cell 35 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 148 | Cell 35 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 149 | Cell 36 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 150 | Cell 36 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 151 | Cell 36 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 152 | Cell 36 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 153 | Cell 37 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 154 | Cell 37 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 155 | Cell 37 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 156 | Cell 37 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |

### Compact — 9 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Pan | 128 | 0-255 = -270° to +270° (128 = straight ahead) |
| 2 | Tilt | 128 | 0-255 = -135° to +135° (128 = straight, along the base axis) |
| 3 | Dimmer | 0 | 0-255 = 0-100 % |
| 4 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 5 | Red | 255 | 0-255 = 0-100 % |
| 6 | Green | 255 | 0-255 = 0-100 % |
| 7 | Blue | 255 | 0-255 = 0-100 % |
| 8 | CTO | 0 | 0-255 = 6500 K to 2700 K |
| 9 | Zoom | 89 | 0-255 = 6° to 60° |

Not reachable in this mode: W; Motor speed; Per cell / per zone control (37 zones); 16-bit position (8-bit pan / tilt).

### Ultra Compact — 6 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Pan | 128 | 0-255 = -270° to +270° (128 = straight ahead) |
| 2 | Tilt | 128 | 0-255 = -135° to +135° (128 = straight, along the base axis) |
| 3 | Dimmer | 0 | 0-255 = 0-100 % |
| 4 | Red | 255 | 0-255 = 0-100 % |
| 5 | Green | 255 | 0-255 = 0-100 % |
| 6 | Blue | 255 | 0-255 = 0-100 % |

Not reachable in this mode: W; CTO; Zoom; Strobe; Motor speed; Per cell / per zone control (37 zones).

## CSC LASERCORE L1 — RGB Laser

### Full — 10 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer | 0 | 0-255 = 0-100 % |
| 2 | Dimmer fine | 0 | fine byte of Dimmer |
| 3 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 4 | Red | 255 | 0-255 = 0-100 % |
| 5 | Green | 255 | 0-255 = 0-100 % |
| 6 | Blue | 255 | 0-255 = 0-100 % |
| 7 | Douser | 255 | 0 = closed, 255 = open (linear transmission) |
| 8 | Laser pattern | 0 | 0-50 dot · 51-101 fan · 102-152 circle · 153-203 cross · 204-255 square |
| 9 | Scan size | 255 | 0-255 = 0-100 % |
| 10 | Scan speed | 128 | 0-255 = 0-100 % |

### Compact — 5 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer | 0 | 0-255 = 0-100 % |
| 2 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 3 | Red | 255 | 0-255 = 0-100 % |
| 4 | Green | 255 | 0-255 = 0-100 % |
| 5 | Blue | 255 | 0-255 = 0-100 % |

Not reachable in this mode: Douser; Laser pattern; Scan size; Scan speed.

### Ultra Compact — 4 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer | 0 | 0-255 = 0-100 % |
| 2 | Red | 255 | 0-255 = 0-100 % |
| 3 | Green | 255 | 0-255 = 0-100 % |
| 4 | Blue | 255 | 0-255 = 0-100 % |

Not reachable in this mode: Strobe; Douser; Laser pattern; Scan size; Scan speed.

## CSC FLOODLIGHT F8 — Architectural Floodlight

### Full — 34 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 2 | CTO | 0 | 0-255 = 6500 K to 2700 K |
| 3 | Cell 1 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 4 | Cell 1 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 5 | Cell 1 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 6 | Cell 1 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 7 | Cell 2 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 8 | Cell 2 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 9 | Cell 2 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 10 | Cell 2 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 11 | Cell 3 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 12 | Cell 3 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 13 | Cell 3 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 14 | Cell 3 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 15 | Cell 4 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 16 | Cell 4 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 17 | Cell 4 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 18 | Cell 4 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 19 | Cell 5 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 20 | Cell 5 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 21 | Cell 5 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 22 | Cell 5 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 23 | Cell 6 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 24 | Cell 6 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 25 | Cell 6 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 26 | Cell 6 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 27 | Cell 7 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 28 | Cell 7 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 29 | Cell 7 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 30 | Cell 7 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 31 | Cell 8 red | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 32 | Cell 8 green | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 33 | Cell 8 blue | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |
| 34 | Cell 8 white | 0 | 0-255 = 0-100 % (the cell itself: no master dimmer in this mode) |

### Compact — 7 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer | 0 | 0-255 = 0-100 % |
| 2 | Strobe | 0 | 0-7 open · 8-255 strobe 1 Hz to 25 Hz (capped by the desk safety) |
| 3 | Red | 255 | 0-255 = 0-100 % |
| 4 | Green | 255 | 0-255 = 0-100 % |
| 5 | Blue | 255 | 0-255 = 0-100 % |
| 6 | White | 0 | 0-255 = 0-100 % |
| 7 | CTO | 0 | 0-255 = 6500 K to 2700 K |

Not reachable in this mode: Per cell / per zone control (8 zones).

### Ultra Compact — 4 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer | 0 | 0-255 = 0-100 % |
| 2 | Red | 255 | 0-255 = 0-100 % |
| 3 | Green | 255 | 0-255 = 0-100 % |
| 4 | Blue | 255 | 0-255 = 0-100 % |

Not reachable in this mode: W; CTO; Strobe; Per cell / per zone control (8 zones).

## CSC PAR P64 — PAR Can (halogen)

### Full — 1 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer (halogen) | 0 | 0-255 = 0-100 % |

### Compact — 1 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer (halogen) | 0 | 0-255 = 0-100 % |

### Ultra Compact — 1 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer (halogen) | 0 | 0-255 = 0-100 % |

## CSC FRESNEL F1 — Fresnel (halogen)

### Full — 1 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer (halogen) | 0 | 0-255 = 0-100 % |

### Compact — 1 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer (halogen) | 0 | 0-255 = 0-100 % |

### Ultra Compact — 1 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer (halogen) | 0 | 0-255 = 0-100 % |

## CSC PC C1 — Plano-Convex (halogen)

### Full — 1 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer (halogen) | 0 | 0-255 = 0-100 % |

### Compact — 1 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer (halogen) | 0 | 0-255 = 0-100 % |

### Ultra Compact — 1 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer (halogen) | 0 | 0-255 = 0-100 % |

## CSC ELLIPSOIDAL E1 — Ellipsoidal Profile (halogen)

### Full — 1 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer (halogen) | 0 | 0-255 = 0-100 % |

### Compact — 1 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer (halogen) | 0 | 0-255 = 0-100 % |

### Ultra Compact — 1 channels

| Ch | Function | Default | Values |
|---:|---|---:|---|
| 1 | Dimmer (halogen) | 0 | 0-255 = 0-100 % |

## CSC VIRTUAL MATRIX — 16 channels

| Ch | Function |
|---:|---|
| 1 | Intensity |
| 2 | Colour A red |
| 3 | Colour A green |
| 4 | Colour A blue |
| 5 | Colour B red |
| 6 | Colour B green |
| 7 | Colour B blue |
| 8 | Speed |
| 9 | Movement |
| 10 | Movement size |
| 11 | Direction |
| 12 | Effect |
| 13 | Phase spread |
| 14 | Strobe |
| 15 | Tilt centre |
| 16 | Pan centre |
