# Design

## Context

The `Layout.tsx` component contains the UI header with the Toolbar component. The version needs to be displayed next to "SOHT2 Server" title, sourced from `package.json`.

## Goals / Non-Goals

**Goals:**
- Display version number in header after "SOHT2 Server" title
- Read version from `package.json`
- Always show version on all pages (including login page)

**Non-Goals:**
- No version update mechanism
- No version API endpoint
- No version comparison or update detection

## Decisions

### Version Source
**Decision**: Read version directly from `package.json` using Vite's environment variable system.

**Rationale**: 
- Simplest approach - no additional build steps needed
- Vite automatically exposes `package.json` version via `import.meta.env.VITE_APP_VERSION`
- No runtime overhead - version is baked into the bundle at build time

**Alternatives Considered:**
1. Runtime JSON read - Rejected: Adds unnecessary HTTP request
2. Central version API - Rejected: Over-engineering for simple display
3. Build-time environment variable - Chosen: Clean, zero-runtime cost

### Conditional Display
**Decision**: Always show version number on all pages.

**Rationale**: Version information is useful for troubleshooting even on the login page.

**Alternatives Considered:**
1. Show only on authenticated pages - Rejected: Version info is useful for all users

## Risks / Trade-offs

- **Risk**: Version may be stale if package.json changes but dev server not restarted
  - **Mitigation**: Clear browser cache or restart dev server

## Open Questions

None
