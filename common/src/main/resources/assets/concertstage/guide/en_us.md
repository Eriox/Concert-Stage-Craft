# Concert Stage Craft guide

## 1. Before you start

**Shimmer is not needed.** CSC lights the blocks, trusses and tarps itself. It looks the same with or without Shimmer.

**FFmpeg is built in** (1.2.1): LED walls play videos, GIFs and images with nothing else to install.

On a server, the server and every player must run the same version of the mod.

To control the lights from a real console, download `CSC_Gobos_et_Patchs` from the mod page: it holds the fixture profiles and gobos (part 4).

## 2. Your first show with the LUMICORE X1 desk

In five minutes: place, patch, program, record a cue, press GO.

### Place the gear

1. From the **CSC Lighting** tab, take some fixtures, a **CSC LUMICORE X1** desk and the **CSC Light Patch Tool**.
2. Place fixtures on the floor, or hang them: aim at the underside of a truss or a block. A fixture placed against the side of a block with something above it and nothing below is hung too.
3. Place the desk (2 m wide). The player who places it owns it.

### Place a fixture anywhere

- **Sneak while placing**: the fixture goes exactly where you aim, on a tube, an edge or the top of any block, turned the way you look.
- **Sneak and click the side of a block**: the fixture is mounted on that side, its beam straight out of the wall.
- Without sneaking, a standing fixture sits on the real top of what is under it (a slab, a truss).
- **Fine tuning**: right-click the fixture with the **Fixture Angle Tool** (on a blinder or a bar, click **Position…** in its angle screen). The position screen moves it up to 2 blocks each way (X, Y, Z) and turns it (yaw, pitch, roll). **Copy** keeps the position in the tool; sneak + right-click another fixture pastes it.
- The desk aims a moved fixture from its real place.

### Patch

- Easiest: on the desk, **Fixtures** tab, **Auto-patch (48 blocks)**. Every free fixture around is added and numbered.
- By hand, with the **Patch Tool**: right-click the desk to pick it, then right-click each fixture. Sneak to unpatch.
- Right-click a fixture with an empty hand: its patch screen (desk, number, address, DMX mode).

### Program

1. **Select** fixtures (Fixtures tab, or keys 1 to 9 for your groups).
2. **Programmer** tab: intensity, position, zoom, gobo, prism, strobe… Only the settings these fixtures have are shown. Right-click a slider to release its value.
3. **Colour** tab (wheel, RGB sliders, saved colours) and **Position** tab (pan / tilt pad). **Create standard positions** works out stage centre, audience, upstage, cross… from where each fixture really is.
4. **Effects** tab: Circle, Wave, Fan, Chase, Pulse… then "Run on selection".

### Record and play

1. **Cues** tab, **Record** (R key): the programmer becomes the next cue. Set its fade, delay and follow.
2. **Playback** tab: **GO** (Space), Back, Pause, Stop, Flash, faders. Grand Master and Blackout are at the bottom of the screen.
3. **Shows** tab: save your show (16 per desk), export it as `.cscshow`, undo with Ctrl+Z.

### Aim with the Focus Tool

Right-click fixtures to select them, then aim where you want them to point. Shift + right-click in the air clears the selection and hands the fixtures back to the desk.

### Conventionals (PAR, Fresnel, PC, ellipsoidal)

- **CSC PAR P64**, **CSC FRESNEL F1** (soft edge), **CSC PC C1** and **CSC ELLIPSOIDAL E1** (hard edge): halogen fixtures with **one dimmer channel** in every DMX mode.
- No motor: aim them with the **Fixture Angle Tool** (right-click), which also slides a **gel** in front of the lens (10 colours).
- Patch them like the others: the console's dimmer is their only control.

### Blinders and fixtures without a motor

Right-click a blinder: a screen with a protractor sets its angle. **Copy** keeps the angle, and sneak + right-click on another blinder pastes it.

### Share the desk

Shows tab: add operators by name, or make the desk public. The safety buttons (BLACKOUT, STOP SHOW, NO STROBE, MOVE LIMIT, HOME, STOP AUTO) are always at the bottom of the screen.

## 3. Lights on the music and AutoShow

The lights follow the linked CSC audio console, on the same clock as the sound, for every player.

### Add music

1. On the audio console, **Import MP3 / WAV…**: tick some files, then **Import selection** or **Import all**. Dragging and dropping several files works too.
2. On the LUMICORE, Fixtures tab: **Link the nearest audio console**.

### Place cues on the track (Timeline)

- **Timeline** tab: "Take the current track", then Shift + click to place a GO of the chosen cue. Drag to move, right-click to delete.
- A click previews the lights at that moment, without music. Set the timeline to **ARMED** so that it runs with the music.
- "Your light delay" moves your own lights earlier or later, if your sound arrives late.

### Let AutoShow build the show

