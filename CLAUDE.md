# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires `local.properties` with `sdk.dir=/home/bee/Android/Sdk` (already present).

## Architecture

Single-activity, single-view native game. No Compose, no WebView, no fragments.

| File | Purpose |
|---|---|
| `MainActivity.kt` | Thin wrapper — sets fullscreen flags, instantiates `GameView` |
| `GameView.kt` | Everything: game engine, renderer, audio, input |

### GameView.kt structure

**Coordinate system:** Logical space `LW=390f × LH=700f`. Scaled to real screen each frame via `canvas.scale(scaleX, scaleY)`. Touch events divided by scale to get logical coords.

**Screen state machine:** `Screen` enum — `MENU, GAME, PAUSE, GAMEOVER, WIN, SCORES, SETTINGS, SHOP, UPGRADE`. The `render()` method dispatches to per-screen draw functions.

**Game loop:** `GameThread` inner class locks canvas at ~60fps, calls `update(dt)` then `render(canvas)`.

**Audio:** `SoundEngine` object generates PCM audio via `AudioTrack` in a background thread. Waveforms: sine (wall), square (brick), sawtooth (paddle), frequency sweep (level up), multi-note chord (win).

**Data models:** `Ball`, `Brick`, `Paddle`, `Particle`, `Powerup`, `Laser`, `Fx` (powerup durations), `GameState`, `DiffCfg`.

**Levels:** 100 `LevelDef` entries in `LEVELS` list. Each has a special-brick spawn chance `sc` and a grid of HP values.

**Themes:** 6 `Theme` entries in `THEMES` map — keys: `neon`, `retro`, `ice`, `fire`, `mono`, `forest`.

**Persistence:** `SharedPreferences` key `"bb"` — stores theme, difficulty, toggles (haptic/particles/trails/sfx), volume, high score list.

**Powerups:** `wide`, `multi`, `laser`, `slow`, `fireball`, `shield` — durations tracked in `Fx`. Drop from special bricks.

## Fullscreen / display cutout (Android 15 / Pixel 9 Pro XL)

`MainActivity` uses `WindowInsetsController.hide(statusBars | navigationBars)` (API 30+) with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`. Deprecated `SYSTEM_UI_FLAG_*` flags are kept as a fallback for API < 30 only. `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` is set so the game renders behind the punch-hole camera area.

`GameView.onApplyWindowInsets` captures the top inset (status bar + cutout) and stores it in `topInsetPx`. This is applied as a logical-unit offset (`topInsetPx / scaleY`) to HUD element Y positions. The override uses a max-only update pattern to avoid jitter during the bar-hide animation, which fires the callback multiple times with decreasing values.

## HUD layout rules (critical — do not break)

**HUD must be drawn AFTER `canvas.restore()` ends the screen-shake block.** If HUD elements (buttons, text) are drawn inside `canvas.save/translate(shakeOx, shakeOy)`, their visual positions shift per-frame but `btnRects` (the logical-coordinate hit-test map) does not update — taps miss during/after brick hits.

Current top-of-screen layout (logical coords, left→right):
- `x=8f` left — score `★ N`
- `LW/2f` centre — level `LVL N/100`
- `LW-58f` right-aligned — lives `♥♥♥`
- `cx=LW-20f` — pause button `❚❚` (w=38, h=32)

The ~38 logical unit gap between lives text end (~`LW-58f`) and pause button start (~`LW-39f`) must be preserved. Do not move the pause button to the left side — users have muscle memory for top-right.

## Shop system

`Screen.SHOP` appears after completing every 10th level (levels 10, 20, 30…). It blocks advancing until the player picks an upgrade or presses "Continue".

- **Coins** — persistent in prefs key `"coins"`. Earned 1 per brick destroyed (`killBrick`). Accumulated across games.
- **Upgrades** (all one-shot per shop visit): +1 Life (20🪙), Wide Paddle 15 s (15🪙), Shield 10 s (12🪙), Slow Ball −18% speed (10🪙), Continue (free).
- Unaffordable upgrade buttons are never added to `btnRects`, so they are not tappable.
- `startNextLevel()` creates the next `GameState` from `shopNextLi` (0-indexed), carrying over score, lives, ballSpeed, and `fx` (active powerup durations).

## Aim system (pre-launch)

Before the ball is launched, dragging moves `st.aimX` only — the paddle stays fixed. The aim line uses `aimX` to compute the launch angle. On tap-to-launch, `aimX` is used to set `ball.vx/vy`. Once launched, dragging moves the paddle as normal.

## Navigation / Settings

`prevScreen` tracks the screen before entering Settings, so "← BACK" returns to PAUSE or MENU correctly. `saveProgress()` now saves when `screen` is SETTINGS (as well as GAME/PAUSE), preventing data loss when the app is backgrounded from Settings.

## Backgrounding (pause-on-background)

`MainActivity.onPause` → `GameView.pause()` freezes the sim and, if a game is live, switches `screen` to `PAUSE` — so returning to the app lands on the pause menu instead of the ball resuming mid-flight. `pause()` also calls `savePrefs()`, which banks coins earned mid-run (previously coins were only written on shop purchases / settings changes / game-over, so backgrounding lost them).

## Multi-HP brick visuals

Bricks with `maxHp > 1` now show:
- A thicker armour border (alpha and strokeWidth scale with maxHp)
- An HP pip bar at the bottom: filled white pips = remaining HP, dark pips = lost HP

The old diagonal crack lines are gone.

## Paint state rule

`pText.textAlign` must always be set explicitly before drawing text. `textBtn()` sets `Paint.Align.CENTER` internally. `drawMenu()` sets it at the top. `drawSettings()` sets `LEFT` for toggle labels — callers after it must reset.

## App identity

- Package: `com.bee.brickbreaker`
- Min SDK: 26 (Android 8.0)
- Portrait only, fullscreen, screen-on flag
- Permission: `VIBRATE`
