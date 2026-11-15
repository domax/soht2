# Proposal

## Why

Users currently have no way to identify which version of the SOHT2 UI is running. This makes troubleshooting, bug reporting, and verifying deployments difficult. Displaying the current app version in the UI header provides immediate visibility into the running version.

## What Changes

- Display the current app version number in the UI header, immediately after the "SOHT2 Server" title
- Version will be sourced from `package.json` in `soht2-ui`
- Implementation will be in `Layout.tsx` Toolbar component

## Capabilities

### New Capabilities
- `ui-version-display`: Display application version number in the UI header for version visibility and troubleshooting

### Modified Capabilities
- None

## Impact

- **Affected code**: `soht2-ui/src/components/Layout.tsx` (Toolbar component)
- **Affected files**: Version will be read from `soht2-ui/package.json`
- **No API changes**: Purely a UI display enhancement
- **No breaking changes**: Backwards compatible addition
