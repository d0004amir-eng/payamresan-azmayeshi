const fs = require('fs');
const f = '.github/workflows/build-apk.yml';
let s = fs.readFileSync(f, 'utf8');
if (s.includes('build.log')) { console.log('already patched'); process.exit(0); }
const old = '      - run: ./gradlew assembleDebug\n';
if (!s.includes(old)) { console.log('pattern not found'); process.exit(1); }
const neu = '      - name: Build debug APK\n        run: |\n          ./gradlew assembleDebug --console=plain > build.log 2>&1 || { echo "=== ERRORS ==="; grep -n -A8 "error:\\|What went wrong\\|FAILURE" build.log | head -n 120; exit 1; }\n';
s = s.replace(old, () => neu);
fs.writeFileSync(f, s);
console.log('patched');
