import { sveltekit } from '@sveltejs/kit/vite';
import adapter from '@sveltejs/adapter-static';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [
    sveltekit({
      // Everything is prerendered. The fallback page lets /app (later phases) run as a client-side SPA.
      adapter: adapter({ pages: 'build', assets: 'build', fallback: '200.html', strict: false }),
      paths: { relative: false }
    })
  ],
  test: { include: ['tests/**/*.test.ts'], environment: 'node' }
});