1. Play a track on the linked audio console.
2. **AutoShow** tab: pick a style (Concert Pop, EDM Festival, Rock Live, Ambient, Custom), intensity, movement, colours, maximum strobe, or no flashes at all.
3. **Analyse the track and generate**: you get an "AutoShow" cue list and its timeline. It's a normal show that you can edit and save.
4. Or **Start AutoShow live**: it follows the music as it plays.
5. **STOP AUTO** stops it at once. Your manual programming always wins.

The analysis is an estimate: it doesn't recognise every song structure perfectly. Touch up the result if needed.

## 4. Control from a real console (Art-Net)

A lighting console or software (grandMA, dot2, QLC+, Onyx…) can control the fixtures over Art-Net, in four steps.

| Item | What it does |
| --- | --- |
| **CSC ART-NET NODE** (block) | Receives Art-Net. The fixtures patched on it follow your console. |
| **Configuration card** (item) | Gives each fixture its address when you click it. |
| Right-click a fixture with an empty hand | "DMX Patch" screen: universe, address, mode, "Next free". |

1. **Place the node** and right-click it. Enter the IP of the PC running your console (or "Any sender"), port **6454** and the universes received. **Apply and keep**. The real status shows: LISTENING, RECEIVING, SOURCE FILTERED…
2. **Set up the card**: right-click in the air with the card. Choose the universe, DMX mode, start address and auto increment, then **Save**.
3. **Link the card**: right-click the node with the card.
4. **Patch**: right-click each fixture with the card. The card moves to the next address by itself and warns you if two fixtures overlap.

### DMX modes

- **Full**: every channel. On pixel fixtures, each cell has its own colour and intensity.
- **Compact** and **Ultra Compact**: fewer channels, to fit many fixtures in one universe.
- Colour is red / green / blue in every mode. The channel count of each mode is in `DMX_CHARTS.md`.
- Moving head tilt: DMX 128 = straight (pointing down when the fixture is hung), ±135° around it.

### Profiles for your console

In `CSC_Gobos_et_Patchs_1.2.0.zip`: GDTF (grandMA3, Capture, Vectorworks…), grandMA2, dot2, QLC+ and ChamSys MagicQ profiles, with the gobos. Import the profile of the **same mode** as the one chosen on the card, or the addresses won't match.

### If nothing moves

First check that the **Art-Net output is enabled** on your console: on grandMA2 and grandMA3, a disabled output line sends nothing at all.

- Check the status on the node. SOURCE FILTERED: the IP you entered is not your console's.
- Nothing shows at all: allow Java (Minecraft) through the Windows firewall for UDP port 6454.
- "External control: OFF" on the node hands control back to the in-game desk programmer.

### grandMA2 / grandMA3

- These consoles send Art-Net from a **2.x.x.x** address built on your PC's IP (2.168.178.61 for 192.168.178.61). Since 1.2.1 the node accepts it on its own.
- If the node still shows "SOURCE FILTERED", click **Accept this sender** under the refused address, or **Any sender**, then **Apply & save**.
- On grandMA2, leave **Network** at 1 (its lowest value), with Subnet 0 and Universe 0.
- Patch the fixture in Minecraft on universe 0 at the same address as in grandMA (1.001 = universe 0, address 1), in the same DMX mode as the imported profile.

## 5. LED walls and cameras (LiveVision)

An LED wall, a processor and a camera are all you need to show your stage live on a screen.

### Build an LED wall

1. Place **CSC LED PANEL P1** panels side by side, in one plane and facing the same way. They form **one single wall** on their own (96 × 96 panels at most).
2. Place a **CSC VISION PROCESSOR VP1**: it takes the nearby walls and cameras. A wall links itself to the nearest processor.
3. Right-click a panel: name, brightness, processor and output (OUT 1 to 4), "Source ▸" button.
4. Accessories: right-click a panel holding the hanging bar, the ground base or the wall mount.

### The control room (right-click the VP1)

- **PREVIEW** (green) and **PROGRAM** (red) monitors for each of the 4 outputs.
- Click a source to put it in preview, Shift + click to send it straight to program.
- **TAKE** sends the preview to program with the chosen transition (CUT, DISSOLVE, FADE). **CUT** switches instantly.
- Automatic switching between cameras, every 2 to 120 s.

### Cameras

- CAM PTZ P1, BROADCAST B1, CRANE C1, REMOTE R1. The PTZ and the Remote hang under a block.
- Right-click: pan, tilt, zoom, and 8 positions (click to recall, Shift + click to store).
- **Quality** (Auto, 360p to 1080p) and **FPS** (Auto, 15 to 60) buttons. A fixed value holds even if the game slows down. Stay on Auto if your PC struggles.
- A newly placed camera goes to output 1.

### Your images and videos

