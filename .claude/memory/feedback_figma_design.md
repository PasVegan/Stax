---
name: feedback-figma-design
description: "When building Figma designs that follow an existing UI kit (Material 3, iOS, etc.), always use the actual library components, variables, and text styles via importComponentByKeyAsync / importVariableByKeyAsync / importStyleByKeyAsync. Never hand-build M3 lookalikes with primitives."
metadata: 
  node_type: memory
  type: feedback
  originSessionId: f1bca87c-afef-4a53-8b77-71d4fb66e018
---

When building Figma designs in a kit-backed file (M3 Design Kit, iOS UI kit, etc.):

- **Use the actual library components** (import by key, instance, override properties). Do NOT hand-build app bars, FABs, buttons, nav bars, status bars, device frames, etc. from raw `figma.createFrame` primitives — even if it produces a visually-similar result.
- **Use the library's variables** for colors (`figma.variables.importVariableByKeyAsync`) so the user can swap themes (e.g. Material Theme Builder plugin re-themes the whole file via M3 token variables).
- **Use the library's text styles** for typography (`figma.importStyleByKeyAsync` + `node.textStyleId`). For M3 that means M3 type scale (display/headline/title/body/label) — do NOT hardcode font family/size. M3 uses Roboto Flex / Google Sans Flex via text styles; never hardcode "Roboto Regular".
- **Use library utility components** (device frame, status bar, system bars, icons). M3 kit has a device frame in utilities — use it. Google Symbols / Google Icons Library for icons.
- **Never use emoji as icons**. Always import proper icon components.

**Why:** Hand-built lookalikes can't be re-themed by plugins, don't update when the kit updates, and produce uncanny "AI UI" results. The user explicitly wants the file to remain plugin-compatible (Material Theme Builder).

**How to apply:** Before building any screen, inspect the linked library: search for components / variables / styles, OR import a known component and read its `boundVariables` + `textStyleId` to discover the M3 token IDs. Cache the IDs and reuse across screens.
