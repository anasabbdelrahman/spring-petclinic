#!/usr/bin/env bash
# PreToolUse hook for Bash: refuses destructive Git forms that prefix
# permission rules cannot match reliably, because the dangerous flag can sit
# anywhere in the command (for example `git push origin main --force`).
#
# Input:  the hook JSON payload on stdin; only .tool_input.command is read.
# Output: exit 0 to let the other controls decide; exit 2 with a one-line
#         reason on stderr to block the tool call.
#
# Blocks: git push with --force, -f (alone or bundled), --force-with-lease,
#         --force-if-includes, a +refspec, or --mirror; git reset --hard;
#         git clean with --force or -f (alone or bundled, e.g. -fd), or with
#         clean.requireForce overridden on the command line.
#
# This is not a sandbox. It runs with the user's privileges and protects
# this machine only; remote branch protection is the shared boundary.
# Bash 3.2 compatible (the macOS system bash).

set -u
set -f

refuse() {
    printf 'guard-git: refused %s. Destructive Git operations need a human.\n' "$1" >&2
    exit 2
}

command -v jq >/dev/null 2>&1 || refuse 'the command: jq is required to inspect it'

payload=$(cat)
cmd=$(printf '%s' "$payload" | jq -r '.tool_input.command // empty' 2>/dev/null) ||
    refuse 'the command: unreadable hook payload'
[ -n "$cmd" ] || exit 0

lower() {
    printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

# clean.requireForce=false makes `git clean` delete without -f.
requireforce_off=0
note_config() {
    case "$(lower "$1")" in
    clean.requireforce=true | clean.requireforce) ;;
    clean.requireforce=*) requireforce_off=1 ;;
    esac
}

check_push() {
    local j=$1 tok opts=1
    while [ "$j" -lt "${#words[@]}" ]; do
        tok=${words[$j]}
        if [ "$opts" -eq 1 ]; then
            case "$tok" in
            --) opts=0 ;;
            --force*) refuse "force push ($tok)" ;;
            --m | --mi | --mir | --mirr | --mirro | --mirror) refuse 'mirror push (--mirror)' ;;
            --*) ;;
            -*f*) refuse "force push ($tok)" ;;
            esac
        fi
        case "$tok" in
        +?*) refuse "force push (refspec $tok)" ;;
        esac
        j=$((j + 1))
    done
}

check_reset() {
    local j=$1 tok
    while [ "$j" -lt "${#words[@]}" ]; do
        tok=${words[$j]}
        case "$tok" in
        --) return 0 ;;
        --h | --ha | --har | --hard) refuse 'hard reset (--hard)' ;;
        esac
        j=$((j + 1))
    done
}

check_clean() {
    local j=$1 tok
    [ "$requireforce_off" -eq 0 ] || refuse 'forced clean (clean.requireForce overridden)'
    while [ "$j" -lt "${#words[@]}" ]; do
        tok=${words[$j]}
        case "$tok" in
        --) return 0 ;;
        --f*) refuse "forced clean ($tok)" ;;
        --*) ;;
        -*f*) refuse "forced clean ($tok)" ;;
        esac
        j=$((j + 1))
    done
}

# Parse one git invocation starting at index $1 (the `git` word).
inspect_git() {
    local i=$(($1 + 1)) tok sub=''
    requireforce_off=0
    while [ "$i" -lt "${#words[@]}" ]; do
        tok=${words[$i]}
        case "$tok" in
        -c)
            note_config "${words[$((i + 1))]:-}"
            i=$((i + 2))
            ;;
        --config-env)
            note_config "${words[$((i + 1))]:-}"
            i=$((i + 2))
            ;;
        --config-env=*)
            note_config "${tok#--config-env=}"
            i=$((i + 1))
            ;;
        -C | --git-dir | --work-tree | --namespace | --super-prefix) i=$((i + 2)) ;;
        -*) i=$((i + 1)) ;;
        *)
            sub=$tok
            break
            ;;
        esac
    done
    case "$sub" in
    push) check_push $((i + 1)) ;;
    reset) check_reset $((i + 1)) ;;
    clean) check_clean $((i + 1)) ;;
    esac
}

# Join backslash-newline continuations, split on shell separators and
# grouping characters, and drop quoting so quoted words are still seen.
cmd=${cmd//$'\\\n'/ }
segments=$(printf '%s\n' "$cmd" | awk '{ gsub(/[;&|(){}`]/, "\n"); print }' | tr -d "\"'\\\\")

while IFS= read -r seg; do
    read -ra words <<<"$seg"
    k=0
    while [ "$k" -lt "${#words[@]}" ]; do
        case "${words[$k]##*/}" in
        git) inspect_git "$k" ;;
        esac
        k=$((k + 1))
    done
done <<<"$segments"

exit 0
