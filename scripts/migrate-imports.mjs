import { readFileSync, writeFileSync, readdirSync, statSync } from 'fs';
import { dirname, resolve, relative, join } from 'path';

const SRC = resolve('src');

function walkDir(dir) {
  const results = [];
  const entries = readdirSync(dir);
  for (const entry of entries) {
    const full = join(dir, entry);
    const stat = statSync(full);
    if (stat.isDirectory()) {
      if (entry === 'node_modules' || entry === '__tests__') continue;
      results.push(...walkDir(full));
    } else if (entry.endsWith('.ts') || entry.endsWith('.tsx')) {
      results.push(full);
    }
  }
  return results;
}

const files = walkDir(SRC);
const importRegex = /from\s+['"]((?:\.\.\/)+[^'"]+)['"]/g;
let changed = 0;

for (const file of files) {
  const content = readFileSync(file, 'utf8');
  const dir = dirname(file);

  const newContent = content.replace(importRegex, (_match, importPath) => {
    const absolute = resolve(dir, importPath).replace(/\\/g, '/');
    let target = relative(SRC, absolute).replace(/\\/g, '/');
    if (!target) return _match;

    const newImport = `from '@/${target}'`;
    const oldImport = `from '${importPath}'`;
    if (oldImport !== newImport) {
      return newImport;
    }
    return _match;
  });

  if (newContent !== content) {
    writeFileSync(file, newContent);
    changed++;
  }
}

console.log(`Updated ${changed} files`);
