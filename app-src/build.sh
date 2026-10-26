#!/bin/bash
set -e
cd /home/claude/apk
SDK=/home/claude/android-sdk; BT=$SDK/build-tools/34.0.0; AJ=$SDK/platforms/android-34/android.jar
python3 make_html.py
rm -rf build && mkdir -p build/classes build/dex
$BT/aapt2 compile --dir res -o build/res.zip
$BT/aapt2 link -o build/unsigned.apk -I $AJ --manifest AndroidManifest.xml -A assets build/res.zip --min-sdk-version 24 --target-sdk-version 34
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath $AJ:$BT/core-lambda-stubs.jar -classpath $AJ -d build/classes src/ru/barorders/app/*.java 2>&1 | grep -v -e JAVA_TOOL -e warning -e '^Note' || true
java -cp $SDK/r8.jar com.android.tools.r8.D8 --min-api 24 --lib $AJ --output build/dex build/classes/ru/barorders/app/*.class 2>&1 | grep -v JAVA_TOOL || true
(cd build/dex && zip -q ../unsigned.apk classes.dex)
$BT/zipalign -f -p 4 build/unsigned.apk build/aligned.apk
$BT/apksigner sign --ks /home/claude/apk-key/barorders.jks --ks-pass env:KS_PASS --key-pass env:KS_PASS --out build/zakazy-na-bar.apk build/aligned.apk 2>&1 | grep -v JAVA_TOOL || true
$BT/apksigner verify build/zakazy-na-bar.apk 2>&1 | grep -v JAVA_TOOL && echo SIGN_OK
$BT/aapt2 dump badging build/zakazy-na-bar.apk 2>&1 | grep -E "^package|sdkVersion|application-label:|launchable"
ls -la build/zakazy-na-bar.apk
