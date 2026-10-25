const fs = require('fs');
const f = 'android/app/src/main/java/com/example/payamresan/SmsLabPlugin.java';
let s = fs.readFileSync(f, 'utf8');
if (s.includes('getDiagnostics')) { console.log('already patched'); process.exit(0); }
const DIAG = `    @PluginMethod
    public void getDiagnostics(PluginCall call) {
        android.content.SharedPreferences d = getContext().getSharedPreferences("smslab_diag", Context.MODE_PRIVATE);
        JSObject ret = new JSObject();
        ret.put("receiverRuns", d.getInt("recv_count", 0));
        ret.put("receiverLast", d.getLong("recv_last", 0L));
        ret.put("receiverErrors", d.getInt("recv_err", 0));
        ret.put("foreground", resumed);
        call.resolve(ret);
    }

`;
const edits = [
  ['private static SmsLabPlugin instance;', 'private static SmsLabPlugin instance;\n    private static volatile boolean resumed = false;'],
  ['protected void handleOnResume() {', 'protected void handleOnResume() {\n        resumed = true;'],
  ['protected void handleOnDestroy() {', 'protected void handleOnPause() {\n        resumed = false;\n    }\n\n    @Override\n    protected void handleOnDestroy() {'],
  ['if (p != null) p.flushResults();', 'if (p != null && resumed) p.flushResults();'],
  ['if (p != null) p.flushReceived();', 'if (p != null && resumed) p.flushReceived();'],
  ['    @PluginMethod\n    public void getDeviceInfo(PluginCall call) {', DIAG + '    @PluginMethod\n    public void getDeviceInfo(PluginCall call) {']
];
for (const e of edits) {
  if (!s.includes(e[0])) { console.log('pattern not found:', e[0].slice(0, 40)); process.exit(1); }
}
for (const e of edits) s = s.replace(e[0], () => e[1]);
fs.writeFileSync(f, s);
console.log('patched');
