# SIMple interface design

Editable Pencil source: [simple-dashboard.pen](simple-dashboard.pen).

The document contains 52 light/dark screen artboards (26 screen states) and a reusable purchase review card. Screen states are named in the Pencil layer tree.

## Workflow

The owner requests Pencil design updates for every interface change. Update the affected light/dark artboards alongside the app, inspect local previews and layout bounds. This preference is also recorded in `.cursor/rules/pencil-design.mdc`.

Navigation order: **Inicio → Historial → Estadísticas → Ajustes**. App destination IDs remain stable; only their visual order changes.

## Coverage

- Home resource grid, quick actions, low-resource notice, and four-tab navigation.
- Settings: Lines, Permissions & SMS, Privacy & storage, Data management, Updates, and About. Data management belongs within Privacy & storage. Developer credit, GitHub, copyright, and licenses stay in About.
- Data selection with SIM choice; consistent purchase review; blocked bank operation; restored result and acknowledgement.
- Voice/SMS selection and bank-menu review; missing-number guidance. The bank SIM is selected in the external Phone app, distinct from the destination line.
- Recharge bank SIM, destination/amount, review/PIN, authentication, expired authentication, uncertain result.
- Update download progress/cancel, cancelled download, verification, and recovered cached APK ready to install.
- History, statistics, transfers, and Play-distribution settings without direct APK updates or SMS import controls.

Artboards show expanded content/state references, not fixed device screenshots. Short states size to content; full Settings shows the scrollable content in one artboard. Android supplies native system bars, scrolling, permission dialogs, device authentication, dialer and installer screens. Platform dialogs are not replacements implemented by SIMple.

All sample phone numbers, balances, and responses are illustrative fixtures. No real PIN or private user data is included. Designs describe the local implementation; they do not imply live carrier payment verification.

## Validation

Pencil content-bound traversal found no clipped nodes. Representative light/dark exports were inspected visually. Both Android variants passed compilation, lint, and the existing 242 unit tests after the navigation reorder. Device interaction testing is separate and remains pending.

## Repository contents

Only the editable `.pen` design is versioned as a design asset. Exported images and the generated screen index remain local and are ignored by Git. Regenerate previews with Pencil when reviewing changes; do not add exported PNG, SVG, or PDF files to commits.
