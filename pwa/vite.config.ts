import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

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
  plugins: [react()],
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
      output: { manualChunks: undefined },
    },
  },
});
