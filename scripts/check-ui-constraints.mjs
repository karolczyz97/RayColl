import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const srcDir = path.join(root, 'src');
const expressiveDir = path.join(srcDir, 'components', 'expressive');
const sourceExtensions = new Set(['.ts', '.tsx']);

function walk(dir, files = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(fullPath, files);
    } else if (sourceExtensions.has(path.extname(entry.name))) {
      files.push(fullPath);
    }
  }
  return files;
}

const errors = [];
const srcFiles = walk(srcDir);

for (const file of srcFiles) {
  const source = fs.readFileSync(file, 'utf8');
  const relative = path.relative(root, file);
  const isExpressiveFile = file.startsWith(expressiveDir);

  if (!isExpressiveFile && /from ['"]react-native-svg['"]/.test(source)) {
    errors.push(`${relative}: import react-native-svg through src/components/expressive`);
  }

  if (isExpressiveFile && /#[0-9a-fA-F]{3,8}\b/.test(source)) {
    errors.push(`${relative}: expressive SVG files must use theme/token colors, not hex literals`);
  }
}

if (errors.length > 0) {
  console.error(errors.join('\n'));
  process.exit(1);
}

console.log('UI constraints passed');

