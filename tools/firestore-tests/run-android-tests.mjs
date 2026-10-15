import {spawnSync} from 'node:child_process';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';

// Runs only the emulator scenarios; emulators:exec provides FIRESTORE_EMULATOR_HOST and FIREBASE_AUTH_EMULATOR_HOST.
const root = fileURLToPath(new URL('../../', import.meta.url));
const gradlew = join(root, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
// EMULATOR_TESTS narrows the run, e.g. "*FirestoreEmulatorTest.every*".
const args = [':app:testDebugUnitTest', '--tests', process.env.EMULATOR_TESTS ?? '*Emulator*', '--rerun'];
const result = process.platform === 'win32'
  ? spawnSync('cmd.exe', ['/d', '/s', '/c', `"${gradlew}" ${args.join(' ')}`], {cwd: root, stdio: 'inherit', windowsHide: true, windowsVerbatimArguments: true})
  : spawnSync(gradlew, args, {cwd: root, stdio: 'inherit'});
if (result.error) console.error(result.error);
process.exit(result.status ?? 1);