- **Images** (PNG, JPG, animated GIF): in `<world>/csc_livevision/media/`. The server sends them to players (16 MB per file at most).
- **Videos** (MP4, MOV, WEBM, MKV, AVI…): every player needs the file, under the same name, in `config/concertstage/livevision/media/`. FFmpeg is built into the mod: nothing to install.

## 6. Trusses, barriers and speakers

### Curved and circular trusses

- **CSC Curved QuadTruss** and **CSC Curved TriTruss**: radius 1 to 20 m, angle 5 to 355°, curving left, right, up or down, aluminium or black finish. Set them up by right-clicking with the Ring tool.
- **Circular truss**: diameter 2 to 20 m, QUAD or TRI section. Your existing circles load as they are.
- Fixtures hang on them and lifts work as on the other trusses.

### Barriers and safety railings

- **Angled barriers** (CrowdGuard, CityLine, AluGuard, SiteFence fences): place them with the angle tool, like trusses. An angled barrier has no collision: you can walk through it.
- **Railings on an angled platform**: they attach to the nearest edge and leave with the platform.
- **Railings on the ground**: straight and corner railings meet on one line. If a corner doesn't close the angle, turn around and place it again: its direction depends on where you're looking.

### New AUREX speakers

- **LA12** and **LA8** line arrays: flown with their fly frame under a structure or a hoist, on the ground with their ground frame, or on a stack of S218-F subs.
- **Adapters** to stand the Orvane, Veyra and Noryx line arrays on their own brand's subs.
- **Portable system**: one B18P sub, a pole, and up to 2 C6 speakers.
- You can no longer add or remove a sub under a stack that carries something. Remove what's on top first.
- These speakers have no crafting recipe: creative mode or `/give`.

## 7. Settings and performance

These settings only change your own screen, never the show for others.

| Where | Setting | What it does |
| --- | --- | --- |
| **K** key (or Mods menu > Concert Stage Craft > Config on Forge) | Beam range (25 to 300 %) | Length of the beams and reach of their light on blocks |
| **K** key | Block distance (25 to 200 %) | Render distance of the mod's blocks |
| **K** key > LiveVision… | LOW to ULTRA profile | LED wall distance, camera size and frame rate, media memory |
| LUMICORE, Shows tab | Beam quality | Low, Medium, High |
| LUMICORE, Shows tab | Auto performance | Lowers beam detail when the frame rate drops |
| LUMICORE, Shows tab | Haze, Haze res. | How visible beams are in the air, and their resolution |

### If your game slows down

1. Look at "T:" in the F3 screen: it is Minecraft's frame rate limit. At 20 the game never goes above 20 frames per second: raise **Max Framerate** in Options > Video.
2. Keep **Auto performance** on.
3. Set beam quality to Medium or Low, and haze resolution to Half.
4. Lower the beam range and the LiveVision profile (fewer active cameras, fewer frames per second).
5. Leave cameras on **Auto** quality and FPS.

The "Shimmer light" setting is no longer used: CSC lights the blocks itself, with or without Shimmer.

## 8. Common problems

| Problem | Fix |
| --- | --- |
| Can't join the server | The server and the player must run the same version of the mod. |
| A fixture doesn't light up | Check that it's patched (right-click it with an empty hand), that the Grand Master is up and that Blackout is off. |
| A player can't see the lights | They must be within 768 blocks of the desk. Fixtures don't show beyond Minecraft's render distance. |
| Beams stop too early | Raise the beam range (K key). |
| Fabric: no settings button in the Mods menu | Use the **K** key. |
| The external console controls nothing | Check the status on the ART-NET NODE, the sender IP and the firewall (part 4). |
| Addresses don't match on the external console | The imported profile must be the same mode (Full, Compact, Ultra Compact) as on the configuration card. |
| Camera shows "NO SIGNAL" or only the sky | Wait about ten seconds: the camera retries on its own. |
| A video doesn't play | Every player needs the file, under the same name, in `config/concertstage/livevision/media/` (part 5). |
| Can't remove a sub | A stack carrying something is locked: remove what's on top first. |
| The game is stuck at 20 FPS | "T: 20" in F3: raise Max Framerate in Options > Video (part 7). |
| A corner railing doesn't close the angle | Break it, turn around and place it again. |
| A wash or spot answers in hue / saturation instead of RGB | A show from before 1.2.0 kept its old modes; 1.2.1 moves them to RGB when it loads. If a fixture still does, repatch it with the card (same mode). |
| The node shows "SOURCE FILTERED" | Click "Accept this sender" on the node (part 4). |
| The node receives no packets at all | Enable the Art-Net output on your console (grandMA2: Setup > Network Protocols > Art-Net; grandMA3: Menu > DMX Protocols > Art-Net). |

Another issue? Note your Forge or Fabric version and your other mods, and send the `logs/latest.log` file with your message.
