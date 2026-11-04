"""Stress tests (PLAN §7, rev 24): your solution against a reference on generated inputs.

The reference is a known-good solution saved with the problem (NeetCode's article, or a
LeetCode solution post). Inputs are generated from the parameter types and the statement's
Constraints section: sizes, value ranges, character sets, "sorted", "distinct", tree and list
sizes, edges within `n`. Random inputs start tiny and grow, so the first difference found is
usually small enough to read. One input at the constraints' full size then checks speed.

Inputs that break a promise the statement makes (and that the generator didn't understand) can
make the reference fail; those are skipped. A difference on such an input isn't always a bug,
so the result lists the assumptions the generator made.

request (on top of the usual): "references": [{"label", "code"}], "constraints": statement text,
"seconds": time budget. result["stress"]: {"tried", "skipped", "reference", "mismatch", "big",
"assumptions", "warnings"}.
"""

import copy
import json
import random
import re
import sys
import time

import titan_checkers as checkers
import titan_limits as limits

REF_FILE = "reference.py"
SMALL_LEN = 8          # the largest arrays in the random phase
SMALL_VAL = 10         # values are clipped to ±this in the random phase, to provoke collisions
BIG_TOTAL = 200000     # cap on the size of the speed-check input (elements, all arrays together)
MAX_TRIES = 600


