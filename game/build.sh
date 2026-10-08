#!/bin/bash
# Build real installable APK without Gradle: aapt2 + javac + d8 + zipalign + apksigner
set -e
cd "$(dirname "$0")"
SDK=/usr/local/lib/android/sdk
BT=$SDK/build-tools/35.0.0
AJAR=$SDK/platforms/android-35/android.jar
OUT=build
rm -rf $OUT
mkdir -p $OUT/compiled $OUT/classes $OUT/dex

echo "[1/7] aapt2 compile resources..."
$BT/aapt2 compile --dir res -o $OUT/compiled.zip

echo "[2/7] aapt2 link..."
$BT/aapt2 link -o $OUT/base.apk \
  -I $AJAR \
  --manifest AndroidManifest.xml \
  --java $OUT/gen \
  -A assets \
  $OUT/compiled.zip

echo "[3/7] javac..."
mkdir -p $OUT/gen/com/noor/oasis
find src $OUT/gen -name "*.java" > $OUT/sources.txt
javac --release 8 -cp "$AJAR" -d $OUT/classes @$OUT/sources.txt

echo "[4/7] d8 dex..."
$BT/d8 --min-api 24 --lib "$AJAR" --output $OUT/dex $(find $OUT/classes -name "*.class")

echo "[5/7] add dex + align..."
cp $OUT/base.apk $OUT/app-unsigned.apk
python3 -c "import zipfile; z=zipfile.ZipFile('$OUT/app-unsigned.apk','a',zipfile.ZIP_DEFLATED); z.write('$OUT/dex/classes.dex','classes.dex'); z.close()"
$BT/zipalign -f 4 $OUT/app-unsigned.apk $OUT/app-aligned.apk

echo "[6/7] sign..."
if [ ! -f debug.keystore ]; then
  keytool -genkeypair -keystore debug.keystore -alias noor -keyalg RSA -keysize 2048 \
    -validity 10950 -storepass android -keypass android \
    -dname "CN=Noor Oasis, OU=Game, O=Noor, L=Cairo, C=EG" > /dev/null 2>&1
fi
$BT/apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
  --out game-noor-oasis.apk $OUT/app-aligned.apk

echo "[7/7] verify..."
$BT/apksigner verify --print-certs game-noor-oasis.apk | head -5
$BT/aapt dump badging game-noor-oasis.apk | head -5
ls -la game-noor-oasis.apk
echo "BUILD OK"
