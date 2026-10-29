/**
 * The small DOM vocabulary the calculator pages share: parse a field, print a result, say what
 * went wrong. No component framework, no virtual DOM — the pages are forms and a paragraph of
 * output, which is exactly the amount of UI this surface needs.
 */

import { parseNumber, formatToman, formatPercent } from './number';

export { formatPercent, formatToman, parseNumber };

export function escape(value: string): string {
  return value.replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!,
  );
}

/** One element from an HTML string, for pages that replace the whole screen. */
export function el(html: string): HTMLElement {
  const wrapper = document.createElement('div');
  wrapper.innerHTML = html.trim();
  return wrapper.firstElementChild as HTMLElement;
}

export function numberValue(root: ParentNode, id: string): number | null {
  const input = root.querySelector<HTMLInputElement>(`#${id}`);
  return input == null ? null : parseNumber(input.value);
}

export function selectValue(root: ParentNode, id: string): string {
  return root.querySelector<HTMLSelectElement>(`#${id}`)?.value ?? '';
}

export function setInputValue(root: ParentNode, id: string, value: string | number): void {
  const input = root.querySelector<HTMLInputElement>(`#${id}`);
  if (input != null) input.value = String(value);
}

export interface ResultRow {
  label: string;
  value: string;
  /** Smaller line under the value, for a formula or a caveat. */
  hint?: string;
}

/**
 * The one result card. `figure` is already formatted (Persian digits, no unit); `unit` is
 * appended once so it cannot be separated from the number by a line break.
 */
export function resultHtml(
  label: string,
  figure: string,
  unit: string,
  rows: readonly ResultRow[] = [],
  note?: string,
): string {
  return `
    <div class="result-label">${escape(label)}</div>
    <div class="result-figure">
      <span class="figure">${escape(figure)}</span>${unit ? `<span class="unit">${escape(unit)}</span>` : ''}
    </div>
    ${
      rows.length
        ? `<dl class="result-rows">${rows
            .map(
              (row) => `
          <div>
            <dt>${escape(row.label)}</dt>
            <dd>${escape(row.value)}${row.hint ? `<span class="hint">${escape(row.hint)}</span>` : ''}</dd>
          </div>`,
            )
            .join('')}</dl>`
        : ''
    }
    ${note ? `<p class="fineprint">${escape(note)}</p>` : ''}
  `;
}

export function errorHtml(message: string): string {
  return `<p class="error" role="alert">${escape(message)}</p>`;
}

/** The result card's first-paint state: an invitation to type, not an error. */
export function emptyResultHtml(message = 'عددها را وارد کن تا نتیجه را ببینی.'): string {
  return `<p class="muted result-empty">${escape(message)}</p>`;
}

/**
 * Wires a form so it calculates on submit and on every input event, then calculates once so the
 * page never opens empty. `input` covers selects and typed figures in one listener.
 */
export function bindCalculator(
  root: ParentNode,
  formId: string,
  run: () => void,
): void {
  const form = root.querySelector<HTMLFormElement>(`#${formId}`);
  if (form == null) return;
  form.addEventListener('submit', (event) => {
    event.preventDefault();
    run();
  });
  form.addEventListener('input', run);
  run();
}

/** Persian year options for a select, from the CPI table's range. */
export function yearOptions(
  years: readonly number[],
  selected: number,
  label: (year: number) => string = (year) => String(year),
): string {
  return years
    .map(
      (year) =>
        `<option value="${year}"${year === selected ? ' selected' : ''}>${escape(label(year))}</option>`,
    )
    .join('');
}
