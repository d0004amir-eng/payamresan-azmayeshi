const fs = require('fs');
const f = 'src/SmsBridge.html';
let s = fs.readFileSync(f, 'utf8');
const oldLine = "if (p && registered) plugin = p;";
if (s.includes("registerPlugin('SmsLab')")) { console.log('already patched'); process.exit(0); }
if (!s.includes(oldLine)) { console.log('pattern not found'); process.exit(1); }
s = s.replace(oldLine, "if (registered) { try { plugin = typeof cap.registerPlugin === 'function' ? cap.registerPlugin('SmsLab') : p; } catch (e) { plugin = p || null; } }");
fs.writeFileSync(f, s);
console.log('patched');
