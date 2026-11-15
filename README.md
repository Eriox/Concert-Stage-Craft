# 🎭 Concert Stage Craft

### Bring Your Concerts to Life in Minecraft!

**Concert Stage Craft (CSC)** is a Minecraft mod dedicated to realistic concert staging, professional lighting, audio systems, and live event production.

Build an outdoor music festival, an indoor arena, a DJ stage, or a full live production — and control the experience with in-game tools.

**Our mission is to make concert production in Minecraft more realistic, creative, and accessible to everyone.**

---

## 🚀 Concert Stage Craft 1.2.0 — LiveShow Revolution

**Version 1.2.0** expands Concert Stage Craft with lighting-show programming, music synchronization, external Art-Net control, LiveVision video production, new stage structures, and audio equipment.

### What's included in 1.2.0?

| System | Highlights |
|---|---|
| 🎛️ **LUMICORE X1** | In-game lighting desk with fixture patching, programming, effects, cues, playback, and show saving |
| 🎵 **AutoShow & Timeline** | Generate or program lighting cues synchronized with a linked CSC audio console |
| 🌐 **Art-Net Control** | Receive compatible external DMX data through the CSC ART-NET NODE |
| 📺 **LiveVision** | LED walls, cameras, video processor, live previews, and transitions |
| 🏗️ **Stage Structures** | Curved/circular trusses, angled barriers, and safety railings |
| 🔊 **AUREX Audio** | LA12/LA8 line arrays, S218-F subs, and portable speaker setups |
| ⚡ **Performance Options** | Adjustable beam quality, haze resolution, render range, and automatic performance scaling |

> **Note:** External console output is subject to the capabilities and licensing restrictions of the console software. CSC's Art-Net receiver does not unlock restricted DMX universes in dot2, grandMA2, or grandMA3.

---

## 📚 Documentation

### 📖 [Complete Tutorial — Concert Stage Craft 1.2.0](TUTORIAL.md)

The full step-by-step guide covers:

