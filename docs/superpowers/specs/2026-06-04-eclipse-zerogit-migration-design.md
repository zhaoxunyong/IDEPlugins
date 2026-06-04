# Eclipse ZeroGit Migration Design

**Date:** 2026-06-04  
**Scope:** Rebuild the Eclipse plugin as a ZeroGit-aligned plugin that matches the current IDEA and VS Code capability model.

## Goal

Replace the legacy Eclipse `Aeasycredit Plugin` implementation with a new ZeroGit Eclipse plugin that:

- exposes the same 13 user-facing entries as IDEA and VS Code
- reuses the shared ZeroGit shell scripts instead of re-implementing Git Flow logic in Java
- preserves Eclipse-native interaction patterns through menus, dialogs, jobs, and console output
- does not attempt compatibility with the old plugin identity, package names, or preference keys

## Approved Constraints

- No backward compatibility with the legacy Eclipse plugin identity is required.
- The implementation should use a new ZeroGit plugin identity and package structure.
- The technical baseline remains `Java 8 + traditional Eclipse PDE plugin`.
- The target is a usable migration, not a placeholder skeleton.

## Approaches Considered

### 1. Rewrite the existing legacy plugin in place

Pros:

- fastest initial start
- can reuse more old files directly

Cons:

- leaves legacy naming, old command semantics, and historical branching assumptions mixed into the new implementation
- makes future maintenance harder because old and new capability models stay entangled

### 2. Build a new ZeroGit Eclipse plugin structure and selectively reuse infrastructure

Pros:

- aligns cleanly with the current ZeroGit product model
- keeps clear boundaries between UI, orchestration, and script execution
- makes later command additions easier because the execution model is unified

Cons:

- larger initial refactor because plugin identity, menu registrations, and package structure all move at once

### 3. Keep one bundle but run old and new structures side by side

Pros:

- smaller upfront engineering than a clean rebuild

Cons:

- worst long-term structure because the codebase would carry two mental models at once
- provides neither true compatibility nor a clean migration

### Recommendation

Use approach 2. The user explicitly chose not to preserve backward compatibility, so the implementation should take advantage of that and remove the old DeployPlugin structure as the organizing center.

## Product Surface

The Eclipse plugin must expose these 13 entries:

1. `Generate Commit Message`
2. `AI Code Review`
3. `Maven Change`
4. `Start New Feature`
5. `Finish Feature`
6. `Rebase Feature`
7. `Merge Request`
8. `Start New Release`
9. `Finish Release`
10. `Start New Hotfix`
11. `Finish Hotfix`
12. `Run CI Command`
13. `GitFlow Guideline`

### Menu Structure

Top-level menu group:

- `ZeroGit`

Submenus:

- `Maven`
- `Feature`
- `Release`
- `Hotfix`
- `GitLab CI`

Primary entry locations:

- project context menu
- main menu ZeroGit group

Optional:

- toolbar shortcuts for selected actions, but not as the only access path

## High-Level Architecture

The new plugin should separate Eclipse integration concerns from ZeroGit business orchestration. The Eclipse side gathers context and user input, while the ZeroGit core decides validation, command preparation, and execution order.

### Suggested Package / Module Structure

- `com.zerofinance.zerogit.eclipse.plugin`
  - activator, plugin metadata access, plugin-level constants
- `com.zerofinance.zerogit.eclipse.actions`
  - 13 command handlers, one per user-visible entry
- `com.zerofinance.zerogit.eclipse.flow`
  - orchestration services for each ZeroGit workflow
- `com.zerofinance.zerogit.eclipse.exec`
  - command runner, console writer, job integration, script resolution
- `com.zerofinance.zerogit.eclipse.git`
  - repo root resolution, branch/tag queries, semver support, Maven root detection, CI file access
- `com.zerofinance.zerogit.eclipse.ui`
  - dialog wrappers, chooser utilities, user-facing confirmation flows
- `com.zerofinance.zerogit.eclipse.settings`
  - preference keys, defaults, settings page, validated configuration access
