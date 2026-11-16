# SIMple dashboard design

Editable Pencil source: [simple-dashboard.pen](simple-dashboard.pen).

- [Light preview](previews/dashboard-light.png)
- [Dark preview](previews/dashboard-dark.png)

Both screens use illustrative balances and recipients; they are design examples, not live SIM data.
The design was created and checked in Pencil, then implemented in `Dashboard.kt`.

The home screen keeps one unified balance/resources card, a compact header and SIM selector,
a row of three quick actions, and inset navigation. Resources retain 48 dp touch targets;
large text switches actions to a vertical layout. Expanded details, expired-resource labels,
query progress, alerts and all existing actions remain available.

Review checklist: content bounds checked in Pencil; light/dark contrast and spacing reviewed
from exported previews; Android build and lint required before delivery. Android uses native
system bars and scrollable content, so its total viewport differs from the full Pencil artboard.
