#!/usr/bin/env node
/**
 * Generates release metadata from git HEAD and writes compact JSON to stdout.
 *
 * Usage in GitHub Actions:
 *   {
 *     echo 'EXPO_PUBLIC_RELEASE_JSON<<EOF'
 *     node scripts/release-metadata.mjs
 *     echo 'EOF'
 *   } >> "$GITHUB_ENV"
 */

import { execSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = join(__dirname, '..');

function git(cmd) {
  return execSync(cmd, { cwd: root, encoding: 'utf8' }).trim();
}

const pkg = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8'));
const appVersion = pkg.version;

const commitSha = git('git rev-parse HEAD');
const shortSha = git('git rev-parse --short HEAD');
const commitTitle = git('git log -1 --format=%s');
const commitBody = git('git log -1 --format=%b');

const notes = commitBody
  .split('\n')
  .map((line) => line.trim())
  .filter((line) => line.startsWith('- ') || line.startsWith('* '))
  .map((line) => line.slice(2).trim())
  .filter(Boolean);

const runNumber = process.env.GITHUB_RUN_NUMBER ?? '0';
const webBuild = `${runNumber}-${shortSha}`;

const release = {
  appVersion,
  webBuild,
  commitSha,
  commitTitle,
  notes,
  publishedAt: new Date().toISOString(),
};

process.stdout.write(JSON.stringify(release));
