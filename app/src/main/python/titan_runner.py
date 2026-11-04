"""Titan Code local runner harness (PLAN §7).

Runs a LeetCode-style Python solution against test inputs, offline, inside the app's `:runner`
process. Called from Kotlin with one JSON request and returns one JSON result:

request: {"code", "metaData", "inputs": [str], "expected": [str|None],
          "mode": "exact"|"any_order"|"k_first"|"stdin"|"stress",
          "number": LeetCode problem number (0 if none): drivers for special types (titan_drivers) and checkers (titan_checkers),
          "caseLimitMs", "memLimitMb": per-case limits (titan_limits), "progressPath": results so far, written after each case,
          stress only: "references": [code], "constraints": statement text, "seconds"}
result:  {"status": "ok"|"compile_error"|"unsupported", "error", "python", "restart": true if the process should be replaced,
          "cases": [{"output", "ok", "stdout", "error", "ms", "note", "unchecked", "limit": "time"|"memory"|"output"}],
          stress only: "stress": {...} (titan_stress)}

The process may be killed at any time for a timeout; nothing here needs cleaning up.
"""

import io
import json
import linecache
import math
import sys
import time
import traceback

import titan_checkers as checkers
import titan_drivers as drivers
import titan_limits as limits

# LeetCode's pre-imported names, so solutions that rely on them work unchanged. To be confirmed
# against LeetCode with a probe solution printing sorted(globals()) (PLAN §7).
PRELUDE = """
from typing import *
import collections, heapq, bisect, math, itertools, functools, string, re, random, operator, copy, statistics
from collections import *
from heapq import *
from bisect import *
from itertools import *
from functools import *
from math import *
from string import *
from operator import *
from sortedcontainers import SortedList, SortedDict, SortedSet

class ListNode:
    def __init__(self, val=0, next=None):
        self.val = val
        self.next = next

class TreeNode:
    def __init__(self, val=0, left=None, right=None):
        self.val = val
        self.left = left
        self.right = right
"""

USER_FILE = "solution.py"
CASE_LIMIT_MS = 3000   # per case, on the phone (rev 24); the request can change it
MEM_LIMIT_MB = 512     # growth of the runner process during one case
SUPPORTED_SCALARS = ("integer", "long", "double", "boolean", "string", "character")


def run(request_json):
    req = json.loads(request_json)
    meta = json.loads(req["metaData"]) if req.get("metaData") else {}
    result = {"python": sys.version.split()[0], "cases": []}
    watch = limits.Watch((req.get("caseLimitMs") or CASE_LIMIT_MS) / 1000.0, req.get("memLimitMb", MEM_LIMIT_MB))
    box = {}

    def work():
        try:
            box["result"] = _run(req, meta, result, watch)
        except BaseException as e:  # never let the thread die silently
            result["status"] = "internal_error"
            result["error"] = "".join(traceback.format_exception(type(e), e, e.__traceback__))
            box["result"] = result

    # Deep recursion (DFS on 10^5 nodes) needs a big C stack and a high recursion limit, as on LeetCode.
    if not limits.run_watched(work, watch):
        # A case ignored every signal (stuck in a call that never returns to Python, such as
        # time.sleep). Report what we have; the app restarts the process for the next run.
        _restore_streams()
        result["restart"] = True
        result.setdefault("status", "ok")
        cases = result["cases"]
        if cases:
            c = cases[-1]
            c["error"] = ("Time Limit Exceeded: %s, inside a call that can't be interrupted "
                          "(time.sleep, a huge built-in operation…)" % watch.message("time"))
            c["limit"] = "time"
            c["ms"] = round(watch.elapsed_s() * 1000, 2)
        stuck = len(cases)
        for j in range(len(cases), len(req.get("inputs") or [])):
            cases.append({"output": None, "ok": None, "stdout": "", "error": "Not run: case %d had to be stopped." % stuck, "ms": 0.0})
        return json.dumps(result, ensure_ascii=False, default=str)
    return json.dumps(box["result"], ensure_ascii=False)


_REAL_STREAMS = (sys.stdin, sys.stdout)


def _restore_streams():
    sys.stdin, sys.stdout = _REAL_STREAMS


def _compile_user(code):
    """Compile the solution, registering its source so tracebacks can show each line."""
    lines = code.splitlines(True)
    linecache.cache[USER_FILE] = (len(code), None, lines, USER_FILE)
    return compile(code, USER_FILE, "exec")


