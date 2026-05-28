# ZeroGit Main Menu Design

## Goal

Restore a top-level `ZeroGit` entry in the IntelliJ IDEA main menu and make its contents match the requested menu structure, including a `GitLab CI` submenu.

## Current State

- The plugin still exposes `ZeroGit` actions through the project/editor popup menu.
- The plugin still exposes `ZeroGit` actions through the toolbar group.
- The top-level main menu entry was removed from `src/main/resources/META-INF/plugin.xml`.

## Proposed Change

Reintroduce a `ZeroGit` action group in `src/main/resources/META-INF/plugin.xml` and attach it to `MainMenu` after `ToolsMenu`.

The restored main menu will contain these items in this order:

1. `Generate Commit Message`
2. `AI Code Review`
3. `Maven` submenu
4. `Feature` submenu
5. `Release` submenu
6. `Hotfix` submenu
7. `GitLab CI` submenu
8. `GitFlow Guideline`

The `GitLab CI` submenu will contain:

- `Run CI Command`

## Implementation Scope

Only `src/main/resources/META-INF/plugin.xml` will change.

No Java action classes will be modified.
No popup menu entries will be changed.
No toolbar entries will be changed.

## Menu Structure Rules

- Reuse the existing action classes already used by popup and toolbar registrations.
- Keep action labels and descriptions consistent with the existing popup registrations.
- Keep the main menu placement rule as `MainMenu` with `anchor="after"` and `relative-to-action="ToolsMenu"`.

## Validation

- Build the plugin with `./gradlew buildPlugin`.
- Confirm the build succeeds without plugin descriptor errors.
- Review the diff to ensure only the main menu registration was added back.

## Risks

- `plugin.xml` contains repeated action registrations per surface, so main menu entries must be added carefully to avoid ID collisions.
- If the main menu structure diverges from popup registrations later, the plugin will need manual synchronization across both sections.