- `com.zerofinance.zerogit.eclipse.model`
  - small data objects such as command request, branch option, CI command option, version suggestion

### Responsibility Boundaries

- handlers only translate an Eclipse command invocation into a use-case call
- flow services own ZeroGit-specific validation and parameter collection order
- exec services own script preparation, `gitCheck`, process execution, console streaming, and refresh
- git services own repository inspection and parsing rules
- ui services own Eclipse-native input and confirmation dialogs

This keeps the Eclipse plugin as a thin orchestration layer around the shared shell scripts instead of a second implementation of Git Flow.

## Command Execution Model

All commands should flow through a unified execution contract rather than building ad hoc shell invocations in each handler.

### Command Categories

#### A. Streaming shell execution

Used for:

- `Start New Feature`
- `Finish Feature`
- `Rebase Feature`
- `Merge Request`
- `Start New Release`
- `Start New Hotfix`
- `Maven Change`
- `Generate Commit Message`
- `AI Code Review`

Behavior:

- run in an Eclipse Job
- stream stdout and stderr to the Eclipse Console
- show success notification on completion
- refresh project resources after success
- on failure, keep full console output and show a readable summary

#### B. Synchronous execution with output parsing

Used for:

- `Finish Release`
- `Finish Hotfix`
- some `Run CI Command` paths when output needs post-processing

Behavior:

- still execute from a Job, not the UI thread
- capture complete stdout and stderr
- parse structured lines such as remaining release/hotfix summaries
- show tailored follow-up messaging after execution

#### C. Repository analysis plus command execution

Used for:

- `Maven Change`
- `Run CI Command`
- version-suggestion flows for release and hotfix

Behavior:

- inspect local repository state first
- collect choices through native Eclipse dialogs
- pass validated parameters to the same unified runner

## Unified Runner Design

Introduce a central service such as `ZeroGitCommandRunner` with a request object that contains:

- repository root path
- optional module path
- script name or direct command
- argument list
- whether `gitCheck` is required
- whether output must be captured and parsed
- success and failure message metadata
- whether project refresh is required

### Execution Pipeline

1. Resolve context from the current selection.
2. Resolve Git repository root.
3. Resolve Maven module root when required.
4. Read and validate plugin settings.
5. On Windows, verify `Git Home` or `bash.exe` availability before any script work.
6. Clear ZeroGit script cache in the temp directory.
7. Resolve the script:
   - first from the repository root
   - otherwise by downloading from `Script URL`
8. Run `gitCheck.sh` unless the command is in the exclusion list.
9. Execute the target script or direct command.
10. Stream console output and retain full raw output.
11. Summarize result to the user and refresh project resources.

### `gitCheck` Exclusions

The following commands must not run `gitCheck` first:

- `Generate Commit Message`
- `AI Code Review`
- `Maven Change`
- `Rebase Feature`

## Script Reuse and Cache Rules

The Eclipse plugin must match IDEA and VS Code script behavior:

- prefer same-named scripts in the current Git repository root
- otherwise download from configured `Script URL`
- clear cached temporary ZeroGit scripts before each run
- keep support for repository-root hooks:
  - `Pre_<ScriptName>.sh`
  - `Post_<ScriptName>.sh`

The Eclipse repository's own `scripts/` directory is a development asset, not the runtime override location for business repositories.

## Platform Strategy

### macOS / Linux

- execute with `bash`

### Windows

- require a configured Git installation directory or `bash.exe` path
- if invalid or missing, route the user to the settings page and abort execution
- when `debug=true`, run bash with `-x`

### Console and Error Behavior

- all command output must be visible in the Eclipse Console
- successful runs must produce a clear success indication
- failure dialogs must show a short, understandable summary
- raw shell output must remain available in the Console for diagnosis

## Settings Model

Create a new settings page labeled `Git Deploy Settings` with these keys:

