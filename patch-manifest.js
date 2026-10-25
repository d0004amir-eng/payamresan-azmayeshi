const fs = require('fs');
const p = 'android/app/src/main/AndroidManifest.xml';
let x = fs.readFileSync(p, 'utf8');
if (x.includes('SmsReceiver')) { console.log('already patched'); process.exit(0); }
if (!x.includes('<application') || !x.includes('</application>')) { console.log('unexpected manifest'); process.exit(1); }
const perms = [
  '<uses-feature android:name="android.hardware.telephony" android:required="false" />',
  '    <uses-permission android:name="android.permission.SEND_SMS" />',
  '    <uses-permission android:name="android.permission.RECEIVE_SMS" />',
  '    <uses-permission android:name="android.permission.READ_PHONE_STATE" />',
  '',
  '    <application'
].join('\n');
const recv = [
  '        <receiver android:name=".SmsReceiver" android:exported="true" android:permission="android.permission.BROADCAST_SMS">',
  '            <intent-filter>',
  '                <action android:name="android.provider.Telephony.SMS_RECEIVED" />',
  '            </intent-filter>',
  '        </receiver>',
  '        <receiver android:name=".SmsResultReceiver" android:exported="false" />',
  '    </application>'
].join('\n');
x = x.replace('<application', perms).replace('</application>', recv);
fs.writeFileSync(p, x);
console.log('patched');
