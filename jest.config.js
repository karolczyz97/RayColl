module.exports = {
  preset: 'jest-expo',
  setupFilesAfterEnv: ['<rootDir>/jest.setup.js'],
  testMatch: [
    '**/*.test.tsx',
    '**/test/__tests__/**/*.test.ts',
    '**/i18n/__tests__/**/*.test.ts',
    '**/expressive/__tests__/expressiveGeometry.test.ts',
    '**/navigation/__tests__/navigationDestinations.test.ts',
    '**/deepEqual.test.ts',
  ],
};
