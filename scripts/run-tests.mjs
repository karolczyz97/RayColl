import fs from 'node:fs';
import { createRequire } from 'node:module';
import ts from 'typescript';

const require = createRequire(import.meta.url);
const previousTsLoader = require.extensions['.ts'];

require.extensions['.ts'] = (module, filename) => {
  const source = fs.readFileSync(filename, 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      esModuleInterop: true,
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
    fileName: filename,
  });

  module._compile(outputText, filename);
};

try {
  const testFiles = [
    '../src/srs/__tests__/srsEngine.test.ts',
    '../src/store/persistence/__tests__/persistenceQueue.test.ts',
    '../src/store/persistence/__tests__/webLifecycle.test.ts',
    '../src/store/persistence/__tests__/firestoreSchema.test.ts',
    '../src/store/selectors/__tests__/merge.test.ts',
    '../src/store/selectors/__tests__/tombstones.test.ts',
    '../src/import/__tests__/importParser.test.ts',
    '../src/features/import/__tests__/importDraftHelpers.test.ts',
    '../src/store/actions/__tests__/groupActions.test.ts',
    '../src/store/actions/__tests__/cardActions.test.ts',
    '../src/features/study/session/__tests__/sessionState.test.ts',
    '../src/features/browse/__tests__/browseFilter.test.ts',
    '../src/utils/__tests__/gridLayout.test.ts',
  ];

  for (const file of testFiles) {
    const mod = require(file);
    if (typeof mod.runTests === 'function') {
      await mod.runTests();
    }
  }
} finally {
  if (previousTsLoader) {
    require.extensions['.ts'] = previousTsLoader;
  } else {
    delete require.extensions['.ts'];
  }
}
