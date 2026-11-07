# Branch and tag protection

Repository rulesets (Settings → Rules → Rulesets). All are active, and none lets the default `GITHUB_TOKEN` write to
a protected ref.

| Ruleset | Targets | Rules | Who can bypass |
|---|---|---|---|
| `protect-main` | default branch | No deletion, no force-push, PR required (threads resolved), required checks: Build/test/lint, gitleaks, CodeQL analysis; code-scanning gate (no new High+ alerts) | Nobody |
| `protect-data-branches` | `market`, `streams`, `extensions` | No deletion, no creation, no direct updates | The `data-branches-bot` deploy key only (used by the three sync workflows) |
| `protect-other-branches` | every other branch except `dependabot/**` | No deletion, no force-push | Repository admin (lets the maintainer clean up merged branches) |
| `protect-release-tags` | `v*` tags | No deletion, no update | Repository admin |

## Why a deploy key
On a personal-account repository, GitHub doesn't accept the GitHub Actions app as a ruleset bypass actor. The usual
pattern is a dedicated deploy key:
- The public key is a repository deploy key with write access.
- The private key exists only in the secret `DATA_BRANCH_DEPLOY_KEY`. It was generated in a throwaway directory,
  stored, and shredded, so no copy exists anywhere else.
- Workflows push over SSH with GitHub's pinned host key. The job's `GITHUB_TOKEN` stays `contents: read`.

**Rotation:** generate a new ed25519 key, replace the deploy key and the secret, and delete the old deploy key. Nothing
else changes. The key is replaceable, so it doesn't need to be backed up.