def _cases(req, result, watch, body, keep=20000):
    """Run body(i, raw, case) for every input, under the limits, one result per case.

    Each case gets its own capped stdout. A limit stops the case with the line it was on; any
    other exception is a runtime error. The results so far go to the progress file after every
    case, so a run the app has to kill still shows the cases that finished.
    """
    progress = req.get("progressPath")
    for i, raw in enumerate(req["inputs"]):
        case = {"output": None, "ok": None, "stdout": "", "error": None, "ms": 0.0}
        result["cases"].append(case)
        out = limits.CappedOutput(keep=keep)
        try:
            try:
                sys.stdout = out
                watch.begin()
                start = time.perf_counter()
                try:
                    body(i, raw, case)
                finally:
                    watch.end()
                    case["ms"] = round((time.perf_counter() - start) * 1000, 2)
                    _restore_streams()
            except limits.LIMITS as e:
                _limit(case, e, watch)
            except BaseException as e:
                case["error"] = _user_tb(e)
        except limits.LIMITS as e:  # a stop signal that landed just after the case ended
            _restore_streams()
            if case["error"] is None and case["output"] is None:
                _limit(case, e, watch)
        case["stdout"] = out.getvalue()
        case["output"] = limits.shorten(case["output"])
        if progress:
            try:
                with open(progress, "w") as f:
                    json.dump(result, f, ensure_ascii=False)
            except Exception:
                pass
    return result


def _limit(case, e, watch):
    kind = e.kind
    detail = (e.args[0] if e.args else "") or watch.message(kind)
    label = {"time": "Time Limit Exceeded", "memory": "Memory Limit Exceeded", "output": "Output Limit Exceeded"}[kind]
    case["limit"] = kind
    case["ok"] = False
    case["error"] = _user_tb(e, "%s: %s" % (label, detail))


def _run(req, meta, result, watch):
    sys.setrecursionlimit(200000)
    if req.get("mode") == "stdin":
        return _run_stdin(req, result, watch)
    ns = {"__name__": "__main__"}
    exec(PRELUDE, ns)
    number = req.get("number") or 0
    driver, provided = drivers.DRIVERS.get(number, (None, ""))
    exec(provided or drivers.DESIGN_CLASSES.get(number, ""), ns)
    ListNode, TreeNode = ns["ListNode"], ns["TreeNode"]
    try:
        exec(_compile_user(req["code"]), ns)
    except BaseException as e:
        result["status"] = "compile_error"
        result["error"] = _user_tb(e)
        return result

    design = driver is None and "classname" in meta
    if driver is None and not design:
        params = meta.get("params", [])
        bad = [p["type"] for p in params if not _supported(p["type"])]
        if bad or "Solution" not in ns:
            result["status"] = "unsupported"
            result["error"] = ("Input type %s isn't supported by the local runner yet. Use sym+E to run it on the site."
                               % ", ".join(bad)) if bad else "No class Solution found."
            return result
    if req.get("mode") == "stress":
        if driver is not None or design:
            result["status"] = "unsupported"
            result["error"] = ("Stress tests don't cover design problems yet." if design else
                               "Stress tests don't cover problems with special types (graphs, cycles, APIs…) yet.")
            return result
        import titan_stress
        return titan_stress.run(req, meta, result, watch, ns, sys.modules[__name__])
    result["status"] = "ok"
    ret_type = (meta.get("return") or {}).get("type", "")
    mode = _mode(req, number)
    expected = req.get("expected") or []
    checker = checkers.CHECKERS.get(number)

    def body(i, raw, case):
        lines = raw.split("\n")
        exp = expected[i] if i < len(expected) else None
        if driver is not None:
            o = driver(ns, lines, meta)
            case["output"] = o.rendered
            case["note"] = o.note
            if o.ok is False:
                case["ok"] = False
            elif not o.check:
                case["ok"] = o.ok
                case["unchecked"] = True
            elif exp is not None:
                case["ok"] = any(_matches(r, exp, mode, ret_type) for r in (o.rendered,) + o.alts)
            return
        if design:
            case["output"] = _dump(_run_design(ns, meta, lines))
            if number in drivers.RANDOM_OUTPUT:
                case["unchecked"] = True
                case["note"] = "Random answers can't be compared with the example."
            elif exp is not None:
                case["ok"] = _matches(case["output"], exp, mode, ret_type)
            return
        rendered, inputs = call_solution(ns, meta, lines, mode)
        case["output"] = rendered
        if checker is not None:
            ok, note = checkers.check(checker, inputs, rendered, exp)
            case["note"] = note
            if ok is not None:
                case["ok"] = ok
                return
        if exp is not None:
            case["ok"] = _matches(rendered, exp, mode, ret_type)

    return _cases(req, result, watch, body)


def _mode(req, number):
    """The checking mode; some problems accept any order without saying so (titan_checkers)."""
    mode = req.get("checkMode") or req.get("mode", "exact")  # stress tests send the checking mode separately
    if mode == "stress":
        mode = "exact"
    return "any_order" if mode == "exact" and number in checkers.ANY_ORDER else mode


