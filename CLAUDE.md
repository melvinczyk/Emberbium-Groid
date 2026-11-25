# Emberbium — Claude Code Rules

Groid-owned Forge 1.20.1 mod, forked from [shuttheirprayer/Emberbium](https://github.com/shuttheirprayer/Emberbium): our history sits on upstream `c8280457`, starting with the "Groid changes on top of upstream Emberbium (0.3.32)" commit. Remote `origin` is the Groid GitHub repo; never `git push`, `git pull` or `git fetch` unless the user asks. Java 21 builds the mod (`JAVA_HOME=$(/usr/libexec/java_home -v 21)` on macOS; Java 25 breaks Gradle).

## Branching: never work on `master`

`master` is always releasable. For every change, however small:

1. `git status` must be clean on `master` first; commit or stash anything pending.
2. `git switch -c claude/<short-task> master`
3. Make the change, committing as you go (see below).
4. When finished, build (`./gradlew build -x test`) and fix errors.
5. Bump the version (see below), commit, then merge: `git switch master && git merge --no-ff claude/<short-task>`.
6. Build again on `master`, tag, then delete the branch: `git branch -d claude/<short-task>`.

Never leave a task half-merged on `master`. Never delete or rewrite `master`'s history.

## When to commit

- Commit on the task branch after each logical, compiling unit of work (a fix, a feature step, a config change), not at the very end and not mid-edit.
- Never commit code that does not compile. Do not commit build output, jars, or `.gradle/`.
- Commit only files you changed for the task; check `git status` and `git diff --stat` first. Do not `git add -A` over unrelated changes.
- Do not commit unless the work is finished or at a stable checkpoint, and never amend or force-rewrite commits already merged to `master`.

## How to commit

- Imperative, specific subject under ~72 chars saying what changed and why it matters, e.g. `Skip meshing fully enclosed blocks`. Add a body only when the reason is not obvious.
- The merge commit on `master` ends with the released version in parentheses, e.g. `Merge claude/fix-lag (0.1.1)`.
- End every commit message with: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`

## Versioning

- Version lives in `gradle.properties:mod_version`. Bump it by hand before merging; **never reuse a released version**. Patch for fixes, minor for features, major for breaking changes.
- Stays on upstream Embeddium's 0.3.x line (addons declare `embeddium` version ranges); bump the patch only, never go below 0.3.31.
- After the final build on `master`: `git tag -a v<ver> -m "Emberbium <ver>"`.
- If a jar replaces the one in the modpack (`Groid Pack OG/mods`), move the old jar to `mods-old-jars/` rather than deleting it.
