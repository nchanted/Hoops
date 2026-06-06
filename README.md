# 🏀 Hoops

A native Android basketball game. Flick the ball up to shoot, sink baskets, climb 60 stages of rising difficulty, earn XP to upgrade skills, and spend coins on outfits and ball skins. Progress saves automatically.

No game engine, no external libraries — pure Android `Canvas`, so it builds to an APK reliably on GitHub Actions.

## How to play
- **Shoot:** press and drag *upward* from anywhere, then release to flick the ball. A dotted guide shows the arc.
- **Score** to earn coins + XP. Consecutive baskets build a **combo** multiplier. A clean **SWISH** (centre of the rim) pays double.
- **Level up** to earn skill points. Spend them in **Upgrades**.
- **Coins** buy outfits and ball skins in the **Shop**.
- Each stage requires a number of made baskets; the hoop shrinks, moves, bobs and gets wind as you progress. Clear **Stage 60** to become Champion, then keep going in endless mode.

### Skills
| Skill | Effect |
|---|---|
| Aim Assist | Longer trajectory guide |
| Power Control | Auto-aim nudge + more range |
| Coin Value | +20% coins per basket per level |
| Slow-Mo | Time slows briefly on launch |
| Rim Magnet | Pulls near-misses toward the rim |
| Combo Master | Higher combo cap & bigger rewards |

## Build the APK on GitHub (no PC setup needed)
1. Create a new GitHub repository.
2. Upload all of these files (keep the folder structure).
3. Go to the **Actions** tab → the **Build Hoops APK** workflow runs automatically on push (or click **Run workflow**).
4. When it finishes, open the run → **Artifacts** → download **hoops-debug-apk**.
5. Unzip it to get `app-debug.apk`.

## Install on your phone
1. Copy `app-debug.apk` to your Android phone.
2. Open it; allow "install from unknown sources" if prompted.
3. Launch **Hoops**.

## Build locally (optional)
With Android Studio: open the folder and press Run. Or from a terminal with the Android SDK + JDK 17:
```
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```
The APK lands in `app/build/outputs/apk/debug/`.

## Tech
- Min SDK 24, target/compile SDK 34, Java 17, AGP 8.5.2, Gradle 8.7.
- Single `SurfaceView` with its own render thread; physics use sub-stepped Euler integration with rim/backboard collision.
- Save data is JSON in `SharedPreferences`.
