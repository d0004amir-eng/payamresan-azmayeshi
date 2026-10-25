const fs = require('fs');
function edit(file, pairs) {
  let s = fs.readFileSync(file, 'utf8');
  for (const p of pairs) {
    if (!s.includes(p[0])) { console.log('pattern not found in', file, ':', p[0].slice(0, 40)); process.exit(1); }
  }
  for (const p of pairs) s = s.replace(p[0], () => p[1]);
  fs.writeFileSync(file, s);
}
const bridge = fs.readFileSync('src/SmsBridge.html', 'utf8');
if (bridge.includes('diagnostics')) { console.log('already patched'); process.exit(0); }
edit('src/SmsBridge.html', [[
  '  return { init, on, isNative,',
  "  async function diagnostics() {\n    if (!plugin) return null;\n    try { return await plugin.getDiagnostics(); } catch (e) { Util.logError('diagnostics', e); return null; }\n  }\n\n  return { init, on, isNative, diagnostics,"
]]);
edit('src/App.html', [[
  "  const box = $('sms-detail');",
  "  const diag = await SmsBridge.diagnostics();\n  if (diag) {\n    smsRows.push(['اجرای گیرندهٔ پیامک (از زمان نصب)', Util.toFa(diag.receiverRuns)]);\n    smsRows.push(['آخرین اجرای گیرنده', diag.receiverLast ? Util.formatDate(diag.receiverLast) + ' ' + Util.formatTime(diag.receiverLast) : 'هرگز']);\n    if (diag.receiverErrors) smsRows.push(['خطای گیرنده', Util.toFa(diag.receiverErrors)]);\n  }\n  const box = $('sms-detail');"
]]);
console.log('patched');
