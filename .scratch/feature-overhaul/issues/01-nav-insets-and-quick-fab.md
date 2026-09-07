# 01: Global Layout Clearance, Nav Insets & Bottom Navigation Quick FAB

**What to build:**
Ensure all scrollable views (Home, Ledger, Markets, Reports, Settings) apply adequate bottom content padding (100.dp) so that content is never clipped or occluded beneath the floating bottom navigation bar. Add a Quick Action Floating Action Button (+) centered on the bottom navigation dock to trigger an ultra-fast expense logging sheet in under 5 seconds.

**Blocked by:** None (can start immediately).

**Status:** completed

- [x] All LazyColumns and scrollable containers include 100.dp bottom padding.
- [x] Floating dock includes a prominent Quick Action (+) button.
- [x] Tapping (+) opens the fast transaction entry sheet with Amount, Category, Card, and Submit.
