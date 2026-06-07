# Third-party licenses — `:core:design-system`

## Material Symbols (icon vector drawables)

The icon vector drawables in `src/main/res/drawable/ic_*.xml` are **Material Symbols**
(Rounded style) by Google, vendored from
[github.com/google/material-design-icons](https://github.com/google/material-design-icons).

- **License:** Apache License 2.0 — <https://www.apache.org/licenses/LICENSE-2.0>
- **Copyright:** © Google LLC
- **Configuration:** weight 400, grade 0, optical size 24dp; `_filled` files are the fill-1 variant.
- **Local modification:** the upstream `android:tint="?attr/colorControlNormal"` attribute is removed
  so the `Icon` composable is the sole tint source (`LocalContentColor`).

To add or update an icon, download the matching Rounded vector drawable from
[fonts.google.com/icons](https://fonts.google.com/icons) (same axis values) and drop it in
`res/drawable/` as `ic_<name>.xml` (or `ic_<name>_filled.xml`). See spec §9 + `StaxIcons`.
