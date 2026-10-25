const fs = require('fs');
const dir = __dirname + '/src/';
let html = fs.readFileSync(dir + 'Index.html', 'utf8');
html = html.replace(/<\?!=\s*include\('([A-Za-z]+)'\);?\s*\?>/g,
  (m, name) => fs.readFileSync(dir + name + '.html', 'utf8'));
fs.writeFileSync(__dirname + '/www/index.html', html);
console.log('written', html.length, 'chars; leftover template tags:', (html.match(/<\?/g) || []).length);
