# BubbleFix — full-bleed bubble avatars on HyperOS

A tiny LSPosed/Vector module that fixes chat bubbles on (some) HyperOS devices
showing the contact photo as a **small picture inside a white circle** instead of
filling the whole bubble. The photo ends up full-bleed in the bubble, the way it
does on stock Android.

---

## The story (why this exists)

My phone (a POCO running HyperOS 3, Android 17) always drew chat-bubble avatars
wrong: the photo sat shrunken in the middle of a white frame. It bugged me for
months, so I finally sat down and dug into it.

What I tried, in order, and why each one failed:

1. **RRO overlay on `bubble_badge_size`** — the ROM just ignored it. Android 12+
   only lets overlays touch resources declared `<overlayable>`, and these aren't.
2. **Root fabricated overlays (FRRO)** — `cmd overlay fabricate` refuses dimens
   entirely (only data types 16–31 allowed).
3. **Binary-patching `resources.arsc` inside SystemUI.apk** — this actually
   worked technically (Android 17 uses a new *compact* arsc entry format, which
   is fun to parse: 8-byte entries, `u16 key | u8 size | u8 type | u32 data`).
   I patched the values, repacked, re-signed, installed as a KernelSU module…
   and the phone booted into recovery with a "corrupted" warning. Turns out
   HyperOS's package manager *accepts* a re-signed SystemUI, but then SystemUI
   crash-loops because it loses **signature-level permissions**
   (`MANAGE_ACTIVITY_TASKS` and friends) that only the platform key unlocks.
   RescueParty then kicks you to recovery. Lesson learned: you can't touch that
   APK, period. (Shout-out to KernelSU safe mode — hold Vol-Down during boot and
   all modules disable. Saved my evening.)
4. **Actually reading SystemUI's code.** Should have done this first.

## The actual root cause

While reading the decompiled SystemUI, I found the avatar pipeline:

`BubbleViewInfoTask` builds the bubble's icon via Launcher3's
`BaseIconFactory.createBadgedIconBitmap(drawable, IconOptions)`.

In `IconOptions`, there's a field `wrapNonAdaptiveIcon` that **defaults to
`true`**. When it's true and the drawable is *not* an adaptive icon (i.e. a
normal contact photo), the factory wraps it in an `AdaptiveIconDrawable` with a
**white background** and scales the photo down (`LEGACY_ICON_SCALE`).

That's the whole bug. One boolean. Contact photo → shrunk onto a white square →
circle-cropped → "small picture in a white frame".

The funny part: the *badge* path (`getBadgeBitmap`, the little app icon in the
corner) explicitly sets `wrapNonAdaptiveIcon = false`. The main avatar path just
…doesn't. Looks like an honest mistake in the ROM.

The red herrings I chased so you don't have to:

- `bubble_badge_size` (24dp) — that's the *small app badge*, not the avatar.
- `bubble_overflow_icon_inset` (30dp) — setting it to 0 gives you a giant "+"
  overflow bubble. Ask me how I know.

## The fix

This module hooks `BaseIconFactory.createBadgedIconBitmap` **inside the
SystemUI process only** and flips `IconOptions.wrapNonAdaptiveIcon` to `false`
before the call runs. Photos are then drawn full-bleed and circle-cropped, like
on stock Android.

No APK modification, no signature problems, no bootloops. Disable the module
and you're instantly back to stock behavior.

It's ~40 lines of Java:

```java
XposedBridge.hookAllMethods(baseIconFactory, "createBadgedIconBitmap",
    new XC_MethodHook() {
        @Override protected void beforeHookedMethod(MethodHookParam param) {
            for (Object arg : param.args) {
                if (arg != null && arg.getClass().getName().equals(
                        "com.android.launcher3.icons.BaseIconFactory$IconOptions")) {
                    XposedHelpers.setBooleanField(arg, "wrapNonAdaptiveIcon", false);
                }
            }
        }
    });
```

## Install

Requirements: rooted device with Zygisk + LSPosed API **102+**
(tested with KernelSU/SukiSU + ReZygisk + Vector 2.2 on HyperOS 3 / Android 17).

1. Install `BubbleFix.apk` from [Releases](../../releases).
2. Enable the module in your LSPosed/Vector manager.
3. Set scope to **System UI** (`com.android.systemui`) only.
4. Restart SystemUI (or reboot).
5. Dismiss old bubbles and create fresh ones (old icons are cached).

## Uninstall

Disable the module or uninstall the APK. Nothing on the system partition is
ever touched.

## Notes / disclaimers

- Only tested on my own device (POCO peridot, HyperOS OS3.0.302, Android 17).
  If your ROM renders bubbles correctly already, you don't need this.
- If a HyperOS update changes SystemUI internals, the hook will simply stop
  matching and you'll be back to framed avatars — it can't break anything.
- This is a workaround, not a fix for the ROM itself. If you're affected,
  report it to your ROM maintainer: *"BaseIconFactory.IconOptions.
  wrapNonAdaptiveIcon is left at its default (true) on the bubble avatar path,
  so non-adaptive contact photos are wrapped in a white adaptive-icon
  background instead of being drawn full-bleed."*

## Credit

Made by **h3nix**. If this fixed your bubbles, that's all the credit I need.

## License

MIT — see [LICENSE](LICENSE).