| Setting | Key | Default |
|---|---|---|
| Git Home / Git Bash | `gitDeployPluginGitHomeKey` | empty |
| Script URL | `gitDeployPluginScriptURLKey` | `https://gitlab.zerofinance.net/dave.zhao/deployPlugin/-/raw/main/git-flow` |
| Debug | `gitDeployPluginDebugKey` | `false` |
| Group Names | `gitDeployPluginGroupNamesKey` | `a b c` |
| Group Default Name | `gitDeployPluginGroupNameKey` | first valid group |
| Git MR Assignees | `gitDeployPluginGitMrAssigneesKey` | `faker.zhou justin.wang conan.chen rain.he` |
| Check Git Version | `gitDeployPluginCheckGitVersionKey` | `false` |

### Settings Rules

- `groupName` is only the default highlighted selection
- commands that need a group must still prompt from `groupNames`
- legacy username and password settings are removed from the target model

## Shared Services

### `ZeroGitSettings`

Responsibilities:

- load defaults and persisted values
- validate `groupNames`
- expose default group selection
- normalize MR assignee configuration
- expose `debug` and `checkGitVersion`

### `ZeroGitSelectionService`

Responsibilities:

- group chooser
- feature/release/hotfix branch chooser
- Maven module chooser
- MR assignee chooser with manual entry
- version input dialogs with suggested defaults
- confirmation dialogs for risky actions

This service ensures the Eclipse implementation satisfies the PRD requirement to use native selection dialogs and confirmation dialogs.

### `ZeroGitGitService`

Responsibilities:

- detect repository root
- read current branch
- list local feature branches by group
- list remote release/hotfix branches
- read and normalize tag lists
- locate `.gitlab-ci.yml`
- support detached HEAD and upstream validation scenarios as needed by `gitCheck`

### `ZeroGitVersionService`

Responsibilities:

- parse SemVer values
- compute release suggestions:
  - global max version -> `minor + 1`, `patch = 0`
- compute hotfix suggestions:
  - latest production tag / related branches -> `patch + 1`
- detect group-level collisions
- recognize supported tag formats:
  - `vX.Y.Z`
  - `release/<group>/X.Y.Z-YYYYMMDDHHmm`
  - `hotfix/<group>/X.Y.Z-YYYYMMDDHHmm`

### `GitlabCiCommandReader` and parser

Responsibilities:

- read `.gitlab-ci.yml` from repository root
- extract `BASE_EXEC_CMD` from:
  - top-level `BASE_EXEC_CMD`
  - top-level `variables.BASE_EXEC_CMD`
  - job-level `variables.BASE_EXEC_CMD`
- provide normalized command options for user selection

### `MavenProjectService`

Responsibilities:

- walk upward from the current selection to find the nearest Maven project root
- if multiple Maven submodules are relevant from the repo root context, prompt the user to select one
- support version suggestion and validation inputs for `Maven Change`

## Command-Specific Behavioral Rules

### `Start New Feature`

- choose group first
- input must start with `feature/<group>/`
- suffix must match `^\d+-\S.*$`
- parameters: `[groupName, fullFeatureName]`

### `Finish Feature`

- confirm that MR to `develop-<group>` has already been merged
- list only local `feature/<group>/` branches
- sort by numeric prefix descending
- parameters: `[groupName, selectedFeatureBranch]`

### `Rebase Feature`

- choose group
- current branch must start with `feature/<group>/`
- does not run `gitCheck`
- parameters: `[groupName, currentBranch]`

### `Merge Request`

- choose group
- choose or input assignee
- assignee cannot be empty
- default target branch is `develop-<group>`
- parameters: `[groupName, assignee]`

### `Maven Change`

- find nearest Maven module, not just the repo root
- choose `release` or `snapshot`
- `release` requires valid `-SNAPSHOT` or `-RCN` source version
- parameters: `[groupName, mavenVersion]`

### `Start New Release`

- confirm release readiness first
- warn if dependencies still include `-SNAPSHOT`
- suggest version using shared SemVer rules
- parameters: `[groupName, fullReleaseName]`

### `Finish Release`