1. [Installation and updating](TUTORIAL.md#1-installing-or-updating)
2. [Your first show with the LUMICORE X1 desk](TUTORIAL.md#2-your-first-show-with-the-lumicore-x1-desk)
3. [Music synchronization and AutoShow](TUTORIAL.md#3-lights-on-the-music-and-autoshow)
4. [External console control with Art-Net](TUTORIAL.md#4-control-from-a-real-console--art-net)
5. [LED walls and cameras with LiveVision](TUTORIAL.md#5-led-walls-and-cameras--livevision)
6. [Trusses, barriers, and speakers](TUTORIAL.md#6-trusses-barriers-and-speakers)
7. [Settings and performance](TUTORIAL.md#7-settings-and-performance)
8. [Troubleshooting](TUTORIAL.md#8-common-problems)

**Using grandMA2?** The tutorial includes troubleshooting for Art-Net source filtering, sender IP addresses, and universe/address matching.

---

## 📥 Download

### 🎮 [Download Concert Stage Craft on CurseForge](https://www.curseforge.com/minecraft/mc-mods/concert-stage-craft)

| Information | Details |
|---|---|
| Minecraft | **1.20.1** |
| Mod loaders | **Forge 47+** or **Fabric Loader 0.15+ with Fabric API** |
| Java | **17** |
| Version covered here | **1.2.0** |
| Project status | **Active Development** |
| Download platform | **CurseForge** |

> Check the download page for the latest available builds and dependencies. Back up your world before updating. For multiplayer, the server and all players must use compatible CSC versions.

### Installation

1. Install **Minecraft 1.20.1** and **Java 17**.
2. Install the appropriate **Forge** or **Fabric** loader (and **Fabric API** for Fabric).
3. Download the matching CSC `.jar` from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/concert-stage-craft).
4. Remove any older CSC `.jar` from the `mods` folder, then add the new one.
5. Start Minecraft and begin building your show.

For external lighting consoles, the tutorial references `CSC_Gobos_et_Patchs_1.2.0.zip`, which contains fixture profiles and gobos.

---

## ✨ Features

### 🎤 Built for Live Entertainment

Create outdoor festivals, indoor arenas, DJ performances, theatres, and custom stage productions — from small shows to large festival setups.

### 🎛️ LUMICORE X1 — Lighting Desk

- Patch fixtures automatically or with the **CSC Light Patch Tool**.
- Program intensity, colour, pan/tilt, zoom, gobos, prisms, and strobe where supported.
- Apply **Circle, Wave, Fan, Chase, and Pulse** effects.
- Record cues, set fades and delays, and operate playback controls.
- Save up to **16 shows per desk** and export `.cscshow` files.
- Share desk access with other operators and use built-in safety controls.

### 🎵 Music-Synchronized Lighting & AutoShow

- Link the lighting desk to a CSC audio console.
- Place cues on a music timeline and arm them for playback.
- Generate editable AutoShow sequences using styles such as **Concert Pop, EDM Festival, Rock Live, and Ambient**.
- Run live music-reactive lighting, with manual programming taking priority.

### 🌐 Art-Net & External DMX Consoles

- Receive Art-Net through the **CSC ART-NET NODE** on UDP port `6454`.
- Patch fixtures by universe, address, and DMX mode.
- Choose **Full**, **Compact**, or **Ultra Compact** fixture modes.
- Use matching fixture profiles for supported external lighting software.
- Diagnose source filtering and incoming packets in the node interface.

### 📺 LiveVision — Video Production

- Assemble LED walls using **CSC LED PANEL P1** panels.
- Route sources through the **CSC VISION PROCESSOR VP1**.
- Use camera models including **CAM PTZ P1**, **BROADCAST B1**, **CRANE C1**, and **REMOTE R1**.
- Switch between **PREVIEW** and **PROGRAM**, with **CUT**, **DISSOLVE**, and **FADE** transitions.
- Display supported image and video files with the required media setup.

### 🏗️ Professional Stage Construction

- Build with curved and circular **QuadTruss** and **TriTruss** sections.
- Configure truss radius, angle, direction, and finish.
- Install angled barriers and safety railings.
- Mount fixtures on compatible stage structures.

### 🔊 AUREX Speaker Systems

- **LA12** and **LA8** line arrays with flying and ground frames.
- **S218-F** subwoofer stacks.
- Portable **B18P** subwoofer, pole, and **C6** speaker configurations.
- Adapters for compatible **Orvane**, **Veyra**, and **Noryx** systems.

### ⚡ Adjustable Graphics & Performance

- Tune beam range, beam quality, and haze resolution.
- Adjust CSC block render distance and LiveVision quality.
- Enable automatic beam-detail reduction when frame rates drop.
- CSC's own block-lighting system does not require Shimmer.

---

## 🔧 Compatibility

Concert Stage Craft 1.2.0 targets **Minecraft 1.20.1** with **Forge** or **Fabric**.

### Integration with Other Mods

**Theatrical** and **Theatrical ExtraLights** may be used alongside other stage-building tools, but specific integrations can vary by version and may still be in development.

**Shimmer is optional for CSC's own lighting system in 1.2.0**, according to the version tutorial.

---

## 🐛 Bug Reports & Feature Requests

Found a bug or have an idea for a new feature? Open an issue in this repository's **Issues** section.

Please include:

- Minecraft version.
- Forge or Fabric version.
- Concert Stage Craft version.
- Other installed mods.
- A clear description and steps to reproduce.
- Screenshots or videos, when helpful.
- `logs/latest.log` and crash logs, if applicable.

For common problems, see the [troubleshooting section](TUTORIAL.md#8-common-problems).

---

## ❤️ Support the Project

Concert Stage Craft is an independent Minecraft mod created with a passion for concerts, professional lighting, audio, and live entertainment.

Every download, suggestion, bug report, and piece of feedback helps the project grow.

**A huge thank you to everyone who downloads and supports Concert Stage Craft!**

---

## 🔗 Official Links

- 📦 **[CurseForge — Official Download](https://www.curseforge.com/minecraft/mc-mods/concert-stage-craft)**
- 📖 **[Complete 1.2.0 Tutorial](TUTORIAL.md)**
- 🐛 **Bug Reports & Suggestions:** Use the **Issues** section of this GitHub repository.

---

### 🎭 Concert Stage Craft

**Create the stage. Control the lights. Bring the show to life.**

*Made with passion for Minecraft concerts and live events.*
