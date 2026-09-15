import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// A separate config rather than test options inside vite.config.ts: the dev
// server and the build never need the jsdom bits, and keeping them apart means
// `npm run build` cannot pick up a test-only plugin order by accident.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