def call_solution(ns, meta, lines, mode):
    """Call Solution().<method> on one test's lines. Returns (rendered output, the inputs as parsed JSON)."""
    params = meta["params"]
    if len(lines) != len(params):
        raise ValueError("this test has %d lines but %s takes %d arguments" % (len(lines), meta["name"], len(params)))
    inputs = [json.loads(line) for line in lines]
    args = [_parse(line, p["type"], ns["ListNode"], ns["TreeNode"]) for line, p in zip(lines, params)]
    fn = getattr(ns["Solution"](), meta["name"])
    value = fn(*args)
    ret_type = (meta.get("return") or {}).get("type", "")
    if ret_type == "void":
        return _dump(_out(args[0], params[0]["type"])), inputs
    if mode == "k_first":
        return json.dumps([value, args[0][:value]], separators=(",", ":")), inputs
    return _dump(_out(value, ret_type)), inputs


def _run_stdin(req, result, watch):
    """Codeforces-style programs (rev 13): each case runs the whole program with the input on stdin.

    The output is compared token by token (Codeforces' usual checker ignores whitespace); numbers
    with a decimal point match within 1e-6. Problems with a special checker may accept other
    answers, which the Results pane says.
    """
    try:
        code = _compile_user(req["code"])
    except BaseException as e:
        result["status"] = "compile_error"
        result["error"] = _user_tb(e)
        return result
    result["status"] = "ok"
    expected = req.get("expected") or []
    real_open = open

    def body(i, raw, case):
        data = raw if raw.endswith("\n") else raw + "\n"
        stdin = io.TextIOWrapper(io.BytesIO(data.encode()), encoding="utf-8")
        sys.stdin = stdin

        def fake_open(file, *a, **k):
            # open(0) is a common fast-input idiom.
            if file == 0:
                return stdin
            return real_open(file, *a, **k)

        ns = {"__name__": "__main__", "open": fake_open}
        try:
            exec(code, ns)
        except SystemExit:
            pass
        finally:
            case["output"] = sys.stdout.getvalue()
        exp = expected[i] if i < len(expected) else None
        if exp is not None:
            case["ok"] = _tokens_match(case["output"], exp)

    _cases(req, result, watch, body, keep=limits.CappedOutput().limit)
    # The program's output *is* its stdout; don't show it twice.
    for c in result["cases"]:
        c["stdout"] = ""
    return result


def _tokens_match(got, want):
    a, b = got.split(), want.split()
    if len(a) != len(b):
        return False
    for x, y in zip(a, b):
        if x == y:
            continue
        if "." in x or "." in y:
            try:
                if math.isclose(float(x), float(y), rel_tol=1e-6, abs_tol=1e-6):
                    continue
            except ValueError:
                pass
        if x.lower() == y.lower() and x.lower() in ("yes", "no"):
            continue
        return False
    return True


def _supported(t):
    base = t.replace("[]", "").replace("list<", "").replace(">", "")
    return base in SUPPORTED_SCALARS or base in ("ListNode", "TreeNode")


def _parse(line, t, ListNode, TreeNode):
    v = json.loads(line)
    if t == "ListNode":
        head = cur = ListNode()
        for x in v:
            cur.next = ListNode(x)
            cur = cur.next
        return head.next
    if t == "TreeNode":
        return _tree(v, TreeNode)
    if t == "ListNode[]":
        return [_parse(json.dumps(x), "ListNode", ListNode, TreeNode) for x in v]
    return v


def _tree(values, TreeNode):
    if not values or values[0] is None:
        return None
    root = TreeNode(values[0])
    queue = [root]
    i = 1
    qi = 0
    while i < len(values) and qi < len(queue):
        node = queue[qi]
        qi += 1
        if i < len(values) and values[i] is not None:
            node.left = TreeNode(values[i])
            queue.append(node.left)
        i += 1
        if i < len(values) and values[i] is not None:
            node.right = TreeNode(values[i])
            queue.append(node.right)
        i += 1
    return root


def _out(v, t):
    """Turn ListNode/TreeNode results back into LeetCode's list notation."""
    if v is None:
        return None
    if hasattr(v, "next") and hasattr(v, "val") and not hasattr(v, "left"):
        out, seen = [], 0
        while v is not None and seen < 100000:
            out.append(v.val)
            v = v.next
            seen += 1
        return out
    if hasattr(v, "left") and hasattr(v, "right") and hasattr(v, "val"):
        out, queue = [], [v]
        while queue:
            n = queue.pop(0)
            if n is None:
                out.append(None)
                continue
            out.append(n.val)
            queue.append(n.left)
            queue.append(n.right)
        while out and out[-1] is None:
            out.pop()
        return out
    if isinstance(v, (list, tuple)) and v and all(hasattr(x, "next") for x in v if x is not None):
        return [_out(x, "ListNode") for x in v]
    if t == "double" and isinstance(v, (int, float)) and not isinstance(v, bool):
        return float(v)
    return v


