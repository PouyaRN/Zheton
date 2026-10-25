import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';

/**
 * Builds into `dist`, which `sync/wrangler.jsonc` serves as the Worker's assets — so the app and
 * its API are one origin. No CORS to configure, and no second hostname for Iranian DNS to lose.
 *
 * No framework. The plan named Svelte for bundle size, and then the whole surface turned out to
 * be a list, a form and a pairing screen — about two hundred lines that render themselves. A
 * framework here would be a dependency bought for nothing, so this is plain TypeScript and the
 * deviation is deliberate. Reach for one the first time this needs real component state.
 *
 * Multi-page, because the calculators are also landing pages: each has its own static
 * `<title>`, meta description and canonical URL so a crawler that never runs JavaScript still
 * sees the right page. They all import the same `src/main.ts`, so the browser downloads one
 * bundle and the router decides which screen to render — the same bundle the ledger uses, which
 * is also what keeps the tools working offline after a visit to the home screen.
 *
 * Cloudflare serves `dist` with `not_found_handling: "single-page-application"`, so a tool URL
 * without its trailing slash falls back to the home document; the router recognises the path and
 * renders the tool, and the runtime canonical link is corrected to the slash form.
 */

const page = (path: string): string => fileURLToPath(new URL(path, import.meta.url));

const TOOL_PAGES = [
  'tools/index.html',
  'tools/mehrieh/index.html',
  'tools/takhir-tadieh/index.html',
  'tools/tavarom/index.html',
  'tools/hoghugh/index.html',
  'tools/hobab-sekke/index.html',
  'tools/hazine-adi/index.html',
  'tools/ghast-vam/index.html',
] as const;

export default defineConfig({
  build: {
    target: 'es2022',
    rollupOptions: {
      input: {
        home: page('index.html'),
        ...Object.fromEntries(
          TOOL_PAGES.map((path) => [
            path.replace(/\//g, '-').replace(/\.html$/, ''),
            page(path),
          ]),
        ),
      },
      // The whole app is smaller than one chunk boundary would be worth.
      output: { manualChunks: undefined },
    },
  },
});
