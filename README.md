# ✦ DLXhub

<div align="center">

**Fast • Private • Reliable**

*A modern Android media downloader focused on fast downloads, clean organization, flexible storage, and an easy mobile experience.*

</div>

---

## 📌 Overview

**DLXhub** is an Android media downloader that lets you save supported online media as **video or audio**, choose available quality options, process multiple links, and organize downloaded files automatically.

The app is designed around a straightforward workflow:

```text
Paste Link
    ↓
Resolve Media
    ↓
Choose Quality / Format
    ↓
Download
    ↓
Save to Selected Folder
```

For multiple items, the same workflow can handle **batch downloads** and **playlists** while preserving meaningful media names and organized folder structures.

---

## ✨ Features

| Feature | Description |
|:--|:--|
| **🎬 Video Downloads** | Download available video streams with selectable quality options. |
| **🎵 Audio Downloads** | Save supported media as audio with available quality options. |
| **📋 Playlist Downloads** | Download an entire playlist and organize its contents automatically. |
| **⚡ Batch Downloads** | Process multiple media links together instead of downloading them one by one. |
| **📊 Live Progress** | View download progress, status, speed and remaining information in real time. |
| **⏳ Background Downloads** | Downloads can continue while the app is running in the background. |
| **📁 Custom Destination** | Select a preferred folder and change it whenever needed. |
| **🔐 Cookie Profiles** | Store authentication cookies locally for sources that require an authenticated session. |
| **🕘 Download History** | Keep a persistent record of downloaded media for easier access later. |
| **▶️ Media Playback** | Open saved media directly from download history. |
| **↗️ Sharing** | Share saved media and send links to the app through Android's share system. |
| **🗂️ Smart Organization** | Automatically create appropriate folders and use meaningful, collision-safe filenames. |
| **🌙 Mobile UI** | Clean dark interface designed for comfortable use on mobile devices. |

---

## 📂 Download Organization

For regular media downloads, files are organized by media type:

```text
DLXhub/
├── video/
│   ├── Video Name 01.mp4
│   └── Video Name 02.mp4
│
└── audio/
    ├── Audio Name 01.mp3
    └── Audio Name 02.mp3
```

### 📋 Playlist Structure

When downloading a playlist, its **actual playlist name** is used as the parent folder:

```text
DLXhub/
└── Playlist Name/
    ├── video/
    │   ├── Video Name 01.mp4
    │   └── Video Name 02.mp4
    │
    └── audio/
        ├── Video Name 01.mp3
        └── Video Name 02.mp3
```

This keeps large collections separated and makes downloaded content easier to find.

---

## 🔄 Download Workflow

### Single Media

```text
Link
 ↓
Media Detection
 ↓
Available Formats
 ↓
Quality Selection
 ↓
Download
 ↓
Destination Folder
```

### Batch

```text
Multiple Links
 ↓
Resolve Each Item
 ↓
Use Actual Media Names
 ↓
Queue Downloads
 ↓
Save Automatically
```

### Playlist

```text
Playlist Link
 ↓
Resolve Playlist
 ↓
Read Playlist Name
 ↓
Resolve Media Items
 ↓
Video / Audio
 ↓
Download All Items
 ↓
Playlist Name/
 ├── video/
 └── audio/
```

---

## 📁 File Naming

DLXhub aims to preserve the **actual media title** rather than using generic names such as:

```text
Batch Media 1
Batch Media 2
```

For playlists and batch downloads, resolved media titles are used wherever the source provides them.

If a file with the same name already exists, the app avoids overwriting the existing file by generating a safe unique filename.

---

## 🔐 Privacy

DLXhub follows a **local-first approach**.

- Download history is stored locally.
- Destination settings remain on the device.
- Cookie profiles are stored locally in private application storage.
- Cookie values are not intended to be displayed in the interface or exposed through download logs.
- No account is required simply to use the downloader.

> **Your downloaded content and local settings stay under your control unless you explicitly choose to share or export them.**

---

## 📱 Android Experience

DLXhub is designed around common Android workflows:

- **Background-friendly downloading**
- **Persistent download notifications**
- **Custom storage selection**
- **Android share-sheet integration**
- **Direct media playback**
- **Download history**
- **Mobile-optimized controls**
- **Automatic file organization**

The interface is intentionally focused on the download task rather than exposing unnecessary technical details to the user.

---

## 🖥️ CLI Edition

Prefer the **command line**?

DLXhub also has a dedicated **CLI Edition** for users who prefer a terminal-based workflow. The CLI project is maintained separately and provides the original command-line downloader source, engines, modules, utilities, and dependency setup.

### 🔗 Explore the CLI Edition

**[→ DLXhub CLI — GitHub](https://github.com/mashrafi141/DLXhub-CLI)**

> **Same project. Different experience.**  
> 📱 Android GUI for mobile users • 🖥️ CLI for terminal users

The CLI edition is kept in its own repository so both projects can evolve independently while sharing the same overall DLXhub ecosystem.

---

## 🛠️ Development

### Build Debug APK

```bash
gradle :app:assembleDebug
```

Generated APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Run Unit Tests

```bash
gradle :app:testDebugUnitTest
```

### Manual GitHub Actions Build

The repository can be configured so APK builds are triggered manually from GitHub Actions.

```text
GitHub
  → Actions
  → Build APK
  → Run workflow
```

A manual workflow run can build the available APK variants and publish the configured build artifacts/release.

---

## 🚀 Release

Create a version tag when preparing a release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Example versioning:

```text
v1.0.0
v1.1.0
v1.2.0
```

Use **major** versions for significant changes, **minor** versions for feature additions, and **patch** versions for fixes.

---

## ⚠️ Responsible Use

DLXhub is a tool for saving media that you are authorized to download.

Users are responsible for complying with:

- **Copyright requirements**
- **Terms of service**
- **Privacy requirements**
- **Applicable local laws and regulations**

Do not use the application to access, copy, or redistribute content without the necessary rights or permission.

---

## 📄 License

This project is intended for **personal use**.

Unless otherwise stated in the repository, the project's source code and associated assets remain subject to their respective licenses and permissions.

---

<div align="center">

### **DLXhub · v1.0**

*Simple downloads. Clean organization. Full control.*

**Developed by MASH!141**

</div>