- no initial group selection
- must confirm maintainer authority and production completion
- choose a `release/<group>/X.Y.Z` branch
- parse resulting output for remaining release/hotfix branches
- parameters: `[selectedReleaseBranch]`

### `Start New Hotfix`

- confirm mainline sync expectations first
- warn if dependencies still include `-SNAPSHOT`
- find latest production tag before proceeding
- suggest version using shared SemVer rules
- parameters: `[groupName, fullHotfixName, baseTag]`

### `Finish Hotfix`

- no initial group selection
- shares `FinishRelease.sh`
- choose a `hotfix/<group>/X.Y.Z` branch
- parse resulting output for remaining release/hotfix branches
- parameters: `[selectedHotfixBranch]`

### `Generate Commit Message`

- operate only on staged changes
- require local `codex`
- generate message only, do not auto-commit

### `AI Code Review`

- operate only on staged changes
- require local `codex`
- require the `code-review-expert` skill on the host

### `Run CI Command`

- parse `.gitlab-ci.yml`
- present extracted `BASE_EXEC_CMD` choices
- run the selected command through the shared execution layer

### `GitFlow Guideline`

- open the configured guideline link in the browser

## Reuse Strategy from the Legacy Eclipse Plugin

Reusable ideas or infrastructure:

- Eclipse `Job` based execution
- `MessageConsole` output integration
- project resource refresh after successful operations
- some Commons Exec based process execution patterns

Not reusable as-is:

- legacy command semantics
- old preference keys and settings page layout
- old package structure and plugin identity
- old release/hotfix/versioning logic based on `*.x`, `*.release`, `*.hotfix`
- old handler-level embedded Git Flow logic

## Verification Strategy

### Service-Level Tests

Add tests for logic that does not require full Eclipse UI bootstrapping:

- group parsing and default selection
- MR assignee normalization
- SemVer parsing and suggestion generation
- tag format recognition
- feature/release/hotfix branch filtering and sorting
- `.gitlab-ci.yml` `BASE_EXEC_CMD` extraction
- Maven version suggestion rules

### Eclipse Integration Verification

Validate:

- menu registration and command visibility
- handler resolution from project context
- preference persistence
- console streaming
- job completion handling
- project refresh after success
- readable error summary without losing raw console output

### Script Contract Verification

Validate:

- repository-root script override wins over remote download
- cache is cleared before each run
- `gitCheck` exclusion rules are correct
- `FinishRelease.sh` output parsing works
- Windows bash path resolution and debug mode work

### Manual Acceptance Flows

Run at least these flows end to end:

- `Start New Feature`
- `Finish Feature`
- `Merge Request`
- `Start New Release`
- `Finish Release`
- `Start New Hotfix`
- `Finish Hotfix`
- `Run CI Command`

## Delivery Sequence

### Phase 1

- create the new plugin identity and package structure
- add settings page and preference model
- add menu and command registrations
- implement shared execution, script, and git infrastructure

### Phase 2

- wire all 13 commands to the new flow services
- prioritize working Feature, Release, Hotfix, MR, and CI flows
- align command prompts and parameter order with IDEA and VS Code

### Phase 3

- remove or quarantine remaining legacy assets
- clean up old naming, obsolete scripts, and historical branch-model assumptions
- verify update site / feature packaging against the new plugin identity

## Key Risks

1. Eclipse selection contexts are more heterogeneous than IDEA action contexts, so repo-root and Maven-root detection must be robust.
2. `Finish Release` and `Finish Hotfix` cannot rely on exit codes alone; output parsing must be treated as a first-class requirement.
3. Windows Git Bash detection is a hard usability boundary; weak validation would make the first release unreliable.
4. Legacy Eclipse utility classes mix infrastructure and old business semantics, so reuse must stay selective.

## Implementation Direction

Proceed with a clean ZeroGit Eclipse plugin rebuild on top of `Java 8 + PDE`, reusing only generic execution infrastructure from the old plugin and aligning all workflow behavior to the shared ZeroGit scripts and current IDEA / VS Code semantics.
