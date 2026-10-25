const fs = require('fs');
const f = 'src/App.html';
let s = fs.readFileSync(f, 'utf8');
if (s.includes('/*bridge-after-handlers*/')) { console.log('already patched'); process.exit(0); }
const a = "  await SmsBridge.init();\n";
const b = "  SmsBridge.on('smsReceived', handleIncoming);\n";
if (!s.includes(a) || !s.includes(b)) { console.log('pattern not found'); process.exit(1); }
s = s.replace(a, '');
s = s.replace(b, b + "  await SmsBridge.init(); /*bridge-after-handlers*/\n");
fs.writeFileSync(f, s);
console.log('patched');
