# UnnamedEditor

An independent Android-first video editor prototype with a mobile editing workspace, local project state, video preview, trim controls, undo/redo, and MP4 export.

## Current working features
- Create a project and import a video using Android's document picker
- Preview imported video and play/pause
- Horizontally scrollable timeline with a fixed center playhead, synchronized ruler/tracks, and preview scrubbing
- Imported video clip width reflects the source duration; select clips directly on the timeline
- Timeline track layout for video, audio, and text layer placeholders
- Select and delete timeline entries; duplicate a selected entry
- Set trim start/end points, reset trim, and loop preview playback within the selected range
- Undo/redo supported editor-state changes (up to 50 history steps)
- Preview speed control
- Adjustable GPU effect stack with brightness, contrast, HSL controls, blur, RGB channel scaling, zoom, and rotation; effect values are saved and rendered during export
- Export the imported source video as MP4 (H.264 video, AAC audio)
- Choose 720p, 1080p, 1440p, or 4K output size and Standard/High/Maximum bitrate preset
- Save completed exports to Gallery → Movies/UnnamedEditor on Android 10+

## Important limitations
This is an early usable prototype, not a full professional multitrack editor.
- Export currently processes only the imported source video with the selected trim range and global effect stack.
- Additional timeline tracks, audio layers, text, stickers, overlays, and node graphs are not composited into the exported file. The current effect stack applies to the whole trimmed source, not separate effect clips with independent timeline ranges.
- Timeline clip blocks are visual entries; they do not yet have independent time positions or durations.
- Duplicate creates a second timeline entry; it does not cut the source media at the playhead.
- Effects are not yet rendered live in the preview; their final appearance is visible after export.
- Effect keyframes, per-effect timeline duration, masks, motion tracking, AI/body effects, and CapCut's changing cloud effect catalog are not implemented.
- Preview speed is a playback control and is not baked into export.
- Frame rate follows the source video.
- Project data is stored locally on the device; there is no cloud sync.

These limitations are stated explicitly so the UI does not imply that unsupported edits are included in the render.

## Build
Open the project in Android Studio and run the app configuration, or use the GitHub Actions workflow **Build UnnamedEditor APK**. The workflow produces a debug APK suitable for device testing, not a signed Play Store release.

- Package: `com.unnamededitor.app`
- Minimum Android: 8.0 (API 26)
- Version: 0.2.1
