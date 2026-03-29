---
name: done-gate
description: "Validate harness policy and generated adapter consistency before declaring Done."
---

Run completion validation for the current workspace.

## Required Command

```bash
pwsh -File scripts/done-gate.ps1
```

## Behavior

1. Executes skill sync drift check (`scripts/sync-skills.ps1 -Check`).
2. Validates protected path policy from `/.harness/registries/done-gate.json`.
3. Applies minimum test/review evidence checks according to policy severity.
4. Blocks Done when any `block` level check fails.

## Output

- `PASS`: safe to mark Done.
- `FAIL`: list blocking checks and required remediation.
- `WARN`: non-blocking checks requiring explicit acknowledgement.
