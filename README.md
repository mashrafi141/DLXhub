DLXhub

DLXhub is a fast, privacy-focused Android media downloader designed for simple, reliable media saving from supported web sources.

Features

Video and audio downloads with available quality options

Playlist and batch downloading

Real-time download progress and background downloads

Custom download folder selection

Secure local cookie profiles for authenticated downloads

Persistent download history with playback and sharing

Android share-sheet support for quick link importing

Collision-safe filenames and organized media folders

Dark, responsive interface optimized for mobile use

Download Organization

DLXhub keeps downloads organized automatically:

DLXhub/
├── video/
└── audio/

For playlists, a dedicated folder is created using the playlist name:

DLXhub/
└── Playlist Name/
    ├── video/
    └── audio/

Build

Debug APK

gradle :app:assembleDebug

APK output:

app/build/outputs/apk/debug/app-debug.apk

Unit Tests

gradle :app:testDebugUnitTest

Release

Create and push a version tag to trigger the automated APK release workflow:

git tag v1.0.0
git push origin v1.0.0

Privacy

DLXhub is designed around local processing and local storage. Download history, destination settings, and cookie profiles remain on the device unless the user explicitly shares or exports content.

License

This project is intended for personal use. Users are responsible for complying with the terms, copyright rules, and applicable laws of the sources they access.
