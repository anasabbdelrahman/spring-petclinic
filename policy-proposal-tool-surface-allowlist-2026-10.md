# Policy proposal: Engineering tool-surface allowlist (2026-10)

**Status: draft proposal, not adopted policy.** Prepared on 2026-10-02 for the virtual Engineering organisation in `governance-posture-2026-10.md`, a homework scenario that is not a real employer.

- **Proposed owner:** Head of Platform Engineering
- **Required co-approver:** Security
- **Consulted:** Backend Engineering Manager
- **Proposed review date:** 2027-01-15

## Gap

No org-wide rule says which MCP servers and plugins Engineering may use with Claude Code, and nothing enforces one. Each team decides in its own repository configuration. One team blocks all MCP tools in CI and has no rule for interactive sessions.

## Proposed policy

1. **Scope:** every MCP server and Claude Code plugin Engineering uses:
   - in interactive sessions;
   - in CI jobs;
   - in unattended agents, meaning scheduled or batch jobs where no person approves each action.
2. **Default deny:** only tools on the Engineering tool allowlist may be used. Platform Engineering maintains it, and every change needs approval from both Platform Engineering and Security.
3. **Registration:** each entry records:
   - **Owner:** the responsible team.
   - **Purpose:** the engineering task it serves.
   - **Permitted data classes,** using these interim classes until an org scheme replaces them:
     - *Public:* published material.
     - *Internal:* source code and internal engineering documentation.
     - *Restricted:* customer or personal data, only when explicitly approved for that specific tool.
   - **Network destinations:** every host it connects to.
   - **Credential method:** how the tool authenticates, and who owns that credential.
   - **Version:** the exact approved version or commit.
   - **Review date:** at most 12 months after approval.
4. **No secrets as input:** credentials, tokens, passwords, private keys and secret values are prohibited as tool inputs. Approved runtime secret injection may be used only to authenticate the tool itself.
5. **CI and unattended agents** may use only allowlisted tools named in that job's configuration.
6. **Exceptions** need Security approval, a written justification and an expiry date at most 90 days away. Platform Engineering records them.
7. **Transition:** tools already in use may run for 30 days after adoption while they are registered.

## Enforcement versus delivery

- **Organisation-managed engineering devices:** centrally managed settings install the allowlist. Team settings, user settings and command-line flags cannot loosen it. **This is enforcement.**
- **Unmanaged devices:** server-managed settings may deliver the allowlist to unmanaged devices, but users on those devices can bypass it. Therefore, unmanaged devices remain outside the enforced boundary until they are organisation-managed. **This is delivery, not enforcement.**
- **CI and headless runners:** job configuration names the approved tools and blocks all others.
- **Repository review:** adding or changing a tool in repository configuration needs review by the repository's owners, with branch protection on the default branch.
- **Exceptions:** point 6 is the only route to a tool that is not on the allowlist.

## Rationale

- **Nothing decides this today.** The posture map records this area as `non-existent`, enforced by `nothing`.
- **Restrictions are improvised.** In the sandbox, the CI review job allows only Bash and no MCP tools, and the headless reviewer has no tools. Each was written separately. Nothing covers interactive sessions.
- **The risk is concrete.** The CI job holds a credential, has `pull-requests: write` and reads untrusted pull-request text (`task-risk-2026-09.md`, task 4). Each added tool widens what injected text can do.
- **The cost is low.** The inventory is small, and this area can be enforced on managed devices immediately.

## Owner review

Reread as the Head of Platform Engineering on 2026-10-02. Wording that needed explaining was removed:

- "Unattended agent" and the interim data classes are now defined.
- Secrets belong to no data class. They only authenticate a tool and are never its input.
- The unmanaged-device statement says exactly where enforcement ends.
- Exceptions expire, and the transition period stops default-deny from pushing teams to route around it.

**This proposal is not adopted.** It is a draft for owner review.
