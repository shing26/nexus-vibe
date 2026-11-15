import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';

// The dictionary, initialized once per worker: components resolve strings
// through i18next, so tests need the same resources the app boots with.
import '../i18n';

// Testing Library's automatic cleanup only registers when it finds a global
// `afterEach`, which `globals: false` deliberately does not provide. Without
// this, every render in a file stays mounted in the same jsdom document and the
// second `getByText` matches two nodes.
afterEach(() => {
  cleanup();
});