def run(req, meta, result, watch, ns_user, h):
    result["status"] = "ok"
    result["cases"] = []
    params = meta.get("params", [])
    ret_type = (meta.get("return") or {}).get("type", "")
    number = req.get("number") or 0
    mode = h._mode(req, number)
    checker = checkers.CHECKERS.get(number)
    stress = {"tried": 0, "skipped": 0, "mismatch": None, "big": None, "assumptions": [], "warnings": []}
    result["stress"] = stress

    def compare(inputs, got, want):
        if mode == "k_first":  # [k, first k]: the first k in any order
            g, w = json.loads(got), json.loads(want)
            return g[0] == w[0] and sorted(map(json.dumps, g[1])) == sorted(map(json.dumps, w[1])), None
        if checker is not None:
            ok, note = checkers.check(checker, inputs, got, want)
            if ok is not None:
                return ok, note
        return h._matches(got, want, mode, ret_type), None

    # ---- references: compile, and keep those that agree with the known expected outputs ----
    refs = []
    for r in req.get("references") or []:
        ns = {"__name__": "__main__"}
        exec(h.PRELUDE, ns)
        try:
            exec(compile(r["code"], REF_FILE, "exec"), ns)
            getattr(ns["Solution"](), meta["name"])
        except Exception:
            continue
        refs.append((r.get("label") or "reference", ns))
    if not refs:
        result["status"] = "unsupported"
        result["error"] = "No reference solution to compare with. Open the problem's Comments (solutions) once while online, then try again."
        return result
    known = [(i, e) for i, e in zip(req.get("inputs") or [], req.get("expected") or []) if e is not None]
    good = []
    for label, ns in refs:
        ok = True
        for raw, exp in known:
            try:
                got, inputs = _call(h, ns, meta, raw.split("\n"), mode, watch)
                if not compare(inputs, got, exp)[0]:
                    ok = False
            except BaseException:
                ok = False
            if not ok:
                break
        if ok:
            good.append((label, ns))
    if not good:
        stress["warnings"].append("No reference passed the examples; using the first one anyway.")
        good = refs[:1]
    stress["reference"] = good[0][0]

    # ---- the generator ----
    try:
        gen = Generator(params, req.get("constraints") or "", number)
    except Unsupported as e:
        result["status"] = "unsupported"
        result["error"] = "Stress tests can't generate this problem's inputs: %s." % e
        return result
    stress["assumptions"] = gen.describe()
    stress["warnings"] += gen.warnings

    rng = random.Random(req.get("seed", 24))
    budget = float(req.get("seconds") or 8)
    start = time.monotonic()
    tries = 0
    while tries < MAX_TRIES and time.monotonic() - start < budget * 0.75:
        size = 1 + min(SMALL_LEN - 1, tries // 25)  # grow slowly: the first difference is small
        tries += 1
        try:
            lines = gen.sample(rng, small=True, cap=size)
        except Unsupported:
            stress["skipped"] += 1
            continue
        want, ref_err = _reference(h, good, meta, lines, mode, watch)
        if ref_err is not None:
            stress["skipped"] += 1
            continue
        stress["tried"] += 1
        try:
            got, inputs = _call(h, ns_user, meta, lines, mode, watch)
        except BaseException as e:
            stress["mismatch"] = _mismatch(h, lines, None, want, e, watch)
            break
        ok, note = compare(inputs, got, want)
        if not ok:
            stress["mismatch"] = {"input": "\n".join(lines), "output": got, "expected": want, "error": None, "note": note}
            break

    # ---- one big input, for speed ----
    if stress["mismatch"] is None and gen.can_grow():
        try:
            lines = gen.sample(rng, small=False)
        except Unsupported:
            lines = None
        if lines is not None:
            big = {"size": gen.size_text(lines), "ms": None, "refMs": None, "error": None, "limit": None}
            t0 = time.perf_counter()
            try:
                got, inputs = _call(h, ns_user, meta, lines, mode, watch)
                big["ms"] = round((time.perf_counter() - t0) * 1000, 1)
            except limits.LIMITS as e:
                big["limit"] = e.kind
                big["error"] = h._user_tb(e, "%s on the big input" % watch.message(e.kind))
                got = None
            except BaseException as e:
                big["error"] = h._user_tb(e)
                got = None
            t1 = time.perf_counter()
            want, ref_err = _reference(h, good[-1:], meta, lines, mode, watch)  # the last approach is usually the fastest
            if ref_err is None:
                big["refMs"] = round((time.perf_counter() - t1) * 1000, 1)
                if got is not None and not compare(inputs, got, want)[0]:
                    stress["mismatch"] = {"input": limits.shorten("\n".join(lines), 2000), "output": limits.shorten(got, 2000),
                                          "expected": limits.shorten(want, 2000), "error": None,
                                          "note": "Different answer on the big input (%s)." % big["size"]}
            stress["big"] = big
    stress["seconds"] = round(time.monotonic() - start, 1)
    return result


def _call(h, ns, meta, lines, mode, watch):
    """One call under the per-case limits, with its prints thrown away."""
    out = limits.CappedOutput(keep=0)
    old = sys.stdout
    try:
        sys.stdout = out
        watch.begin()
        try:
            return h.call_solution(ns, meta, list(lines), mode)
        finally:
            watch.end()
    finally:
        sys.stdout = old


def _reference(h, refs, meta, lines, mode, watch):
    """The first reference that handles the input; an input none can handle is skipped."""
    err = None
    for _, ns in refs:
        try:
            return _call(h, ns, meta, lines, mode, watch)[0], None
        except BaseException as e:
            err = e
    return None, err


def _mismatch(h, lines, got, want, e, watch):
    if isinstance(e, limits.LIMITS):
        err = h._user_tb(e, "%s: %s" % ({"time": "Time Limit Exceeded", "memory": "Memory Limit Exceeded",
                                          "output": "Output Limit Exceeded"}[e.kind], watch.message(e.kind) or e))
    else:
        err = h._user_tb(e)
    return {"input": "\n".join(lines), "output": got, "expected": want, "error": err, "note": None}


# ---- constraints --------------------------------------------------------------------------------

class Unsupported(Exception):
    pass


_SUP = str.maketrans("⁰¹²³⁴⁵⁶⁷⁸⁹", "0123456789")


def _norm(text):
    t = text.replace("≤", "<=").replace("≥", ">=").replace("−", "-").replace("–", "-").replace(" ", " ")
    t = t.replace("&lt;", "<").replace("&gt;", ">").replace("`", "").replace("*", " * ").replace("×", " * ")
    t = re.sub(r"10([⁰¹²³⁴⁵⁶⁷⁸⁹]+)", lambda m: "10^" + m.group(1).translate(_SUP), t)
    return t


def _num(expr, env):
    """Evaluate a bound like `10^4`, `5 * 10^4`, `n - 1` or `nums.length`; None if unknown."""
    e = expr.strip().rstrip(".,;")
    if not e:
        return None

    def name(m):
        k = m.group(0)
        if k in env:
            return str(env[k])
        raise KeyError(k)
    try:
        e = re.sub(r"[A-Za-z_][\w]*(?:\[i\])?(?:\.length)?", name, e)
    except KeyError:
        return None
    e = e.replace("^", "**").replace(" ", "")
    if not re.fullmatch(r"[0-9+\-*/()]+", e) or not e:
        return None
    try:
        v = eval(e, {"__builtins__": {}}, {})
    except Exception:
        return None
    return int(v) if isinstance(v, (int, float)) else None


class Spec:
    """What the constraints say about one parameter."""

    def __init__(self, name, type_):
        self.name, self.type = name, type_
        self.len = None        # (lo expr, hi expr) as strings; evaluated when generating
        self.val = None
        self.inner_len = None
        self.inner_val = None
        self.cols = {}         # pair column -> (lo, hi): edges[i] = [a_i, b_i]
        self.col_order = False  # start_i <= end_i: sort each pair
        self.chars = None
        self.inner_chars = None
        self.sorted = False
        self.strict = False
        self.distinct = False
        self.no_self = False
        self.bst = False
        self.choices = None    # grid[i][j] is 0, 1, or 2
        self.rotated = False   # sorted, then possibly rotated
        self.square = False
        self.col_strict = False
        self.no_leading_zero = False
        self.sorted_rows = False


class Generator:
    def __init__(self, params, text, number=0):
        self.warnings = []
        self.lenient = False
        self.custom = CUSTOM.get(number)
        self.min_total = []  # (names, lo): 1 <= m + n
        self.specs = [Spec(p["name"], p["type"]) for p in params]
        self.by_name = {s.name: s for s in self.specs}
        for s in self.specs:
            if not _known(s.type):
                raise Unsupported("parameters of type %s" % s.type)
        self.aliases = {}  # n -> ("nums", "len")
        self.free = {}     # m -> (lo, hi) for aliases
        self.nodes = None  # node count of the tree/list
        self.node_val = None
        body = _norm(text)
        i = body.lower().rfind("constraints")
        cons = body[i:] if i >= 0 else body
        self.whole = body.lower()
        if re.search(r"only one valid answer|exactly one solution|exactly one valid", self.whole):
            self.warnings.append("The problem promises exactly one answer; a random input may have several, so a difference isn't always a bug.")
        for line in re.split(r"[\n•]+", cons):
            self._line(line.strip())
        # "Given two sorted arrays nums1 and nums2": the opening sentence promises sorted input.
        intro = self.whole.split("example 1")[0][:600]
        for s in self.specs:
            if s.type in ("integer[]", "long[]", "list<integer>") and re.search(r"(?:sorted\b[^.]*\b%s\b|\b%s\b[^.]*\bsorted in)" % (re.escape(s.name.lower()), re.escape(s.name.lower())), intro):
                s.sorted = True
        if re.search(r"each row is sorted|rows? (?:are|is) sorted|sorted in (?:non-decreasing|ascending) order from left to right", self.whole):
            for s in self.specs:
                if s.type in ("integer[][]", "list<list<integer>>"):
                    s.sorted_rows = True
        bst = re.search(r"root of a binary search tree|\bbst\b", cons.lower()) or re.search(r"root of a binary search tree", self.whole)
        for s in self.specs:
            if s.type in ("TreeNode",) and bst:
                s.bst = True

    # -- parsing --
    def _target(self, term):
        """`nums.length` → (spec, "len"), `nums[i]` → (spec, "val"), `n` → alias or scalar; None if unknown."""
        t = term.strip().strip(".").strip()
        m = re.fullmatch(r"(\w+)\[\w\]\.length", t) or re.fullmatch(r"(\w+)\[\w\]\[\w\]\.length", t)
        if m and m.group(1) in self.by_name:
            return self.by_name[m.group(1)], "inner_len"
        m = re.fullmatch(r"(\w+)\.length", t)
        if m and m.group(1) in self.by_name:
            return self.by_name[m.group(1)], "len"
        m = re.fullmatch(r"(\w+)\[\w\]\[\w\]", t)
        if m and m.group(1) in self.by_name:
            return self.by_name[m.group(1)], "inner_val"
        m = re.fullmatch(r"(\w+)\[\w\]", t)
        if m and m.group(1) in self.by_name:
            return self.by_name[m.group(1)], "val"
        if re.fullmatch(r"(Node|node)\.val", t):
            return "#node", "val"
        if t in self.by_name:
            return self.by_name[t], "val"
        if t in self.aliases:
            return ("#alias", t)
        m = re.fullmatch(r"([a-z]+)_?i", t)  # a_i, start_i, x_i: a column of a pair parameter
        if m:
            return "#col", t
        if re.fullmatch(r"[A-Za-z]\w*", t):
            return "#free", t
        return None

    def _set(self, target, lo, hi, strict_lo, strict_hi):
        if lo is not None and strict_lo:
            lo = "(%s)+1" % lo
        if hi is not None and strict_hi:
            hi = "(%s)-1" % hi
        kind = target[1]
        obj = target[0]
        if obj == "#alias":
            for t in self.aliases[kind]:
                self._set(t, lo, hi, False, False)
            self.free[kind] = _merge(self.free.get(kind), lo, hi)
            return
        if obj == "#node":
            self.node_val = _merge(self.node_val, lo, hi)
        elif obj == "#col":
            pairs = [s for s in self.specs if s.type in ("integer[][]", "list<list<integer>>")]
            if pairs:
                pairs[0].cols[kind] = _merge(pairs[0].cols.get(kind), lo, hi)
        elif obj == "#free":
            self.free[kind] = _merge(self.free.get(kind), lo, hi)
        elif obj == "#nodes":
            self.nodes = _merge(self.nodes, lo, hi)
        else:
            setattr(obj, kind, _merge(getattr(obj, kind), lo, hi))

    def _line(self, line):
        if not line:
            return
        low = line.lower()
        m = re.search(r"number of nodes in [\w\s]*?(?:tree|list|graph)s?\b.*?range \[\s*([^,\]]+)\s*,\s*([^\]]+)\]", low)
        if m:
            self.nodes = (m.group(1), m.group(2))
            return
        m2 = re.match(r"\s*(\w+) is in the range \[\s*([^,\]]+)\s*,\s*([^\]]+)\]", line)
        if m2 and m2.group(1) in self.by_name:
            self._set((self.by_name[m2.group(1)], "val"), m2.group(2), m2.group(3), False, False)
        m = re.search(r"number of nodes in (?:the |each )?(?:tree|list|linked list)s? is (\w+)", low)
        if m:
            self.aliases.setdefault(m.group(1), []).append(("#nodes", "len"))
        # edges[i] = [a_i, b_i]
        m = re.search(r"(\w+)\[i\]\s*=\s*\[\s*(\w+)\s*,\s*(\w+)\s*\]", line)
        if m and m.group(1) in self.by_name:
            s = self.by_name[m.group(1)]
            s.cols.setdefault(m.group(2), None)
            s.cols.setdefault(m.group(3), None)
            s.inner_len = ("2", "2")
        if "!=" in line and re.search(r"\w+_i\s*!=\s*\w+_i", line):
            for s in self.specs:
                if s.type in ("integer[][]", "list<list<integer>>"):
                    s.no_self = True
        mc = re.search(r"(\w+)\[\w\](\[\w\])? is (?:either )?(-?\d+(?:\s*,\s*-?\d+)*,?\s*(?:or|and)\s*-?\d+)", line)
        if mc and mc.group(1) in self.by_name:
            self.by_name[mc.group(1)].choices = [int(x) for x in re.findall(r"-?\d+", mc.group(3))]
        if "leading zero" in low:
            for n in self.by_name:
                if re.search(r"\b%s\b" % re.escape(n), line):
                    self.by_name[n].no_leading_zero = True
        if "rotated" in low:
            for n in self.by_name:
                if re.search(r"\b%s\b" % re.escape(n), line):
                    self.by_name[n].rotated = True
        # flags
        names = [n for n in self.by_name if re.search(r"\b%s\b" % re.escape(n), line)]
        target = self.by_name[names[0]] if names else None
        if re.search(r"\bsorted\b|non-decreasing|ascending|increasing order", low) and target is not None:
            target.sorted = True
            target.strict = "strictly" in low
        if re.search(r"\b(unique|distinct)\b", low):
            if target is not None:
                target.distinct = True
            elif "node" in low or "values" in low:
                for s in self.specs:
                    if s.type in ("TreeNode", "ListNode"):
                        s.distinct = True
        if re.search(r"consist|letter|digit|'|\bcharacters?\b", low):
            chars = _charset(line)
            if chars:
                for n in names or [s.name for s in self.specs if _stringy(s.type)][:1]:
                    s = self.by_name[n]
                    if re.search(r"\b%s\[i\]" % re.escape(n), line) and s.type in ("string[]", "list<string>"):
                        s.inner_chars = chars
                    else:
                        s.chars = chars
        # comparison chains
        if not re.search(r"<=|<|==|>=", line):
            return
        line2 = re.sub(r"\(.*?\)", "", line)
        parts = re.split(r"\s*(<=|<|==|>=)\s*", line2)
        if len(parts) < 3:
            return
        terms, ops = parts[0::2], parts[1::2]
        if any(o == ">=" for o in ops):
            return
        if ops == ["=="]:
            a, b = terms
            ta, tb = self._target(b), a.strip()
            if ta is not None and re.fullmatch(r"[A-Za-z]\w*", tb) and tb not in self.by_name:
                self.aliases.setdefault(tb, []).append(ta)  # n == nums.length
                return
            ta2 = self._target(a)
            if ta2 is not None and re.fullmatch(r"[A-Za-z]\w*", b.strip()) and b.strip() not in self.by_name:
                self.aliases.setdefault(b.strip(), []).append(ta2)  # nums.length == n
                return
            if ta2 is not None and (_num(b, {}) is not None or ta is not None):
                # A fixed size, or the same size as another input (endWord.length == beginWord.length).
                self._set(ta2, b.strip(), b.strip(), False, False)
            return
        if ops and all(o == "==" for o in ops):
            fixed = next((x for x in terms if _num(x, {}) is not None), None)
            if fixed is not None:  # target.length == triplets[i].length == 3
                for x in terms:
                    t = self._target(x)
                    if t is not None and t[0] not in ("#free", "#col") and _num(x, {}) is None:
                        self._set(t, fixed, fixed, False, False)
                return
            # n == matrix.length == matrix[i].length: a square
            name = next((x.strip() for x in terms if re.fullmatch(r"[A-Za-z]\w*", x.strip()) and x.strip() not in self.by_name), None)
            for x in terms:
                t = self._target(x)
                if name and t is not None and t[0] not in ("#free", "#alias", "#col"):
                    self.aliases.setdefault(name, []).append(t)
            return
        # lo <= x <= hi (x may be "m, n")
        def order(t):
            return self.specs.index(t[0]) if t is not None and isinstance(t[0], Spec) else -1

        def outward(k, step, cur):
            # 1 <= k <= nums.length <= 10^4: nums.length's lower bound is k, which is generated
            # after it; look past such members to the next bound out (transitively, 1).
            j = k + step
            while 0 < j < len(terms) - 1:
                t = self._target(terms[j])
                if t is None or _num(terms[j], {}) is not None or order(t) <= order(cur):
                    break
                j += step
            return terms[j]

        for k in range(1, len(terms) - 1):
            # 1 <= m + n <= 2000: at least that many elements in all
            if "+" in terms[k] and _num(terms[k - 1], {}) is not None:
                names = [x.strip() for x in terms[k].split("+")]
                if all(re.fullmatch(r"[A-Za-z]\w*(?:\.length)?", x) for x in names):
                    self.min_total.append((names, _num(terms[k - 1], {})))
                continue
            mids = [x for x in re.split(r"\s*,\s*", terms[k]) if x]
            for mid in mids:
                t = self._target(mid)
                if t is None:
                    continue
                lo, hi = outward(k, -1, t), outward(k, +1, t)
                self._set(t, lo if _looks_bound(lo) else None, hi if _looks_bound(hi) else None, ops[k - 1] == "<", ops[k] == "<")
        # start_i <= end_i: pairs are ordered
        if len(terms) >= 4 and all(re.fullmatch(r"[a-z]+_?i", x.strip()) for x in terms[1:-1]):
            for s in self.specs:
                if s.type in ("integer[][]", "list<list<integer>>"):
                    s.col_order = True
                    s.col_strict = "<" in ops[1:-1]

    # -- description --
    def describe(self):
        if self.custom is not None:
            return [self.custom[1]]
        out = []
        for s in self.specs:
            bits = [s.type]
            if s.len:
                bits.append("length %s…%s" % s.len)
            if s.val:
                bits.append("values %s…%s" % s.val)
            if s.inner_len:
                bits.append("inner length %s…%s" % s.inner_len)
            if s.inner_val:
                bits.append("inner values %s…%s" % s.inner_val)
            if s.chars:
                bits.append("characters %s" % _show_chars(s.chars))
            if s.inner_chars:
                bits.append("characters %s" % _show_chars(s.inner_chars))
            if s.sorted:
                bits.append("strictly increasing" if s.strict else "sorted")
            if s.distinct:
                bits.append("distinct")
            if s.bst:
                bits.append("a binary search tree")
            if s.type in ("TreeNode", "ListNode") and self.nodes:
                bits.append("%s…%s nodes" % self.nodes)
            if s.type in ("TreeNode", "ListNode") and self.node_val:
                bits.append("node values %s…%s" % self.node_val)
            out.append("%s: %s" % (s.name, ", ".join(bits)))
        return out

    def can_grow(self):
        return any(not _scalar(s.type) for s in self.specs)

    def size_text(self, lines):
        parts = []
        for s, line in zip(self.specs, lines):
            if not _scalar(s.type):
                v = json.loads(line)
                parts.append("%s: %d" % (s.name, len(v)))
        return ", ".join(parts)

    # -- sampling --
    def sample(self, rng, small, cap=SMALL_LEN):
        if self.custom is not None:
            vals = self.custom[0](rng, small, cap)
            return [json.dumps(v, separators=(",", ":")) for v in vals]
        for _ in range(20):
            lines, env = self._sample(rng, small, cap)
            if all(sum(env.get(n if n.endswith(".length") else n, env.get(n + ".length", 0)) for n in names) >= lo for names, lo in self.min_total):
                return lines
        raise Unsupported("an input that meets every constraint")

    def _sample(self, rng, small, cap):
        env = {}
        values = {}
        pending = list(self.specs)
        self.lenient = False
        for attempt in range(4):
            # The last pass uses defaults for bounds that never resolve (`sum(nums[i]) <= …`).
            self.lenient = attempt == 3
            later = []
            for s in pending:
                try:
                    values[s.name] = self._gen(s, rng, small, cap, env)
                except _Later:
                    later.append(s)
            pending = later
            if not pending:
                break
        if pending:
            raise Unsupported("bounds that depend on each other")
        return [json.dumps(values[s.name], separators=(",", ":")) for s in self.specs], env

    def _range(self, r, env, default, small, cap):
        lo, hi = default
        if r is not None and "#unique" in env:
            r = tuple(("#U" if x and re.search(r"unique|distinct", x, re.I) else x) for x in r)
            r = tuple((str(env["#unique"]) if x == "#U" else x) for x in r)
        if r is not None:
            a = _num(r[0], env) if r[0] is not None else None
            b = _num(r[1], env) if r[1] is not None else None
            if (r[0] is not None and a is None) or (r[1] is not None and b is None):
                # Depends on something not generated yet?
                if not self.lenient and any(re.search(r"[A-Za-z]", x or "") for x in r):
                    raise _Later()
            lo = a if a is not None else lo
            hi = b if b is not None else hi
        if small:
            if hi - lo > 2 * cap:
                c_lo, c_hi = max(lo, -cap), min(hi, cap)
                if c_lo > c_hi:
                    c_lo, c_hi = lo, min(hi, lo + 2 * cap)
                lo, hi = c_lo, c_hi
        if lo > hi:
            raise Unsupported("an empty range")
        return lo, hi

    def _len(self, s, rng, small, cap, env, which="len", default=(0, 1000)):
        # Already decided through a shared name (n == matrix.length == matrix[i].length).
        for alias, ts in self.aliases.items():
            if (s, which) in ts and alias in env and isinstance(env[alias], int):
                env["%s.length" % s.name] = env[alias]
                return env[alias]
        r = getattr(s, which)
        if r is None:
            for alias, ts in self.aliases.items():
                if (s, which) in ts and alias in self.free:
                    r = self.free[alias]
        if s.type in ("TreeNode", "ListNode") and which == "len":
            r = self.nodes
            for alias, ts in self.aliases.items():
                if ("#nodes", "len") in ts and alias in self.free:
                    r = self.free[alias]
        lo, hi = self._range(r, env, default, False, cap)
        if small:
            hi = min(hi, max(lo, cap))
        else:
            hi = min(hi, BIG_TOTAL)
            lo = hi
        n = rng.randint(lo, hi) if small else hi
        env["%s.length" % s.name] = n
        for alias, ts in self.aliases.items():
            if (s, which) in ts or (s.type in ("TreeNode", "ListNode") and ("#nodes", "len") in ts):
                env.setdefault(alias, n)
        return n

    def _values(self, s, rng, n, r, small, cap, env, chars=None, distinct=False, sort=False, strict=False):
        if chars is not None:
            pool = chars
            if distinct and n > len(pool):
                n = len(pool)
            vals = rng.sample(pool, n) if distinct else [rng.choice(pool) for _ in range(n)]
        else:
            lo, hi = self._range(r, env, (-SMALL_VAL, SMALL_VAL) if small else (-10 ** 9, 10 ** 9), small, SMALL_VAL)
            if distinct or strict:
                n = min(n, hi - lo + 1)
                vals = rng.sample(range(lo, hi + 1), n) if hi - lo < 10 ** 7 else list({rng.randint(lo, hi) for _ in range(n)})
            else:
                vals = [rng.randint(lo, hi) for _ in range(n)]
        if sort or strict:
            vals.sort()
        return vals

    def _gen(self, s, rng, small, cap, env):
        t = s.type
        if t in ("integer", "long"):
            lo, hi = self._range(s.val, env, (-SMALL_VAL, SMALL_VAL) if small else (0, 10 ** 5), small, SMALL_VAL)
            v = rng.randint(lo, hi)
            env[s.name] = v
            return v
        if t == "double":
            lo, hi = self._range(s.val, env, (-SMALL_VAL, SMALL_VAL), small, SMALL_VAL)
            return round(rng.uniform(lo, hi), 2)
        if t == "boolean":
            return rng.random() < 0.5
        if t == "string":
            n = self._len(s, rng, small, cap, env)
            v = "".join(self._values(s, rng, n, None, small, cap, env, chars=s.chars or _LOWER, distinct=s.distinct))
            if s.no_leading_zero and len(v) > 1 and v[0] == "0":
                v = rng.choice("123456789") + v[1:]
            return v
        if t == "character":
            return rng.choice(s.chars or _LOWER)
        if t in ("integer[]", "long[]", "list<integer>", "list<long>"):
            n = self._len(s, rng, small, cap, env)
            if s.choices:
                vals = [rng.choice(s.choices) for _ in range(n)]
            else:
                vals = self._values(s, rng, n, s.val, small, cap, env, distinct=s.distinct, sort=s.sorted or s.rotated, strict=s.strict)
            if s.rotated and vals:
                k = rng.randrange(len(vals))
                vals = vals[k:] + vals[:k]
            env.setdefault("#unique", len(set(vals)))
            return vals
        if t in ("double[]",):
            n = self._len(s, rng, small, cap, env)
            lo, hi = self._range(s.val, env, (-SMALL_VAL, SMALL_VAL), small, SMALL_VAL)
            return [round(rng.uniform(lo, hi), 2) for _ in range(n)]
        if t in ("boolean[]",):
            return [rng.random() < 0.5 for _ in range(self._len(s, rng, small, cap, env))]
        if t in ("character[]",):
            n = self._len(s, rng, small, cap, env)
            return [rng.choice(s.chars or _LOWER) for _ in range(n)]
        if t in ("string[]", "list<string>"):
            n = self._len(s, rng, small, cap, env)
            out = []
            for _ in range(n):
                lo, hi = self._range(s.inner_len, env, (1, 10), True, cap)
                m = rng.randint(lo, min(hi, max(lo, cap if small else 20)))
                out.append("".join(rng.choice(s.inner_chars or s.chars or _LOWER) for _ in range(m)))
            if s.distinct:
                out = list(dict.fromkeys(out))
            if s.sorted:
                out.sort()
            return out
        if t in ("integer[][]", "list<list<integer>>", "character[][]", "string[][]", "list<list<string>>"):
            return self._grid(s, rng, small, cap, env)
        if t == "ListNode":
            n = self._len(s, rng, small, cap, env)
            return self._values(s, rng, n, self.node_val or s.val, small, cap, env, distinct=s.distinct,
                                sort=s.sorted or "sorted" in self.whole)
        if t == "ListNode[]":
            k = self._len(s, rng, small, cap, env)
            out = []
            for _ in range(k):
                lo, hi = self._range(s.inner_len, env, (0, 10), True, cap)
                m = rng.randint(lo, min(hi, max(lo, cap if small else 500)))
                out.append(self._values(s, rng, m, s.inner_val or self.node_val, small, cap, env, sort=True))
            return out
        if t == "TreeNode":
            n = self._len(s, rng, small, cap, env)
            vals = self._values(s, rng, n, self.node_val or s.val, small, cap, env, distinct=s.distinct or s.bst)
            return _bst_level_order(vals) if s.bst else _random_tree(vals, rng)
        raise Unsupported("parameters of type %s" % t)

    def _grid(self, s, rng, small, cap, env):
        chars = s.chars
        fixed = _num(s.inner_len[0], env) if s.inner_len and s.inner_len[0] == s.inner_len[1] else None
        char_grid = s.type == "character[][]"
        pair_like = not char_grid and (bool(s.cols) or (fixed is not None and fixed <= 3))
        rows = self._len(s, rng, small, cap, env)
        if pair_like and s.type in ("string[][]", "list<list<string>>"):
            pool = chars or list("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
            width = fixed or 2
            k = 1 if small else 3
            words = ["".join(rng.choice(pool) for _ in range(3)) for _ in range(max(2, k * 2 if small else 50))]
            return [[rng.choice(words) for _ in range(width)] for _ in range(rows)]
        if pair_like:
            width = _num(s.inner_len[0], env) if s.inner_len else 2
            cols = list(s.cols.keys())
            out = []
            for _ in range(rows):
                row = []
                for c in range(width):
                    r = s.cols.get(cols[c]) if c < len(cols) else None
                    r = r or s.inner_val
                    lo, hi = self._range(r, env, (0, SMALL_VAL), small, SMALL_VAL)
                    row.append(rng.randint(lo, hi))
                if s.col_order:
                    row[:2] = sorted(row[:2])
                    if s.col_strict and len(row) >= 2 and row[0] == row[1]:
                        row[1] += 1
                if s.no_self and len(row) >= 2 and row[0] == row[1]:
                    continue
                out.append(row)
            if s.distinct or "unique" in self.whole or "duplicate" in self.whole:
                seen, uniq = set(), []
                for row in out:
                    key = tuple(row[:2]) if len(row) > 2 else (tuple(sorted(row)) if s.no_self else tuple(row))
                    if key not in seen:
                        seen.add(key)
                        uniq.append(row)
                out = uniq
                env["%s.length" % s.name] = len(out)
                for alias, ts in self.aliases.items():
                    if (s, "len") in ts:
                        env[alias] = len(out)
            return out
        width = self._len(s, rng, small, cap, env, which="inner_len", default=(1, 1000))
        if not small:
            width = max(1, min(width, BIG_TOTAL // max(rows, 1)))
        if char_grid or chars is not None:
            pool = chars or _LOWER
            return [[rng.choice(pool) for _ in range(width)] for _ in range(rows)]
        if s.choices:
            return [[rng.choice(s.choices) for _ in range(width)] for _ in range(rows)]
        lo, hi = self._range(s.inner_val or s.val, env, (-SMALL_VAL, SMALL_VAL), small, SMALL_VAL)
        if s.sorted_rows:  # row by row, each row after the last: sorted both ways
            flat = sorted(rng.randint(lo, hi) for _ in range(rows * width))
            return [flat[r * width:(r + 1) * width] for r in range(rows)]
        if s.distinct and (hi - lo + 1) >= rows * width:
            flat = rng.sample(range(lo, hi + 1), rows * width) if hi - lo < 10 ** 7 else [rng.randint(lo, hi) for _ in range(rows * width)]
            return [flat[r * width:(r + 1) * width] for r in range(rows)]
        return [[rng.randint(lo, hi) for _ in range(width)] for _ in range(rows)]


class _Later(Exception):
    pass


_LOWER = list("abcdefghijklmnopqrstuvwxyz")


def _merge(old, lo, hi):
    if old is None:
        return (lo, hi)
    return (lo if lo is not None else old[0], hi if hi is not None else old[1])


def _looks_bound(x):
    return bool(re.fullmatch(r"[\w\s.+\-*^()\[\]]+", x.strip())) and len(x.strip()) < 40


def _known(t):
    base = t.replace("[]", "").replace("list<", "").replace(">", "")
    return base in ("integer", "long", "double", "boolean", "string", "character", "ListNode", "TreeNode")


def _scalar(t):
    return t in ("integer", "long", "double", "boolean", "character")


def _stringy(t):
    return t in ("string", "string[]", "list<string>", "character", "character[]", "character[][]")


def _charset(line):
    low = line.lower()
    chars = []
    rng_m = re.search(r"range \[\s*'(.)'\s*,\s*'(.)'\s*\]", line)
    if rng_m:
        return [chr(c) for c in range(ord(rng_m.group(1)), ord(rng_m.group(2)) + 1)]
    quoted = [c for q in re.findall(r"'([^']{1,12})'", line) for c in q] + re.findall(r'"(.)"', line)
    if "lowercase" in low:
        chars += list("abcdefghijklmnopqrstuvwxyz")
    if "uppercase" in low:
        chars += list("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
    if "english letters" in low and "lowercase" not in low and "uppercase" not in low:
        chars += list("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
    if "digit" in low:
        chars += list("0123456789")
    if "space" in low:
        chars.append(" ")
    if "symbol" in low or "printable ascii" in low or "ascii" in low:
        chars += list("!#$%&()*+,-./:;<=>?@[]^_{|}~") + list("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ")
    chars += quoted
    return list(dict.fromkeys(chars)) or None


def _show_chars(chars):
    s = "".join(chars)
    return repr(s if len(s) <= 12 else s[:12] + "…")


def _random_tree(vals, rng):
    """A random tree shape over [vals], in level-order notation."""
    if not vals:
        return []
    out = [vals[0]]
    open_slots = 2
    i = 1
    while i < len(vals):
        if rng.random() < 0.25 and open_slots > 1:
            out.append(None)
            open_slots -= 1
        else:
            out.append(vals[i])
            i += 1
            open_slots += 1
    while out and out[-1] is None:
        out.pop()
    return out


def _bst_level_order(vals):
    """Insert [vals] into a BST and return its level order."""
    if not vals:
        return []
    root = [vals[0], None, None]
    for v in vals[1:]:
        n = root
        while True:
            side = 1 if v < n[0] else 2
            if n[side] is None:
                n[side] = [v, None, None]
                break
            n = n[side]
    out, q, qi = [], [root], 0
    while qi < len(q):
        n = q[qi]
        qi += 1
        if n is None:
            out.append(None)
            continue
        out.append(n[0])
        q.append(n[1])
        q.append(n[2])
    while out and out[-1] is None:
        out.pop()
    return out


# ---- problems whose inputs have structure the constraints can't express ---------------------------
# LeetCode number -> (generator(rng, small, cap) -> the parameter values, description)

def _n(rng, small, cap, big):
    return rng.randint(1, cap) if small else big


def _find_duplicate(rng, small, cap):  # 287: n + 1 values in 1..n, one value repeated (maybe often)
    n = _n(rng, small, cap, 10 ** 5)
    dup = rng.randint(1, n)
    nums = list(range(1, n + 1)) + [dup]
    for i in rng.sample(range(n), rng.randint(0, n - 1)):
        nums[i] = dup
    rng.shuffle(nums)
    return [nums]


def _traversals(order):
    def gen(rng, small, cap):  # 105, 106: two traversals of one random tree of distinct values
        n = _n(rng, small, cap, 3000)
        vals = rng.sample(range(-3000, 3001), n)
        nodes = [[vals[0], None, None]]
        free = [(0, 1), (0, 2)]
        for v in vals[1:]:  # each new node hangs off a random free slot
            p, side = free.pop(rng.randrange(len(free)))
            nodes[p][side] = len(nodes)
            nodes.append([v, None, None])
            free += [(len(nodes) - 1, 1), (len(nodes) - 1, 2)]
        pre, ino, post = [], [], []
        stack = [(0, 0)]
        while stack:  # iterative: the trees can be deep
            i, state = stack.pop()
            if i is None:
                continue
            v, l, r = nodes[i]
            if state == 0:
                pre.append(v)
                stack += [(i, 1), (l, 0)]
            elif state == 1:
                ino.append(v)
                stack += [(i, 2), (r, 0)]
            else:
                post.append(v)
        return [pre, ino] if order == "pre" else [ino, post]
    return gen


def _redundant(rng, small, cap):  # 684: a tree on 1..n plus one extra edge
    n = _n(rng, small, cap, 998) + 2
    edges = [[rng.randint(1, i - 1), i] for i in range(2, n + 1)]
    have = {tuple(e) for e in edges}
    for _ in range(100):
        a, b = sorted(rng.sample(range(1, n + 1), 2))
        if (a, b) not in have:
            edges.append([a, b])
            break
    rng.shuffle(edges)
    return [edges]


def _jump_ii(rng, small, cap):  # 45: every jump at least 1, so the end is reachable
    n = _n(rng, small, cap, 10 ** 4)
    return [[rng.randint(1, 5 if small else 1000) for _ in range(n - 1)] + [rng.randint(0, 5)]]


def _insert_interval(rng, small, cap):  # 57: sorted, non-overlapping intervals and one new interval
    n = rng.randint(0, cap) if small else 10 ** 4
    top = 30 if small else 10 ** 5
    pts = sorted(rng.sample(range(0, top + 1), min(2 * n, top + 1)))
    ivs = [[pts[i], pts[i + 1]] for i in range(0, len(pts) - 1, 2)]
    a, b = sorted(rng.randint(0, top) for _ in range(2))
    return [ivs, [a, b]]


def _single(times, singles):
    def gen(rng, small, cap):  # 136, 137, 260: every value repeated except the single ones
        k = _n(rng, small, max(1, cap // times), 10 ** 4)
        vals = rng.sample(range(-30, 31) if small else range(-3 * 10 ** 4, 3 * 10 ** 4), k + singles)
        nums = [v for v in vals[:k] for _ in range(times)] + vals[k:]
        rng.shuffle(nums)
        return [nums]
    return gen


def _rpn(rng, small, cap):  # 150: a valid expression in reverse Polish notation, no division by zero
    ops = rng.randint(0, cap) if small else 2000
    stack = []  # [(tokens, value)]
    for _ in range(ops + 1):
        v = rng.randint(-20, 20) if small else rng.randint(-200, 200)
        stack.append(([str(v)], v))
    while len(stack) > 1:
        i = rng.randrange(len(stack) - 1)
        (a, va), (b, vb) = stack[i], stack[i + 1]
        op = rng.choice("+-*/" if vb != 0 else "+-*")
        if op == "*" and abs(va * vb) > 10 ** 6:
            op = "+"
        v = {"+": va + vb, "-": va - vb, "*": va * vb, "/": int(va / vb) if vb else 0}[op]
        stack[i:i + 2] = [(a + b + [op], v)]
    return [stack[0][0]]


CUSTOM = {
    287: (_find_duplicate, "nums: n + 1 values in 1…n with one value repeated (built for this problem)"),
    105: (_traversals("pre"), "preorder, inorder: two traversals of one random tree of distinct values (built for this problem)"),
    106: (_traversals("post"), "inorder, postorder: two traversals of one random tree of distinct values (built for this problem)"),
    684: (_redundant, "edges: a tree on 1…n plus one extra edge (built for this problem)"),
    45: (_jump_ii, "nums: jumps of at least 1 so the end is reachable (built for this problem)"),
    57: (_insert_interval, "intervals: sorted and non-overlapping; newInterval: any (built for this problem)"),
    136: (_single(2, 1), "nums: every value twice except one (built for this problem)"),
    137: (_single(3, 1), "nums: every value three times except one (built for this problem)"),
    260: (_single(2, 2), "nums: every value twice except two (built for this problem)"),
    150: (_rpn, "tokens: a valid expression in reverse Polish notation (built for this problem)"),
}
