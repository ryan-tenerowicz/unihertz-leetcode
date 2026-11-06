"""Type-aware completion for the Code pane (PLAN §5.4, rev 38), with Jedi.

Runs in the app's `:complete` process (CompletionService), apart from the runner, so a long
run never delays suggestions. Jedi reads the solution as it is typed, with LeetCode's
pre-imports in front (titan_prelude), and infers types from hints, assignments and the
standard library's stubs: `nums: List[int]` then `nums.` offers `append`, `pop`, `sort`…

request: {"code", "line": 1-based, "col": 0-based, "limit"}
result:  {"items": [{"name", "type", "args"}], "ms", "error"}
         "args" (functions and classes, rev 39): how many arguments the call takes (0 = none), so
         the app can put the cursor after `()` or inside it; left out if Jedi took too long to say.
"""

import json
import os
import time

from titan_prelude import PRELUDE

_jedi = None
_env = None
# Jedi takes a name from the first `import *` that has it, so `Counter` came out as typing's
# alias; at run time `from collections import *` comes later and wins. Say so explicitly (rev 40).
_PRELUDE = PRELUDE + "from collections import Counter, OrderedDict, ChainMap, defaultdict, deque\n"
_OFFSET = _PRELUDE.count("\n")
_SIGNATURE_BUDGET_S = 0.15  # looking up a library's signatures the first time can take a second


def init(cache_dir):
    """Load Jedi and warm its caches (builtins, typing, collections) so the first `.` is quick."""
    global _jedi, _env
    os.environ["XDG_CACHE_HOME"] = cache_dir
    import jedi
    jedi.settings.cache_directory = os.path.join(cache_dir, "jedi")
    jedi.settings.case_insensitive_completion = False
    jedi.settings.add_bracket_after_function = False
    _jedi = jedi
    _env = jedi.InterpreterEnvironment()
    t = time.time()
    complete(json.dumps({"code": "x: List[int] = []\nd = defaultdict(list)\nx.", "line": 3, "col": 2}))
    complete(json.dumps({"code": "d = defaultdict(list)\nd.", "line": 2, "col": 2}))
    return int((time.time() - t) * 1000)


def _rank(c, prefix, user_line):
    """Names defined in the solution first, then public before private, then shorter and A–Z."""
    line = c.line if c.module_path is None else None
    own = 0 if (line is not None and line > _OFFSET and line <= user_line) else 1
    return (own, c.name.startswith("_"), len(c.name) if prefix else 0, c.name.lower())


def _resolved(c):
    """A name bound to a function or class (not a typing alias like `List`): its real kind."""
    try:
        kinds = {(d.type, d.module_name == "typing") for d in c.infer()}
    except Exception:
        return None
    if len(kinds) == 1:
        kind, typing_alias = next(iter(kinds))
        if kind in ("function", "class") and not typing_alias:
            return kind
    return None


def _args(c):
    """Fewest parameters over the call's signatures (`self` is already left out), or None."""
    try:
        sigs = c.get_signatures()
        return min(len(s.params) for s in sigs) if sigs else None
    except Exception:
        return None


def complete(request):
    if _jedi is None:
        return json.dumps({"items": [], "error": "not ready"})
    t = time.time()
    r = json.loads(request)
    code = _PRELUDE + r["code"]
    line = int(r["line"]) + _OFFSET
    col = int(r["col"])
    limit = int(r.get("limit", 60))
    try:
        script = _jedi.Script(code, environment=_env)
        found = script.complete(line, col)
    except Exception as e:  # Jedi can fail on half-typed code; no suggestions then.
        return json.dumps({"items": [], "error": repr(e)[:300], "ms": int((time.time() - t) * 1000)})
    seen = set()
    picked = []
    for c in found:
        prefix = c.name[: len(c.name) - len(c.complete or "")] if c.complete is not None else ""
        if c.name in seen or not c.complete:
            continue  # duplicates, and the word already typed in full
        if c.name.startswith("__") and not prefix.startswith("__"):
            continue  # dunders only when asked for
        if c.name.startswith("_") and not prefix.startswith("_") and c.type != "param":
            continue
        seen.add(c.name)
        picked.append((c, prefix))
    picked.sort(key=lambda cp: _rank(cp[0], cp[1], line))
    items = []
    deadline = time.time() + _SIGNATURE_BUDGET_S
    for c, _ in picked[:limit]:
        kind = c.type
        if kind == "statement" and time.time() < deadline:
            kind = _resolved(c) or kind  # `insort = insort_right` is a function
        item = {"name": c.name, "type": kind}
        if kind in ("function", "class") and time.time() < deadline:
            args = _args(c)
            if args is not None:
                item["args"] = args
        items.append(item)
    return json.dumps({"items": items, "ms": int((time.time() - t) * 1000)})
