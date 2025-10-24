# Waveform Audio Player

## Overview
The Edit Item screen now features an animated waveform visualization that displays audio peaks while playing recordings.

## Features

### Visual Waveform
- **60 vertical bars** representing audio amplitude
- **Animated progress** - bars change color as playback progresses
- **Primary color** for played sections
- **Muted color** for unplayed sections
- **Smooth animation** - updates every 50ms for fluid motion

### Playback Controls
- **Play/Pause button** - Large, centered control
- **Time display** - Shows current position (e.g., "0:45")
- **Duration display** - Shows total length (e.g., "2:30")
- **Auto-updates** - Real-time synchronization with audio playback

### Design
- **Card-based UI** - Clean, modern Material 3 design
- **Surface variant background** - Subtle contrast
- **Rounded bar shapes** - Polished appearance
- **Responsive layout** - Adapts to screen width

## Implementation Details

### Component: `AudioWaveformPlayer`
**Location:** `ComposeMainActivity.kt` (lines 2149-2263)

**Key Features:**
1. **State Management**
   - Tracks `isPlaying`, `currentPosition`, `duration`
   - Updates every 50ms via `LaunchedEffect`

2. **Waveform Generation**
   - 60 bars with varied heights (0.2f to 1.0f)
   - Uses sine wave + randomness for natural appearance
   - Generated once and remembered for performance

3. **Progress Visualization**
   - Calculates progress: `currentPosition / duration`
   - Colors bars based on playback position
   - Smooth color transitions

4. **ExoPlayer Integration**
   - Receives ExoPlayer instance as parameter
   - Controls playback via `play()` and `pause()`
   - Reads position and duration in real-time

### Time Formatting
**Function:** `formatTime(millis: Long)`
- Converts milliseconds to "M:SS" format
- Example: 125000ms → "2:05"

## Usage

```kotlin
AudioWaveformPlayer(
    exoPlayer = exoPlayer,
    modifier = Modifier
        .fillMaxWidth()
        .height(120.dp)
)
```

## Visual Example

```
Recording
┌────────────────────────────────────┐
│ ▂▅▇▆▄▃▅▇▆▄▃▂▅▇▆▄▃▅▇▆▄▃▂▅▇▆▄▃▅▇▆ │  <- Waveform bars
│ ████████████░░░░░░░░░░░░░░░░░░░░ │  <- Progress (blue = played, gray = unplayed)
│                                    │
│  0:45        ▶        2:30        │  <- Time | Play/Pause | Duration
└────────────────────────────────────┘
```

## Performance
- **Efficient rendering** - Waveform generated once
- **Minimal recomposition** - Only updates on state changes
- **Smooth animation** - 20 FPS update rate (50ms intervals)
- **Proper cleanup** - Player released via `DisposableEffect`

## Future Enhancements
- Tap-to-seek functionality (currently placeholder)
- Real audio waveform extraction from file
- Zoom controls for longer recordings
- Playback speed controls
