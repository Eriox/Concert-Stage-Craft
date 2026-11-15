# Concert Stage Craft 1.2.0 — Complete Tutorial

**November 15, 2026 · @Eriox**

Welcome to the official **Concert Stage Craft 1.2.0** tutorial!

Learn how to install the mod, build your first lighting show, synchronize lights with music, connect external DMX consoles, configure LiveVision, and optimize performance.

## Table of Contents

1. [Installing or Updating](#1-installing-or-updating)
2. [Your First Show with the LUMICORE X1 Desk](#2-your-first-show-with-the-lumicore-x1-desk)
3. [Lights on the Music and AutoShow](#3-lights-on-the-music-and-autoshow)
4. [Control from a Real Console — Art-Net](#4-control-from-a-real-console--art-net)
5. [LED Walls and Cameras — LiveVision](#5-led-walls-and-cameras--livevision)
6. [Trusses, Barriers and Speakers](#6-trusses-barriers-and-speakers)
7. [Settings and Performance](#7-settings-and-performance)
8. [Common Problems](#8-common-problems)

---

## 1. Installing or Updating

Concert Stage Craft **1.2.0** runs on:

| Requirement | Version |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47 or later |
| Fabric Loader | 0.15 or later |
| Fabric API | Required for Fabric |
| Java | 17 |

### Installation

1. **Back up your world** before updating.
2. Remove the old CSC JAR from your `mods` folder.
3. Install the appropriate version:
   - Forge: `concertstage-forge-1.20.1-1.2.0.jar`
   - Fabric: `concertstage-fabric-1.20.1-1.2.0.jar`
4. On multiplayer servers, install **1.2.0 on both the server and every client**.

> **Important:** The network protocol has changed. Older versions cannot connect to a 1.2.0 server.

### Lighting compatibility

**Shimmer is not required.** CSC lights blocks, trusses, and tarps using its own system, with the same appearance whether Shimmer is installed or not.

For external lighting consoles, also download:

`CSC_Gobos&Patchs_1.2.0.zip`

This package contains fixture profiles and gobos.

---

## 2. Your First Show with the LUMICORE X1 Desk

**Place → Patch → Program → Record → GO**

### Place the equipment

1. Open the **CSC Lighting** creative tab.
2. Take some fixtures, a **CSC LUMICORE X1** desk, and the **CSC Light Patch Tool**.
3. Place fixtures on the floor or hang them from a truss or block by aiming at its underside.
4. A fixture placed against the side of a block also hangs if there is something above it and nothing below.
5. Place the LUMICORE X1 desk (2 meters wide).

The player who places the desk becomes its owner.

### Patch fixtures

**Automatic patching**

1. Open the desk.
2. Go to the **Fixtures** tab.
3. Click **Auto-patch (48 blocks)**.

All free fixtures within range are automatically added and numbered.

**Manual patching**

1. Right-click the desk using the **Light Patch Tool**.
2. Right-click each fixture to patch it.
3. Sneak while using the tool to unpatch a fixture.

Right-click a fixture with an empty hand to open its patch screen and configure its desk, fixture number, address, and DMX mode.

### Program your lights

1. Select fixtures in the **Fixtures** tab or use keys **1–9** for your groups.
2. Open **Programmer** to control intensity, position, zoom, gobo, prism, strobe, and other supported attributes.
3. Right-click a slider to release its value.
4. Use **Colour** for the colour wheel, RGB sliders, and saved colours.
5. Use **Position** for the pan/tilt pad.

**Create standard positions** calculates stage centre, audience, upstage, cross, and other positions based on the fixtures' actual locations.

### Add effects

Open **Effects**, choose an effect, and click **Run on selection**.

Available effects include:

- Circle
- Wave
- Fan
- Chase
- Pulse

### Record and play cues

1. Open **Cues**.
2. Click **Record** or press **R**.
3. Set the cue's fade, delay, and follow settings.
4. Open **Playback**.
5. Press **GO** or **Space** to run the cue.

Playback controls include **GO, Back, Pause, Stop, Flash**, and faders.

**Grand Master** and **Blackout** are at the bottom of the screen.

### Save your show

In the **Shows** tab:

- Save up to **16 shows per desk**.
- Export a show as `.cscshow`.
- Undo with **Ctrl+Z**.

### Focus Tool

Right-click fixtures to select them, then aim where you want them to point.

**Shift + right-click in the air** clears the selection and returns control to the desk.

### Blinders and non-motorized fixtures

Right-click a blinder to open its angle control.

- **Copy** saves the angle.
- **Sneak + right-click** another blinder to paste the angle.

### Sharing the desk

In **Shows**, add operators by name or make the desk public.

Safety controls are always available:

`BLACKOUT` · `STOP SHOW` · `NO STROBE` · `MOVE LIMIT` · `HOME` · `STOP AUTO`

---

## 3. Lights on the Music and AutoShow

The lighting system can synchronize with a linked CSC audio console using the same playback clock for all players.

### Import music

1. Open the CSC audio console.
2. Select **Import MP3 / WAV…**.
3. Choose files and click **Import selection** or **Import all**.

You can also drag and drop multiple files.

Then open **LUMICORE → Fixtures** and click **Link the nearest audio console**.

### Create a timeline

1. Open **Timeline**.
2. Click **Take the current track**.
3. Use **Shift + click** to place a GO command for the selected cue.
4. Drag events to move them.
5. Right-click an event to delete it.

Clicking the timeline previews the lights at that moment without playing the music.

Set the timeline to **ARMED** to run it with playback.

**Your light delay** adjusts the timing of your own lights if the sound arrives late.

### Generate an AutoShow

1. Play a track on the linked audio console.
2. Open **AutoShow**.
3. Choose a style:
   - Concert Pop
   - EDM Festival
   - Rock Live
   - Ambient
   - Custom
4. Configure intensity, movement, colours, and maximum strobe, or disable flashes entirely.
5. Click **Analyse the track and generate**.

The system creates an editable AutoShow cue list and timeline.

Alternatively, click **Start AutoShow live** to follow the music during playback.

**STOP AUTO** immediately stops AutoShow. Manual programming always takes priority.

> **Note:** Music analysis is an estimate and may not recognize every song structure perfectly. Adjust the generated show when necessary.

---

## 4. Control from a Real Console — Art-Net

Concert Stage Craft can receive Art-Net data from compatible lighting consoles and software, including **grandMA, dot2, QLC+, and Onyx**, subject to their output capabilities and licensing restrictions.

### Required equipment

| Item | Purpose |
|---|---|
| CSC ART-NET NODE | Receives Art-Net data from your console |
| Configuration Card | Assigns DMX addresses to fixtures |
| Fixture DMX Patch screen | Configures universe, address, and mode |

### Step 1 — Configure the Art-Net Node

1. Place a **CSC ART-NET NODE**.
2. Right-click the node.
3. Enter the IP address of the computer running your lighting console, or select **Any sender**.
4. Use UDP port **6454**.
5. Configure the received universes.
6. Click **Apply & save**.

The node displays statuses such as:

- `LISTENING`
- `RECEIVING`
- `SOURCE FILTERED`

### Step 2 — Configure the Card

1. Right-click in the air while holding the Configuration Card.
2. Choose the universe.
3. Select the DMX mode.
4. Set the starting address.
5. Configure automatic address increment.
6. Save.

### Step 3 — Link the Card

Right-click the Art-Net Node with the Configuration Card.

### Step 4 — Patch Fixtures

Right-click each fixture with the card.

The card automatically advances to the next address and warns about overlapping fixtures.

### DMX modes

| Mode | Description |
|---|---|
| Full | All available channels; independent colour and intensity for each cell on pixel fixtures |
| Compact | Reduced channel count |
| Ultra Compact | Further reduced channel count |

RGB channels use **Red / Green / Blue** in every mode.

See `DMX_CHARTS.md` for channel counts.

**Moving-head tilt:** DMX 128 represents the straight position (pointing down when hung), with a ±135° range around it.

### Import fixture profiles

The `CSC_Gobos_et_Patchs_1.2.0.zip` package contains profiles for:

- GDTF (grandMA3, Capture, Vectorworks, and compatible applications)
- grandMA2
- dot2
- QLC+
- ChamSys MagicQ

It also includes gobos.

> **Important:** Import the profile matching the fixture's selected DMX mode. Otherwise, channel addresses will not match.

### Troubleshooting Art-Net

**SOURCE FILTERED**

The configured sender IP does not match the incoming source.

Check the IP filter or select **Any sender**.

**No Art-Net data**

Allow Java/Minecraft through the Windows firewall for **UDP port 6454**.

**Return to in-game desk control**

Set **External control: OFF** on the node.

### grandMA2 — SOURCE FILTERED or No Data Received

When using grandMA2, the Art-Net sender address may appear in the `2.x.x.x` range rather than using the PC's normal local IP.

For example:

| PC IP | Possible Art-Net sender IP |
|---|---|
| `192.168.178.61` | `2.168.178.61` |

This can cause the CSC Art-Net Node to reject incoming packets.

**Fix:**

1. Open the CSC Art-Net Node.
2. Click **Any sender**, or enter the address displayed after **Refused (IP filter)** as the **Application IP**.
3. Click **Apply & save**.
4. In grandMA2, open **Setup → Network Protocols → Art-Net**.
5. Leave **Network** at `1` (the lowest available value), **Subnet** at `0`, and **Universe** at `0`.
6. If packets are counted as **not received** instead of **accepted**, configure the CSC node to receive Art-Net universes `0–271`.
7. On the Full desk, open **Settings → Network → Art-Net** and identify the incoming **Port-Address**.
8. In Minecraft, patch the fixture on **universe 0**, using the same DMX address and mode as in grandMA2.

Example:

`grandMA2 1.001 → CSC universe 0, address 1`

The node's **Packets accepted** counter should increase when valid data is received.

> **Note:** Art-Net reception does not bypass the DMX output or parameter licensing restrictions of MA Lighting software.

---

## 5. LED Walls and Cameras — LiveVision

LiveVision allows you to display stage footage on LED walls using cameras and a video processor.

### Build an LED wall

1. Place **CSC LED PANEL P1** panels next to each other.
2. Keep the panels in one plane, facing the same direction.
3. Panels automatically form one wall, up to **96 × 96 panels**.
4. Place a **CSC VISION PROCESSOR VP1** near the wall.

The wall automatically links to the nearest processor.

Right-click a panel to configure:

- Name
- Brightness
- Processor
- Output (`OUT 1` to `OUT 4`)
- Source

Accessories include hanging bars, ground bases, and wall mounts.

### Video control room

Right-click the VP1 processor.

Each of the four outputs has:

- **PREVIEW** monitor (green)
- **PROGRAM** monitor (red)

Click a source to send it to Preview.

**Shift + click** sends it directly to Program.

Use **TAKE** to apply the selected transition:

- CUT
- DISSOLVE
- FADE

**CUT** switches instantly.

Automatic camera switching can be configured between **2 and 120 seconds**.

### Cameras

Available cameras:

- CAM PTZ P1
- BROADCAST B1
- CRANE C1
- REMOTE R1

PTZ and Remote cameras can hang beneath blocks.

Right-click a camera to configure:

- Pan
- Tilt
- Zoom
- Eight stored positions

Click a position to recall it.

**Shift + click** stores the current position.

### Camera quality

| Setting | Available values |
|---|---|
| Quality | Auto, 360p to 1080p |
| FPS | Auto, 15 to 60 |

A fixed value is maintained even if game performance decreases.

**Recommended:** Use Auto on lower-end PCs.

New cameras are assigned to **Output 1** by default.

### Images and videos

**Images:** PNG, JPG, and animated GIF.

Place image files in:

```text
<world>/csc_livevision/media/
```

The server distributes these files to players, with a maximum size of **16 MB per file**.

**Videos:** MP4, MOV, WEBM, MKV, and other supported formats.

Every player must have the video under the same filename in:

```text
config/concertstage/livevision/media/
```

FFmpeg must be installed on the system PATH or available at:

```text
config/concertstage/livevision/ffmpeg.exe
```

---

## 6. Trusses, Barriers and Speakers

### Curved and circular trusses

**CSC Curved QuadTruss** and **CSC Curved TriTruss** support:

- Radius: **1–20 m**
- Angle: **5–355°**
- Curving left, right, up, or down
- Aluminium or black finish

Configure them by right-clicking with the **Ring Tool**.

**Circular trusses** support:

- Diameter: **2–20 m**
- QUAD or TRI sections

Existing circular trusses retain their saved configurations.

Fixtures and lifts work on these structures as they do on other trusses.

### Barriers and safety railings

Angled barriers include:

- CrowdGuard
- CityLine
- AluGuard
- SiteFence

Place them using the angle tool.

> **Note:** Angled barriers have no collision, so players can walk through them.

Railings on angled platforms attach to the nearest edge and are removed with the platform.

Ground railings include straight and corner sections.

If a corner does not connect correctly, turn around and place it again. Its orientation depends on where you are looking.

### New AUREX speakers

**LA12 and LA8 line arrays** can be installed:

- Using a fly frame beneath a structure or hoist.
- On the ground using a ground frame.
- On a stack of S218-F subwoofers.

Adapters are available for placing **Orvane, Veyra, and Noryx** line arrays on their respective subwoofers.

### Portable sound system

Combine:

- One **B18P** subwoofer.
- One pole.
- Up to **two C6** speakers.

### Speaker placement restrictions

A subwoofer cannot be added to or removed from a stack carrying another object.

Remove the equipment above it first.

These speakers have no crafting recipes and are available through Creative mode or `/give`.

Existing line arrays adopt their updated dimensions. Check for collisions with nearby objects after updating.

---

## 7. Settings and Performance

These settings affect only your own display, not other players' shows.

| Location | Setting | Description |
|---|---|---|
| K key / Forge Mods menu | Beam range (25–300%) | Beam length and lighting reach |
| K key | Block distance (25–200%) | CSC block render distance |
| K → LiveVision | LOW to ULTRA | LED wall distance, camera resolution, FPS, and media memory |
| LUMICORE → Shows | Beam quality | Low, Medium, High |
| LUMICORE → Shows | Auto performance | Automatically reduces beam detail when FPS drops |
| LUMICORE → Shows | Haze / Haze resolution | Beam visibility in the air and rendering resolution |

### Recommended settings for better FPS

1. Keep **Auto performance** enabled.
2. Set **Beam quality** to Medium or Low.
3. Set **Haze resolution** to Half.
4. Reduce **Beam range**.
5. Lower the LiveVision quality profile.
6. Reduce the number of active cameras and their frame rates.
7. Keep cameras on automatic quality and FPS.

The old **Shimmer light** setting is no longer used. CSC handles block lighting independently of Shimmer.

---

## 8. Common Problems

| Problem | Solution |
|---|---|
| Cannot join a server | Ensure the server and every client use CSC 1.2.0 |
| A fixture does not light up | Check patching, Grand Master, and Blackout |
| Another player cannot see the lights | Stay within 768 blocks of the desk and within Minecraft's render distance |
| Beams stop too early | Increase Beam range using the K key |
| No Fabric settings button in Mods menu | Use the K key |
| External console does not control fixtures | Check Art-Net Node status, sender IP, and firewall |
| External console addresses do not match | Use the same DMX mode in Minecraft and the console profile |
| A beta show retains an old DMX mode | Repatch fixtures to use the current RGB mode |
| Camera shows NO SIGNAL or only the sky | Wait about 10 seconds for automatic retry |
| Video does not play | Ensure every player has the file and FFmpeg is installed |
| Cannot remove a subwoofer | Remove the equipment stacked above it first |
| Corner railing does not close the angle | Break it, turn around, and place it again |
| grandMA2 shows SOURCE FILTERED | Select Any sender on the Art-Net Node, then Apply & save |

With Embeddium or Sodium, CSC builds the terrain visible in camera images itself.

### Reporting bugs

If you encounter another issue, include:

- Minecraft version
- Forge or Fabric version
- Concert Stage Craft version
- Installed mods
- A description of the issue
- Your `logs/latest.log` file

---

**Concert Stage Craft 1.2.0**

*Build your stage. Program your lights. Create your show.*
