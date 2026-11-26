---
name: git-commit-message
description: Generate concise, accurate English Git commit messages from actual code changes, usually in Conventional Commits format. Use when asked to draft or suggest a commit message; do not stage, commit, amend, or push unless explicitly requested.
---

# Git Commit Message

Produce one ready-to-use English commit message grounded in the actual diff. Generating a message does not authorize staging, committing, amending, or pushing.

## Determine the change set

Use the scope requested by the user: staged changes, unstaged changes, a commit range, a PR, or specific paths.

When no scope is specified:

1. Check `git status --short`.
2. Prefer staged changes when any exist because they are the likely commit payload.
3. Otherwise use the current working-tree diff.
4. Read the applicable `AGENTS.md` and recent commit history.

Do not include unrelated dirty files merely because they exist. If the selected changes contain several independent purposes, say they should be split and generate messages for the coherent subsets instead of hiding them under one vague subject.

## Match repository conventions

Inspect recent commit subjects first. Match a clear existing convention. If the repository has no strong convention, default to Conventional Commits:

- `feat`: new user-visible capability
- `fix`: corrected behavior
- `refactor`: internal restructuring without intended behavior change
- `perf`: measurable performance improvement
- `test`: test-only changes
- `docs`: documentation-only changes
- `build`: build system or dependencies
- `ci`: CI configuration
- `chore`: maintenance without product behavior
- `style`: formatting only

Use a short lowercase scope when it improves precision, such as `feat(profile): ...` or `fix(douyin): ...`.

## Write the message

The subject must:

- describe the primary result, not the files touched;
- use imperative mood: `add`, `fix`, `remove`, not `added` or `adds`;
- avoid a trailing period;
- stay concise, preferably within 72 characters;
- distinguish a feature from a bug fix or refactor accurately.

Add a body only when it explains why the change was needed, user or system impact, migration requirements, or a non-obvious tradeoff. Separate it from the subject with a blank line and wrap prose at roughly 72 characters.

Use `!` after the type or scope and a `BREAKING CHANGE:` footer only for a real compatibility break.

Do not:

- invent issue numbers, tests, or motivation;
- claim tests passed unless they were run;
- describe formatting churn as a refactor;
- call a behavior change a refactor;
- mention that AI generated the message;
- add generic phrases such as `various improvements`.

## Output

By default, return only the recommended message in a `text` code block:

```text
feat(profile): unify drafts with published works
```

Example with a body:

```text
fix(douyin): preserve background audio for slides

Read slides audio from video.play_addr when music.play_url is absent,
so imported image posts retain their original soundtrack.
```

If the user asks for alternatives, provide two or three materially different options rather than punctuation variants.

If the change set is mixed or the intended commit scope is ambiguous, explain the split briefly and still provide the best message for each coherent subset.

If the user explicitly asks to create the commit as well, use the generated message only after following repository rules and receiving any required authorization. Do not amend or push unless separately requested.
