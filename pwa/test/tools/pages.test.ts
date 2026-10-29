import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import {
  SITE_ORIGIN,
  TOOLS,
  TOOLS_INDEX_META,
  canonicalUrl,
  toolsRoute,
} from '../../src/tools/meta';
import { PAGES } from '../../src/tools/pages';

/**
 * The SEO surface is generated from `meta.ts` for the router and written by hand into the static
 * HTML for crawlers. These tests keep the two copies honest: a title edited in one place and not
 * the other fails here rather than quietly shipping a page whose tab and search result disagree.
 */

const ROOT = join(__dirname, '..', '..');

function read(path: string): string {
  return readFileSync(join(ROOT, path), 'utf8');
}

function titleOf(html: string): string {
  return html.match(/<title>([^<]*)<\/title>/)?.[1] ?? '';
}

function descriptionOf(html: string): string {
  return html.match(/<meta\s+name="description"\s+content="([^"]*)"/s)?.[1] ?? '';
}

function canonicalOf(html: string): string {
  return html.match(/<link rel="canonical" href="([^"]*)"/)?.[1] ?? '';
}

describe('static tool pages', () => {
  it('has one HTML file per tool, in Persian, RTL, mobile-first', () => {
    for (const tool of TOOLS) {
      const html = read(`tools/${tool.id}/index.html`);
      expect(html, tool.id).toContain('<html lang="fa" dir="rtl">');
      expect(html, tool.id).toContain('name="viewport"');
      expect(html, tool.id).toContain('width=device-width');
      expect(titleOf(html), tool.id).toBe(tool.title);
      expect(descriptionOf(html), tool.id).toBe(tool.description);
      expect(canonicalOf(html), tool.id).toBe(canonicalUrl(tool.href));
      expect(html, tool.id).toContain('<meta property="og:title"');
    }
  });

  it('has the /tools/ index with its own metadata', () => {
    const html = read('tools/index.html');
    expect(html).toContain('<html lang="fa" dir="rtl">');
    expect(titleOf(html)).toBe(TOOLS_INDEX_META.title);
    expect(descriptionOf(html)).toBe(TOOLS_INDEX_META.description);
    expect(canonicalOf(html)).toBe(canonicalUrl('/tools/'));
  });

  it('has a renderer for every listed tool and no orphan route', () => {
    expect(TOOLS).toHaveLength(7);
    for (const tool of TOOLS) expect(typeof PAGES[tool.id], tool.id).toBe('function');
    expect(Object.keys(PAGES).sort()).toEqual(TOOLS.map((tool) => tool.id).sort());
  });
});

describe('sitemap and robots', () => {
  it('lists the home page and every tool once', () => {
    const sitemap = read('public/sitemap.xml');
    expect(sitemap).toContain('<?xml version="1.0" encoding="UTF-8"?>');
    for (const path of ['/', '/tools/', ...TOOLS.map((tool) => tool.href)]) {
      expect(sitemap, path).toContain(`<loc>${SITE_ORIGIN}${path}</loc>`);
    }
  });

  it('points robots.txt at the sitemap', () => {
    const robots = read('public/robots.txt');
    expect(robots).toContain('User-agent: *');
    expect(robots).toContain(`Sitemap: ${SITE_ORIGIN}/sitemap.xml`);
  });
});

describe('tools routes', () => {
  it('matches the index with or without a trailing slash', () => {
    expect(toolsRoute('/tools/')).toEqual({ kind: 'index' });
    expect(toolsRoute('/tools')).toEqual({ kind: 'index' });
  });

  it('matches every tool with or without a trailing slash and query', () => {
    for (const tool of TOOLS) {
      expect(toolsRoute(tool.href)).toEqual({ kind: 'tool', tool });
      expect(toolsRoute(tool.href.replace(/\/$/, ''))).toEqual({ kind: 'tool', tool });
      expect(toolsRoute(`${tool.href}?utm=x`)).toEqual({ kind: 'tool', tool });
    }
  });

  it('treats an unknown tool as not-found rather than redirecting home', () => {
    expect(toolsRoute('/tools/nope/')).toEqual({ kind: 'not-found' });
    expect(toolsRoute('/tools/nope/deeper/')).toEqual({ kind: 'not-found' });
  });

  it('leaves the ledger routes alone', () => {
    expect(toolsRoute('/')).toBeNull();
    expect(toolsRoute('/ledger')).toBeNull();
    expect(toolsRoute('/toolsmith/')).toBeNull();
  });

  it('canonicalises to an absolute trailing-slash URL', () => {
    expect(canonicalUrl('/')).toBe(`${SITE_ORIGIN}/`);
    expect(canonicalUrl('/tools/mehrieh')).toBe(`${SITE_ORIGIN}/tools/mehrieh/`);
    expect(canonicalUrl('/tools/mehrieh/?a=1')).toBe(`${SITE_ORIGIN}/tools/mehrieh/`);
  });
});

describe('home entry point', () => {
  it('links the ledger, the pairing screens and the join screen to /tools/', () => {
    const main = read('src/main.ts');
    const links = main.match(/href="\/tools\/"/g) ?? [];
    expect(links.length).toBeGreaterThanOrEqual(3);
    expect(main).toContain('renderToolsRoute(app)');
  });
});