def _dump(v):
    if isinstance(v, float):
        return "%.5f" % v
    if isinstance(v, (set, frozenset)):
        v = list(v)
    if isinstance(v, tuple):
        v = list(v)
    return json.dumps(v, separators=(",", ":"), ensure_ascii=False, default=_fallback)


def _fallback(o):
    if isinstance(o, (set, frozenset, tuple)):
        return list(o)
    return str(o)


def _run_design(ns, meta, lines):
    ops = json.loads(lines[0])
    args = json.loads(lines[1])
    cls = ns.get(meta["classname"])
    if cls is None:
        raise NameError("class %s is not defined" % meta["classname"])
    if len(ops) != len(args):
        raise ValueError("%d operations but %d argument lists" % (len(ops), len(args)))
    # TreeNode/ListNode/Iterator arguments (BSTIterator, Linked List Random Node, PeekingIterator)
    # by metaData's declared types, or the method's annotations when there's no metaData (NeetCode).
    ctor = [p["type"] for p in (meta.get("constructor") or {}).get("params", [])] or drivers.hinted_types(cls.__init__)
    obj = cls(*drivers.convert(args[0], ctor, ns))
    declared = {m.get("name"): [p["type"] for p in m.get("params", [])] for m in meta.get("methods", [])}
    out = [None]
    for op, a in zip(ops[1:], args[1:]):
        f = getattr(obj, op)
        types = declared.get(op) or drivers.hinted_types(f)
        out.append(_out(f(*drivers.convert(a, types, ns)), ""))
    return out


# ---- checking modes (PLAN §7) ----------------------------------------------------------

def _matches(rendered, expected, mode, ret_type):
    exp = _load(expected)
    got = _load(rendered)
    if mode == "k_first":
        k, arr = got
        want = _k_first_expected(expected)
        if want is None:
            return None
        wk, warr = want
        # removeElement allows any order of the first k; for removeDuplicates they're sorted anyway.
        return k == wk and sorted(map(json.dumps, arr)) == sorted(map(json.dumps, warr))
    if exp is _NOPE or got is _NOPE:
        return rendered.replace(" ", "") == expected.replace(" ", "")
    if mode == "any_order":
        return _canon(got) == _canon(exp)
    return _close(got, exp)


_NOPE = object()


def _load(s):
    try:
        return json.loads(s)
    except Exception:
        return _NOPE


def _close(a, b):
    """Equal, with floats within 1e-5 (LeetCode's float tolerance)."""
    if isinstance(a, bool) or isinstance(b, bool):
        return a == b
    if isinstance(a, (int, float)) and isinstance(b, (int, float)):
        return math.isclose(a, b, rel_tol=1e-5, abs_tol=1e-5)
    if isinstance(a, list) and isinstance(b, list):
        return len(a) == len(b) and all(_close(x, y) for x, y in zip(a, b))
    return a == b


def _canon(v):
    """Order-insensitive form: every list sorted, recursively."""
    if isinstance(v, list):
        return sorted((_canon(x) for x in v), key=lambda x: json.dumps(x, sort_keys=True))
    return v


def _k_first_expected(expected):
    # Statement form: "2, nums = [1,2,_]"
    import re
    m = re.match(r"\s*(\d+)\s*,\s*nums\s*=\s*\[(.*)\]", expected)
    if not m:
        return None
    k = int(m.group(1))
    items = [x.strip() for x in m.group(2).split(",") if x.strip() and x.strip() != "_"]
    return k, [json.loads(x) for x in items][:k]


def _user_tb(e, headline=None):
    """The traceback, keeping only frames from the user's code; [headline] replaces the last line."""
    frames = [f for f in traceback.extract_tb(e.__traceback__) if f.filename == USER_FILE]
    lines = ["  File \"%s\", line %d, in %s\n    %s" % (USER_FILE, f.lineno, f.name, (f.line or "").strip()) for f in frames]
    if isinstance(e, SyntaxError) and e.filename == USER_FILE:
        lines.append("  File \"%s\", line %s\n    %s" % (USER_FILE, e.lineno, (e.text or "").strip()))
    head = "Traceback (most recent call last):\n" if lines else ""
    return head + "\n".join(lines) + ("\n" if lines else "") + (headline or "%s: %s" % (type(e).__name__, e))
