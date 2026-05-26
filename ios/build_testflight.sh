#!/usr/bin/env bash
# =============================================================================
# build_testflight.sh — بناء تطبيق أوتوتيون عربي ورفعه إلى TestFlight
# AutoTune Arabic — Build & TestFlight upload helper
# =============================================================================
# الاستخدام: قم بتشغيل هذا السكريبت من جهاز Mac مثبت عليه Xcode
# Usage: Run this script on a Mac with Xcode installed.
#   chmod +x build_testflight.sh
#   ./build_testflight.sh
# =============================================================================

set -euo pipefail

# ── الألوان للمخرجات / Terminal colors ──────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
RESET='\033[0m'

info()    { echo -e "${CYAN}[INFO]${RESET}  $*"; }
success() { echo -e "${GREEN}[OK]${RESET}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${RESET}  $*"; }
error()   { echo -e "${RED}[ERROR]${RESET} $*" >&2; }
die()     { error "$*"; exit 1; }

echo ""
echo -e "${BOLD}============================================================${RESET}"
echo -e "${BOLD}   أوتوتيون عربي — AutoTune Arabic — TestFlight Builder     ${RESET}"
echo -e "${BOLD}============================================================${RESET}"
echo ""

# ── الخطوة 1: التحقق من تثبيت Xcode / Step 1: Verify Xcode ─────────────────
info "Step 1/5 — Checking for Xcode installation..."

XCODE_PATH=$(xcode-select -p 2>/dev/null || true)
if [[ -z "$XCODE_PATH" ]]; then
    die "Xcode command-line tools not found. Install Xcode from the Mac App Store and run: sudo xcode-select --switch /Applications/Xcode.app"
fi
success "Xcode found at: $XCODE_PATH"

# التحقق من إصدار Xcode / Check Xcode version
XCODE_VERSION=$(xcodebuild -version 2>/dev/null | head -1 || true)
if [[ -z "$XCODE_VERSION" ]]; then
    die "xcodebuild is not available. Make sure the full Xcode app (not just command-line tools) is installed."
fi
success "Using: $XCODE_VERSION"

# ── الانتقال إلى مجلد ios / Change to ios directory ─────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_DIR="$SCRIPT_DIR"

if [[ ! -f "$IOS_DIR/AutoTuneArabic.xcodeproj/project.pbxproj" ]]; then
    die "Cannot find AutoTuneArabic.xcodeproj in $IOS_DIR. Make sure you run this script from the ios/ directory or its parent."
fi
cd "$IOS_DIR"
success "Working directory: $IOS_DIR"

# ── الخطوة 2: الحصول على Team ID / Step 2: Get Apple Team ID ────────────────
echo ""
info "Step 2/5 — Apple Developer Team ID"
echo -e "${YELLOW}  You can find your Team ID at: https://developer.apple.com/account${RESET}"
echo -e "${YELLOW}  It looks like: XXXXXXXXXX (10 uppercase alphanumeric characters)${RESET}"
echo ""

TEAM_ID="${APPLE_TEAM_ID:-}"
if [[ -z "$TEAM_ID" ]]; then
    read -rp "  Enter your Apple Team ID (or press Enter to use placeholder 'TEAMIDHERE'): " TEAM_ID
fi
if [[ -z "$TEAM_ID" ]]; then
    TEAM_ID="TEAMIDHERE"
    warn "Using placeholder Team ID '$TEAM_ID'. You will need to set the correct Team ID before uploading."
fi
success "Team ID: $TEAM_ID"

# ── المسارات / Paths ─────────────────────────────────────────────────────────
ARCHIVE_PATH="/tmp/AutoTuneArabic.xcarchive"
EXPORT_PATH="/tmp/AutoTuneArabic_IPA"
EXPORT_OPTIONS_PLIST="/tmp/AutoTuneArabic_ExportOptions.plist"
SCHEME="AutoTuneArabic"
CONFIGURATION="Release"

# ── الخطوة 3: إنشاء ملف خيارات التصدير / Step 3: Create ExportOptions.plist ─
info "Step 3/5 — Writing ExportOptions.plist to $EXPORT_OPTIONS_PLIST..."
cat > "$EXPORT_OPTIONS_PLIST" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key>
    <string>app-store</string>
    <key>teamID</key>
    <string>${TEAM_ID}</string>
    <key>uploadBitcode</key>
    <false/>
    <key>compileBitcode</key>
    <false/>
    <key>signingStyle</key>
    <string>automatic</string>
    <key>stripSwiftSymbols</key>
    <true/>
    <key>thinning</key>
    <string>&lt;none&gt;</string>
</dict>
</plist>
PLIST
success "ExportOptions.plist written."

# ── الخطوة 4: بناء الأرشيف / Step 4: Build the archive ──────────────────────
echo ""
info "Step 4/5 — Archiving the app (this may take a few minutes)..."
echo -e "${YELLOW}  Archive destination: $ARCHIVE_PATH${RESET}"
echo ""

# إزالة الأرشيف القديم إن وجد / Remove stale archive if it exists
if [[ -d "$ARCHIVE_PATH" ]]; then
    warn "Removing existing archive at $ARCHIVE_PATH..."
    rm -rf "$ARCHIVE_PATH"
