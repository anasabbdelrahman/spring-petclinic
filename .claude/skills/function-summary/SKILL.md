---
name: function-summary
description: Summarize a Java method before changing it. Use when reviewing unfamiliar code or assessing the risk of a modification.
argument-hint: "<path-to-file>:<method-name>"
---

Read the Java method identified by `$ARGUMENTS`. Locate the exact method in the supplied file and summarize it in exactly four sections:

1. **Purpose** - what problem the method solves in one sentence.
2. **Inputs and outputs** - parameters, return value, and declared or possible exceptions.
3. **Side effects** - repository calls, model changes, I/O, redirects, or other mutations. Write "none" when there are none.
4. **Failure risk** - what user-visible or system behavior could fail if the method is changed incorrectly.

Ground every statement in the actual method and nearby code. If the method cannot be found unambiguously, report that and list the matching candidates instead of guessing.
