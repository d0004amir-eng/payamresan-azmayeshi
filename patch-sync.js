const fs = require('fs');
function edit(file, pairs) {
  let s = fs.readFileSync(file, 'utf8');
  for (const p of pairs) {
    if (!s.includes(p[0])) { console.log('pattern not found in', file, ':', p[0].slice(0, 40)); process.exit(1); }
  }
  for (const p of pairs) s = s.replace(p[0], () => p[1]);
  fs.writeFileSync(file, s);
}
if (fs.readFileSync('src/Index.html', 'utf8').includes('SyncEngine')) { console.log('already patched'); process.exit(0); }
edit('src/Index.html', [[
  "<?!= include('SmsBridge'); ?>",
  "<?!= include('SmsBridge'); ?>\n<?!= include('SyncClient'); ?>\n<?!= include('SyncEngine'); ?>"
]]);
edit('src/App.html', [
  ["    if (!SmsBridge.capabilities().available) { await failMessage(id, 'UNAVAILABLE', ''); return; }",
   "    if (!SmsBridge.capabilities().available && window.SyncCfg && SyncCfg.ready() && window.SyncEngine) { await SyncEngine.enqueue(m); return; }\n    if (!SmsBridge.capabilities().available) { await failMessage(id, 'UNAVAILABLE', ''); return; }"],
  ["main().catch(e => Util.logError('main', e));",
   "window.SmsApp = { refreshUI: refreshUI, toast: toast, handleIncoming: handleIncoming, attemptSend: attemptSend };\nmain().catch(e => Util.logError('main', e));"]
]);
console.log('patched');