fi

xcodebuild \
    -scheme "$SCHEME" \
    -configuration "$CONFIGURATION" \
    -archivePath "$ARCHIVE_PATH" \
    -destination "generic/platform=iOS" \
    archive \
    CODE_SIGN_STYLE=Automatic \
    DEVELOPMENT_TEAM="$TEAM_ID" \
    | xcpretty 2>/dev/null || true

# xcpretty may not be installed — fall back to raw output
if [[ ! -d "$ARCHIVE_PATH" ]]; then
    warn "xcpretty not found or archive not produced — retrying with raw output..."
    xcodebuild \
        -scheme "$SCHEME" \
        -configuration "$CONFIGURATION" \
        -archivePath "$ARCHIVE_PATH" \
        -destination "generic/platform=iOS" \
        archive \
        CODE_SIGN_STYLE=Automatic \
        DEVELOPMENT_TEAM="$TEAM_ID"
fi

if [[ ! -d "$ARCHIVE_PATH" ]]; then
    die "Archive was not created. Check the xcodebuild output above for errors."
fi
success "Archive created at: $ARCHIVE_PATH"

# ── الخطوة 5: تصدير IPA / Step 5: Export IPA ────────────────────────────────
echo ""
info "Step 5/5 — Exporting IPA for App Store / TestFlight..."
echo -e "${YELLOW}  Export destination: $EXPORT_PATH${RESET}"
echo ""

if [[ -d "$EXPORT_PATH" ]]; then
    warn "Removing existing export at $EXPORT_PATH..."
    rm -rf "$EXPORT_PATH"
fi

xcodebuild \
    -exportArchive \
    -archivePath "$ARCHIVE_PATH" \
    -exportPath "$EXPORT_PATH" \
    -exportOptionsPlist "$EXPORT_OPTIONS_PLIST"

IPA_FILE=$(find "$EXPORT_PATH" -name "*.ipa" | head -1)
if [[ -z "$IPA_FILE" ]]; then
    die "IPA file not found after export. Check the xcodebuild output above."
fi
success "IPA exported: $IPA_FILE"

# ── التعليمات النهائية / Final instructions ──────────────────────────────────
echo ""
echo -e "${BOLD}============================================================${RESET}"
echo -e "${GREEN}${BOLD}  Build complete! / اكتمل البناء بنجاح                     ${RESET}"
echo -e "${BOLD}============================================================${RESET}"
echo ""
echo -e "${BOLD}IPA location:${RESET}"
echo "  $IPA_FILE"
echo ""
echo -e "${BOLD}How to upload to TestFlight:${RESET}"
echo ""
echo -e "  ${CYAN}Option A — Xcode Organizer (recommended):${RESET}"
echo "    1. Open Xcode."
echo "    2. Go to Window > Organizer (Shift+Cmd+O)."
echo "    3. Select the 'Archives' tab."
echo "    4. Find 'AutoTuneArabic' and click 'Distribute App'."
echo "    5. Choose 'App Store Connect' then 'Upload'."
echo "    6. Follow the wizard to upload to TestFlight."
echo ""
echo -e "  ${CYAN}Option B — altool (Xcode 14 and earlier):${RESET}"
echo "    xcrun altool --upload-app \\"
echo "        --type ios \\"
echo "        --file \"$IPA_FILE\" \\"
echo "        --username \"your@apple-id.email\" \\"
echo "        --password \"@keychain:AC_PASSWORD\""
echo ""
echo -e "  ${CYAN}Option C — notarytool / altool replacement (Xcode 15+):${RESET}"
echo "    xcrun altool --upload-app \\"
echo "        --type ios \\"
echo "        --file \"$IPA_FILE\" \\"
echo "        --apiKey \"YOUR_API_KEY\" \\"
echo "        --apiIssuer \"YOUR_ISSUER_UUID\""
echo ""
echo -e "  ${CYAN}Option D — Transporter app (free from Mac App Store):${RESET}"
echo "    Open Transporter, drag in the IPA, and click Deliver."
echo ""
echo -e "${YELLOW}Note:${RESET} Make sure the app version and build number in Xcode are"
echo "  incremented before each TestFlight upload."
echo ""
# ── تذكير بالمتطلبات / Reminder ─────────────────────────────────────────────
echo -e "${BOLD}Pre-upload checklist:${RESET}"
echo "  [x] Apple Developer Program membership active"
echo "  [x] App ID 'com.autotune.arabic' registered in developer.apple.com"
echo "  [x] Signing certificate and provisioning profile valid"
echo "  [x] App record created in App Store Connect (appstoreconnect.apple.com)"
echo "  [ ] Team ID is set correctly (used: $TEAM_ID)"
if [[ "$TEAM_ID" == "TEAMIDHERE" ]]; then
    echo ""
    warn "You used the placeholder Team ID 'TEAMIDHERE'."
    warn "Re-run this script with: APPLE_TEAM_ID=XXXXXXXXXX ./build_testflight.sh"
fi
echo ""
