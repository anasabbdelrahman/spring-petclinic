#!/usr/bin/env bash
# PreToolUse hook for the Bash tool: block any force-push whose destination is main.
#
# Reads the tool-call JSON on stdin. Exit 2 with a reason on stderr blocks the
# call; exit 0 allows it. Force is detected from --force, --force-with-lease[=..],
# a short-flag cluster containing f (-f, -fu, -uf), a +refspec, or --mirror, in
# any position. The destination is main for main, refs/heads/main, <src>:main,
# <src>:refs/heads/main, HEAD/@ while on main, or no refspec / --all while the
# current branch is main or cannot be determined.
#
# This is string-level matching of Claude's Bash tool calls. It does not see
# commands hidden behind eval, variables, Git aliases or script files, and it is
# not a substitute for server-side branch protection.

block() {
  echo "BLOCKED by block-force-push-main: force-push to 'main' is not permitted. $1" >&2
  exit 2
}

input=$(cat)

if ! command -v jq >/dev/null 2>&1; then
  case "$input" in *push*) block "jq is not available to inspect the command (fail closed)." ;; esac
  exit 0
fi

if ! cmd=$(printf '%s' "$input" | jq -er '.tool_input.command // ""' 2>/dev/null); then
  case "$input" in *push*) block "The tool-call JSON could not be parsed (fail closed)." ;; esac
  exit 0
fi
cwd=$(printf '%s' "$input" | jq -r '.cwd // ""' 2>/dev/null)
[ -n "$cwd" ] || cwd=$PWD

case "$cmd" in *push*) ;; *) exit 0 ;; esac

current_branch() {
  local dir=$1
  case "$dir" in /*) ;; *) dir="$cwd/$dir" ;; esac
  git -C "$dir" symbolic-ref --short -q HEAD 2>/dev/null
}

# Normalize: redirections like 2>&1 / &> keep their '>' only, quotes and
# backslashes become spaces so nested "bash -c '...'" commands are visible,
# and shell separators become segment breaks.
norm=${cmd//>&/>}
norm=${norm//&>/>}
norm=${norm//[\"\'\\]/ }
nl=$'\n'
norm=${norm//[;|&()\`]/$nl}

check_push() {
  # $1 = directory git runs in; remaining args = tokens after "push"
  local gdir=$1; shift
  local force=0 all=0 repo_set=0 skip_next=0 end_opts=0 t
  local positional=()
  for t in "$@"; do
    if [ "$skip_next" = 1 ]; then skip_next=0; continue; fi
    case "$t" in
      *'<'* | *'>'*)
        case "$t" in *'<' | *'>') skip_next=1 ;; esac
        continue ;;
    esac
    if [ "$end_opts" = 0 ]; then
      case "$t" in
        --) end_opts=1; continue ;;
        --force | --force-with-lease | --force-with-lease=*) force=1; continue ;;
        --mirror) force=1; all=1; continue ;;
        --all | --branches) all=1; continue ;;
        --repo=*) repo_set=1; continue ;;
        --repo) repo_set=1; skip_next=1; continue ;;
        --push-option | --receive-pack | --exec) skip_next=1; continue ;;
        --*) continue ;;
        -?*)
          case "$t" in *f*) force=1 ;; esac
          case "$t" in *o) skip_next=1 ;; esac
          continue ;;
      esac
    fi
    positional+=("$t")
  done

  local refspecs=()
  if [ "$repo_set" = 1 ]; then
    refspecs=("${positional[@]}")
  elif [ "${#positional[@]}" -gt 1 ]; then
    refspecs=("${positional[@]:1}")
  fi

  local r dst branch main_target=0
  if [ "${#refspecs[@]}" -gt 0 ]; then
    for r in "${refspecs[@]}"; do
      case "$r" in +*) force=1; r=${r#+} ;; esac
      case "$r" in
        *:*) dst=${r#*:} ;;
        HEAD | @) dst=$(current_branch "$gdir") ;;
        *) dst=$r ;;
      esac
      dst=${dst#refs/heads/}
      [ "$dst" = main ] && main_target=1
    done
  fi

  if [ "${#refspecs[@]}" -eq 0 ] || [ "$all" = 1 ]; then
    branch=$(current_branch "$gdir")
    if [ "$all" = 1 ] || [ -z "$branch" ] || [ "$branch" = main ]; then
      main_target=1
    fi
  fi

  [ "$force" = 1 ] && [ "$main_target" = 1 ]
}

while IFS= read -r seg; do
  read -ra toks <<<"$seg"
  n=${#toks[@]}
  i=0
  while [ "$i" -lt "$n" ]; do
    t=${toks[$i]}
    i=$((i + 1))
    case "$t" in git | */git) ;; *) continue ;; esac
    gdir=.
    # Skip git's global options to find the subcommand.
    while [ "$i" -lt "$n" ]; do
      case "${toks[$i]}" in
        -C) gdir=${toks[$((i + 1))]}; i=$((i + 2)) ;;
        -c | --git-dir | --work-tree | --namespace) i=$((i + 2)) ;;
        -*) i=$((i + 1)) ;;
        *) break ;;
      esac
    done
    [ "$i" -lt "$n" ] && [ "${toks[$i]}" = push ] || continue
    if check_push "$gdir" "${toks[@]:$((i + 1))}"; then
      block "Matched: $(printf '%s' "$seg" | tr -s ' \t' ' ' | sed 's/^ //; s/ $//'). Push a feature branch without force, or ask a human."
    fi
  done
done <<<"$norm"

exit 0
