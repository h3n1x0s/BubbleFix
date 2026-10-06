# Building BubbleFix from source

The module is deliberately dependency-free (no Gradle, no Android SDK) — it's
built with four tools: `ecj` (Eclipse compiler), `d8` (from R8), `aapt2`, and
`apksigner`.

## Dependencies

- `ecj.jar` — https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/
- `r8.jar` — https://dl.google.com/dl/android/maven2/com/android/tools/r8/
  (run D8 via `java -cp r8.jar com.android.tools.r8.D8`)
- `aapt2`, `apksigner`, `keytool` — any Android SDK build-tools package
- An `android.jar` for compilation (any recent API level), plus an **older**
  one (~API 30) for `aapt2 link` if your aapt2 is old — new compact-format
  resource tables confuse older aapt2 builds.

The Xposed API classes (`de.robv.android.xposed.*`) are compile-time stubs —
any copy of `XposedBridgeApi` works, or write 5-line stubs yourself. They must
**not** be dexed into the APK; the runtime (LSPosed/Vector) provides the real
implementations.

## Steps

```bash
# 1. compile
java -jar ecj.jar -1.8 -nowarn -cp android.jar -d out $(find src -name '*.java')

# 2. dex (module classes only — NOT the Xposed stubs)
mkdir -p dexout
java -cp r8.jar com.android.tools.r8.D8 --release --min-api 29 \
    --lib android.jar --output dexout $(find out/com -name '*.class')

# 3. package
aapt2 compile --dir res -o compiled_res.zip
aapt2 link -I android-30.jar -o unsigned.apk \
    --manifest AndroidManifest.xml -A assets compiled_res.zip
# add classes.dex to the zip root
# assets/xposed_init must contain the entry-point class name:
#   com.henix.bubblefix.BubbleFix

# 4. sign (any key; LSPosed doesn't care)
apksigner sign --ks your.keystore --out BubbleFix.apk unsigned.apk
```

## Install / test loop

```bash
adb push BubbleFix.apk /data/local/tmp/
adb shell "pm install -r /data/local/tmp/BubbleFix.apk"
# enable + scope to com.android.systemui in your LSPosed manager, then:
adb shell "su -c 'killall com.android.systemui'"
```
