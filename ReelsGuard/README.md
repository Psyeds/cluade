# Reels Guard

Floating draggable bubble. Tap it, pick the time you need to be done (like 1:29 PM) or hit +15 / +30 / +1 hr.
Buzz warnings at 5 min and 1 min. At zero, Reels gets locked for 30 min: any time you open Reels it kicks you to the home screen.
Unlock takes a 15 second wait on purpose. Long-lock length is `LOCK_MINUTES` in `Store.kt`.

## Get the APK (no Android Studio needed)
1. Make a new GitHub repo, upload everything in this folder (make sure `.github/workflows/build.yml` made it).
2. Repo > Actions > Build APK > Run workflow. Takes about 4 min.
3. Open the finished run, download the `ReelsGuard-apk` artifact, unzip, send `app-debug.apk` to your phone, install it.

If the build fails, copy the red error from the Actions log and send it back.

## Phone setup (once)
1. Open Reels Guard > "Allow floating bubble" > switch on.
2. "Turn on the Reels guard" > Reels Guard > switch on. If it is greyed out: App info > three dots > Allow restricted settings, then retry.
3. Tap "Start bubble" and drag it wherever you want.

## Notes
- "Only Reels" detection relies on Instagram's internal screen IDs, which Instagram can change. If Reels slips through, untick it in the panel to block all of Instagram.
- Bubble stops if you reboot or force-stop the app. Open the app and hit Start bubble again.
