import { readFileSync, writeFileSync, readdirSync, statSync } from 'fs';
import { dirname, resolve, relative, join } from 'path';

const SRC = resolve('src');
const ASSETS = resolve('assets');
const write = process.argv.includes('--write');

function walkDir(dir) {
  const results = [];
  const entries = readdirSync(dir);
  for (const entry of entries) {
    const full = join(dir, entry);
    const stat = statSync(full);
    if (stat.isDirectory()) {
      if (entry === 'node_modules') continue;
      results.push(...walkDir(full));
    } else if (/\.(tsx?|jsx?)$/.test(entry)) {
      results.push(full);
    }
  }
  return results;
}

const files = walkDir(SRC);
let totalChangedFiles = 0;
let totalReplacedImports = 0;

console.log(write ? 'Applying changes...' : 'Dry-run mode (pass --write to apply changes):');

for (const file of files) {
  const content = readFileSync(file, 'utf8');
  const dir = dirname(file);
  let fileChanged = false;
  const changes = [];

  const regex = /(import\s+|from\s+|import\(\s*|require\(\s*)['"]((?:\.\.\/)+[^'"]+)['"]/g;

  const newContent = content.replace(regex, (match, prefix, importPath) => {
    const absolute = resolve(dir, importPath);
    
    // Check if the resolved absolute path is inside SRC
    if (absolute.startsWith(SRC)) {
      const rel = relative(SRC, absolute).replace(/\\/g, '/');
      const newImport = `${prefix}'@/${rel}'`;
      if (match !== newImport) {
        changes.push({ from: match, to: newImport });
        totalReplacedImports++;
        fileChanged = true;
        return newImport;
      }
    }
    
    // Check if the resolved absolute path is inside ASSETS
    if (absolute.startsWith(ASSETS)) {
      const rel = relative(ASSETS, absolute).replace(/\\/g, '/');
      const newImport = `${prefix}'@/assets/${rel}'`;
      if (match !== newImport) {
        changes.push({ from: match, to: newImport });
        totalReplacedImports++;
        fileChanged = true;
        return newImport;
      }
    }

    return match;
  });

  if (fileChanged) {
    totalChangedFiles++;
    console.log(`\nFile: ${relative(process.cwd(), file)}`);
    for (const change of changes) {
      console.log(`  - ${change.from}  =>  ${change.to}`);
    }
    if (write) {
      writeFileSync(file, newContent, 'utf8');
    }
  }
}

console.log(`\nSummary: ${totalChangedFiles} files would be updated, replacing ${totalReplacedImports} imports.`);
if (!write && totalChangedFiles > 0) {
  console.log('Run with --write to apply these changes.');
}
