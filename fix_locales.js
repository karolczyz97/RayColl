const fs = require('fs');
const dir = 'src/i18n/locales';

fs.readdirSync(dir).filter(f => f.endsWith('.ts')).forEach(f => {
  const p = dir + '/' + f;
  let s = fs.readFileSync(p, 'utf8');
  
  // Fix stats.overall_progress
  s = s.replace(/('stats\.overall_progress':\s*'.+?'),\s+('stats\.day\.mon':)/, "$1,\n  $2");
  
  // Fix archive.empty
  s = s.replace(/('archive\.empty':\s*'.+?'),\s+('archive\.auto_delete_in':)/, "$1,\n  $2");

  fs.writeFileSync(p, s);
  console.log('Fixed', p);
});
