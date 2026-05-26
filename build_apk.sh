#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# سكريبت بناء أوتوتيون عربي — يعمل بدون Android Studio أو إنترنت
# يستخدم Android SDK المثبَّت محلياً + javac + dx + aapt2 + apksigner
# ─────────────────────────────────────────────────────────────────────────────
set -e

ANDROID_SDK=/usr/lib/android-sdk
ANDROID_JAR=$ANDROID_SDK/platforms/android-23/android.jar
BUILD_TOOLS=$ANDROID_SDK/build-tools/debian
BUILD_DIR=/tmp/autotune_build_$$
KEYSTORE=$BUILD_DIR/debug.keystore
OUTPUT=AutoTuneArabic.apk

echo "=== أوتوتيون عربي — بناء APK ==="
mkdir -p $BUILD_DIR/classes

# 1. تجميع Java
echo "[1/5] تجميع Java..."
find app/src/main/java -name "*.java" | xargs javac \
  -source 8 -target 8 \
  -classpath "$ANDROID_JAR" \
  -d $BUILD_DIR/classes \
  -Xlint:-options 2>&1

# 2. تحويل إلى DEX
echo "[2/5] تحويل إلى DEX..."
$BUILD_TOOLS/dx --dex \
  --min-sdk-version=26 \
  --output=$BUILD_DIR/classes.dex \
  $BUILD_DIR/classes/

# 3. تجميع الموارد
echo "[3/5] معالجة الموارد..."
$BUILD_TOOLS/aapt2 compile \
  --dir app/src/main/res \
  -o $BUILD_DIR/compiled_res.zip

# 4. ربط الموارد وإنشاء APK
echo "[4/5] ربط الموارد..."
$BUILD_TOOLS/aapt2 link \
  -o $BUILD_DIR/base.apk \
  -I "$ANDROID_JAR" \
  --manifest app/src/main/AndroidManifest.xml \
  $BUILD_DIR/compiled_res.zip \
  --min-sdk-version 23 \
  --target-sdk-version 34 \
  --version-code 1 \
  --version-name "1.0"

# إضافة DEX
cd $BUILD_DIR && zip -j base.apk classes.dex && cd -

# توليد keystore للتوقيع إن لم يكن موجوداً
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -alias autotune -keyalg RSA -keysize 2048 \
    -validity 10000 -keystore $KEYSTORE \
    -storepass android -keypass android \
    -dname "CN=AutoTune,OU=Dev,O=Dev,C=US" -noprompt 2>/dev/null
fi

# 5. محاذاة وتوقيع
echo "[5/5] توقيع APK..."
$BUILD_TOOLS/zipalign -f 4 $BUILD_DIR/base.apk $BUILD_DIR/aligned.apk
$BUILD_TOOLS/apksigner sign \
  --ks $KEYSTORE \
  --ks-key-alias autotune \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out $OUTPUT \
  $BUILD_DIR/aligned.apk

echo ""
echo "=== تم البناء بنجاح! ==="
ls -lh $OUTPUT
rm -rf $BUILD_DIR
