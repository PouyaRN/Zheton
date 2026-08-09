# 04 — Filter sheet v1: structured tokens + confirmed batch actions

**What to build:** The timeline search box opens a filter sheet that builds a query from structured tokens (field + operator + value: merchant contains, category, bank, direction), AND-composed, with matches streamed back into the existing timeline list. While in selection mode the batch bar shows the live match count, «select all» covers every match in the filter (not only the rendered rows), and each bulk action (category assignment, hide, flip, delete) first confirms «اعمال به N مورد؟» with the count before applying. Editing any criterion clears the current selection, so a batch can never act on rows that have just filtered out of view.

**Blocked by:** none — builds on the existing search field, selection mode, and batch bar.

**Status:** done

- [ ] Tapping the search field opens a filter sheet; choosing tokens narrows the timeline live.
- [ ] All tokens are AND-combined; a token can be removed or edited without rebuilding the whole query from scratch.
- [ ] In selection mode the batch bar shows how many matches the filter has; «select all» selects all of them, not only the rendered rows.
- [ ] Category assignment (and the other batch actions) shows a confirm with the affected count before writing.
- [ ] Changing any criterion clears the selection and its batch bar.