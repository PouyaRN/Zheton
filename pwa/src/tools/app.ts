/**
 * Dispatches `/tools/` and `/tools/<tool>/` to the calculator screens.
 *
 * `main.ts` calls this before it looks for a session: the calculators are public, work without a
 * household, and must not open IndexedDB or ask for a pairing code. Returning false means "not a
 * tools route" and lets the ledger boot normally.
 */

import { TOOLS_INDEX_META, setPageMeta, toolsRoute } from './meta';
import { PAGES, renderToolsIndex, renderToolsNotFound } from './pages';

export function renderToolsRoute(app: HTMLElement): boolean {
  const route = toolsRoute(location.pathname);
  if (route == null) return false;

  if (route.kind === 'index') {
    setPageMeta(TOOLS_INDEX_META.title, TOOLS_INDEX_META.description, '/tools/');
    renderToolsIndex(app);
  } else if (route.kind === 'tool') {
    setPageMeta(route.tool.title, route.tool.description, route.tool.href);
    PAGES[route.tool.id]?.(app);
  } else {
    setPageMeta('این صفحه پیدا نشد | چقدر تومن', 'نشانی درخواستی در ماشین‌حساب‌ها پیدا نشد.', location.pathname);
    renderToolsNotFound(app);
  }
  window.scrollTo(0, 0);
  return true;
}
