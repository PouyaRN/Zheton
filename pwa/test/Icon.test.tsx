import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { Icon } from '../src/components/Icon';
import { ICON_MAP, resolveIconNodes } from '../src/utils/iconMap';

describe('PWA Icon & iconMap — Offline Bundled SVG Icons', () => {
  it('maps every required Material Symbols ligature to a valid Lucide SVG definition', () => {
    const names = Object.keys(ICON_MAP);
    expect(names.length).toBeGreaterThanOrEqual(45);

    for (const name of names) {
      const html = renderToStaticMarkup(<Icon name={name} size={20} />);
      expect(html, `Icon '${name}' must render an <svg>`).toContain('<svg');
      expect(html, `Icon '${name}' must include data-icon attribute`).toContain(`data-icon="${name}"`);
      expect(html, `Icon '${name}' must have non-empty path/shape children`).toMatch(/<(path|circle|rect|line|polyline|polygon)/);
    }
  });

  it('extracts size from Tailwind text-[Npx] className when size prop is omitted', () => {
    const html = renderToStaticMarkup(<Icon name="check_circle" className="text-[18px] text-emerald-500" />);
    expect(html).toContain('width="18"');
    expect(html).toContain('height="18"');
  });

  it('falls back gracefully for unknown icon names without throwing', () => {
    const nodes = resolveIconNodes('non_existent_icon_xyz');
    expect(nodes.length).toBeGreaterThan(0);
    const html = renderToStaticMarkup(<Icon name="non_existent_icon_xyz" />);
    expect(html).toContain('<svg');
  });
});
