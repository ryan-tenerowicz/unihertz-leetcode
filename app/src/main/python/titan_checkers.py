"""Checkers for problems that accept any valid answer (PLAN §7, rev 24).

Comparing with the example's output fails a correct solution when several answers are right
("aba" is as good as "bab"). A checker validates the answer itself instead, from the inputs, so
it also judges your own test cases, which have no expected output.

A checker gets the inputs (parsed JSON, before the call: in-place problems see the original),
the answer (parsed JSON; for in-place problems the modified argument) and the expected answer
(parsed JSON, or None), and returns (ok, note). ok is None when it can't tell; the harness then
falls back to comparing with the expected output.

Chosen by LeetCode number, like the drivers.
"""

import json
from collections import Counter


def check(fn, inputs, rendered, expected):
    try:
        got = json.loads(rendered)
    except Exception:
        return False, "The answer isn't a valid value."
    exp = None
    if expected is not None:
        try:
            exp = json.loads(expected)
        except Exception:
            exp = None
    try:
        return fn(inputs, got, exp)
    except Exception as e:  # a malformed answer (wrong type…) is a wrong answer
        return False, "Couldn't check the answer: %s" % e


# ---- helpers ----------------------------------------------------------------------------------

class _N:
    __slots__ = ("val", "left", "right")

    def __init__(self, v):
        self.val, self.left, self.right = v, None, None


def _tree(vals):
    if not vals or vals[0] is None:
        return None
    root = _N(vals[0])
    q, qi, i = [root], 0, 1
    while i < len(vals) and qi < len(q):
        n = q[qi]
        qi += 1
        for side in ("left", "right"):
            if i < len(vals) and vals[i] is not None:
                c = _N(vals[i])
                setattr(n, side, c)
                q.append(c)
            i += 1
    return root


def _inorder(root):
    out, st, n = [], [], root
    while st or n:
        while n:
            st.append(n)
            n = n.left
        n = st.pop()
        out.append(n.val)
        n = n.right
    return out


def _is_bst(root):
    vals = _inorder(root)
    return all(a < b for a, b in zip(vals, vals[1:]))


def _balanced(root):
    """Height-balanced at every node."""
    ok = [True]

    def h(n, depth=0):
        if n is None:
            return 0
        a, b = h(n.left), h(n.right)
        if abs(a - b) > 1:
            ok[0] = False
        return 1 + max(a, b)
    h(root)
    return ok[0]


def _no_adjacent_equal(seq):
    return all(a != b for a, b in zip(seq, seq[1:]))


def _verdict(ok, why):
    return (True, None) if ok else (False, why)


# ---- checkers ---------------------------------------------------------------------------------

def longest_palindrome(inputs, got, exp):  # 5
    s = inputs[0]
    if not isinstance(got, str) or got not in s:
        return False, "Not a substring of s."
    if got != got[::-1]:
        return False, "Not a palindrome."
    best = 0
    for c in range(2 * len(s) - 1):
        l, r = c // 2, c // 2 + c % 2
        while l >= 0 and r < len(s) and s[l] == s[r]:
            l, r = l - 1, r + 1
        best = max(best, r - l - 1)
    return _verdict(len(got) == best, "A palindrome, but the longest has length %d." % best)


def course_order(inputs, got, exp):  # 210
    n, pre = inputs[0], inputs[1]
    indeg = [0] * n
    adj = [[] for _ in range(n)]
    for a, b in pre:
        adj[b].append(a)
        indeg[a] += 1
    q = [i for i in range(n) if indeg[i] == 0]
    seen = 0
    while q:
        x = q.pop()
        seen += 1
        for y in adj[x]:
            indeg[y] -= 1
            if indeg[y] == 0:
                q.append(y)
    if seen < n:
        return _verdict(got == [], "The prerequisites have a cycle, so the answer is [].")
    if sorted(got) != list(range(n)):
        return False, "Not an ordering of all %d courses." % n
    pos = {c: i for i, c in enumerate(got)}
    for a, b in pre:
        if pos[b] > pos[a]:
            return False, "Course %d comes before its prerequisite %d." % (a, b)
    return True, None


def alien_order(inputs, got, exp):  # 269 (NeetCode "Foreign Dictionary")
    words = inputs[0]
    letters = set("".join(words))
    edges = set()
    for a, b in zip(words, words[1:]):
        for x, y in zip(a, b):
            if x != y:
                edges.add((x, y))
                break
        else:
            if len(a) > len(b):
                return _verdict(got == "", "A word comes before its own prefix, so the answer is \"\".")
    # Is there an order at all?
    indeg = Counter()
    adj = {c: [] for c in letters}
    for x, y in edges:
        adj[x].append(y)
        indeg[y] += 1
    q = [c for c in letters if indeg[c] == 0]
    seen = 0
    while q:
        c = q.pop()
        seen += 1
        for y in adj[c]:
            indeg[y] -= 1
            if indeg[y] == 0:
                q.append(y)
    if seen < len(letters):
        return _verdict(got == "", "The words contradict each other, so the answer is \"\".")
    if not isinstance(got, str) or sorted(got) != sorted(letters):
        return False, "Must contain each letter of the words exactly once."
    pos = {c: i for i, c in enumerate(got)}
    for x, y in edges:
        if pos[x] > pos[y]:
            return False, "'%s' must come before '%s'." % (x, y)
    return True, None


def reorganize(inputs, got, exp):  # 767, 1054 (arrays)
    s = inputs[0]
    most = max(Counter(s).values(), default=0)
    if most > (len(s) + 1) // 2:
        return _verdict(got in ("", []), "Impossible here, so the answer is empty.")
    if Counter(got) != Counter(s):
        return False, "Not a rearrangement of the input."
    return _verdict(_no_adjacent_equal(got), "Two equal neighbours.")


def by_frequency(inputs, got, exp):  # 451
    s = inputs[0]
    if Counter(got) != Counter(s):
        return False, "Not a rearrangement of s."
    groups, i = [], 0
    while i < len(got):
        j = i
        while j < len(got) and got[j] == got[i]:
            j += 1
        groups.append((got[i], j - i))
        i = j
    if len(groups) != len(set(c for c, _ in groups)):
        return False, "Equal characters must be together."
    counts = [n for _, n in groups]
    return _verdict(all(a >= b for a, b in zip(counts, counts[1:])), "Not sorted by frequency, highest first.")


def parity(inputs, got, exp):  # 905
    nums = inputs[0]
    if Counter(got) != Counter(nums):
        return False, "Not a rearrangement of nums."
    first_odd = next((i for i, x in enumerate(got) if x % 2), len(got))
    return _verdict(all(x % 2 for x in got[first_odd:]), "An even number comes after an odd one.")


def parity_ii(inputs, got, exp):  # 922
    nums = inputs[0]
    if Counter(got) != Counter(nums):
        return False, "Not a rearrangement of nums."
    bad = next((i for i, x in enumerate(got) if x % 2 != i % 2), None)
    return _verdict(bad is None, "Index %s has the wrong parity." % bad)


def wiggle(inputs, got, exp):  # 324 (in place: got is nums afterwards)
    nums = inputs[0]
    if Counter(got) != Counter(nums):
        return False, "nums isn't a rearrangement of the input any more."
    for i in range(len(got) - 1):
        if (got[i] < got[i + 1]) != (i % 2 == 0):
            return False, "nums[%d] and nums[%d] break the wiggle pattern." % (i, i + 1)
    return True, None


def happy_string(inputs, got, exp):  # 1405
    a, b, c = inputs
    cnt = Counter(got)
    if set(got) - set("abc") or cnt["a"] > a or cnt["b"] > b or cnt["c"] > c:
        return False, "Uses letters it doesn't have."
    if any(x * 3 in got for x in "abc"):
        return False, "Contains three equal letters in a row."
    if exp is None:
        return True, "Valid; whether it's the longest can't be checked without an expected output."
    return _verdict(len(got) == len(exp), "Valid, but the longest has length %d." % len(exp))


def no_aaa_bbb(inputs, got, exp):  # 984
    a, b = inputs
    cnt = Counter(got)
    if set(got) - set("ab") or cnt["a"] != a or cnt["b"] != b:
        return False, "Needs exactly %d a's and %d b's." % (a, b)
    return _verdict("aaa" not in got and "bbb" not in got, "Contains three equal letters in a row.")


def not_average(inputs, got, exp):  # 1968
    nums = inputs[0]
    if Counter(got) != Counter(nums):
        return False, "Not a rearrangement of nums."
    bad = next((i for i in range(1, len(got) - 1) if got[i] * 2 == got[i - 1] + got[i + 1]), None)
    return _verdict(bad is None, "nums[%s] is the average of its neighbours." % bad)


def unique_binary(inputs, got, exp):  # 1980
    nums = inputs[0]
    n = len(nums)
    if not isinstance(got, str) or len(got) != n or set(got) - set("01"):
        return False, "Must be a binary string of length %d." % n
    return _verdict(got not in nums, "That string is in nums.")


def bst_delete(inputs, got, exp):  # 450
    before, key = _inorder(_tree(inputs[0])), inputs[1]
    t = _tree(got)
    if not _is_bst(t):
        return False, "Not a binary search tree."
    want = [v for v in before if v != key]
    return _verdict(_inorder(t) == want, "The tree should hold exactly the other values.")


def bst_insert(inputs, got, exp):  # 701
    t = _tree(got)
    if not _is_bst(t):
        return False, "Not a binary search tree."
    want = sorted(_inorder(_tree(inputs[0])) + [inputs[1]])
    return _verdict(_inorder(t) == want, "The tree should hold the old values and the new one.")


def balanced_bst(values):
    def fn(inputs, got, exp):
        t = _tree(got)
        if _inorder(t) != sorted(values(inputs)):
            return False, "Not a BST of exactly the given values."
        return _verdict(_balanced(t), "Not height-balanced.")
    return fn


def supersequence(inputs, got, exp):  # 1092
    a, b = inputs

    def sub(x, y):
        it = iter(y)
        return all(ch in it for ch in x)
    if not isinstance(got, str) or not (sub(a, got) and sub(b, got)):
        return False, "Doesn't contain both strings as subsequences."
    # LCS length gives the shortest possible length.
    prev = [0] * (len(b) + 1)
    for x in a:
        cur = [0]
        for j, y in enumerate(b):
            cur.append(prev[j] + 1 if x == y else max(prev[j + 1], cur[j]))
        prev = cur
    best = len(a) + len(b) - prev[-1]
    return _verdict(len(got) == best, "A supersequence, but the shortest has length %d." % best)


def two_sum(one_indexed):
    def fn(inputs, got, exp):  # 1, 167: the problem promises one answer; random inputs may have several
        nums, target = inputs
        if exp is None and got is None:
            return None, None
        if not isinstance(got, list) or len(got) != 2:
            return _verdict(exp is None or got == exp, "Return two indices.")
        i, j = (x - one_indexed for x in got)
        if one_indexed and not i < j:
            return False, "index1 must be smaller than index2."
        if not (0 <= i < len(nums) and 0 <= j < len(nums)) or i == j:
            return False, "Two different valid indices are needed."
        return _verdict(nums[i] + nums[j] == target, "nums[%d] + nums[%d] isn't the target." % (i, j))
    return fn


def top_k(inputs, got, exp):  # 347: ties make several answers right
    nums, k = inputs
    cnt = Counter(nums)
    if not isinstance(got, list) or len(set(got)) != k or len(got) != k or any(x not in cnt for x in got):
        return False, "Return k different elements of nums."
    least = min(cnt[x] for x in got)
    other = max((c for x, c in cnt.items() if x not in set(got)), default=0)
    return _verdict(least >= other, "Some element left out is more frequent than one you returned.")


def k_closest(inputs, got, exp):  # 973
    points, k = inputs
    if not isinstance(got, list) or len(got) != k or Counter(map(tuple, got)) - Counter(map(tuple, points)):
        return False, "Return k of the given points."
    d = lambda p: p[0] * p[0] + p[1] * p[1]
    rest = Counter(map(tuple, points)) - Counter(map(tuple, got))
    far = max(d(p) for p in got) if got else 0
    near = min((d(p) for p in rest.elements()), default=far)
    return _verdict(far <= near, "A point left out is closer than one you returned.")


def gas_station(inputs, got, exp):  # 134
    gas, cost = inputs
    n = len(gas)

    def works(s):
        tank = 0
        for i in range(n):
            j = (s + i) % n
            tank += gas[j] - cost[j]
            if tank < 0:
                return False
        return True
    starts = [s for s in range(n) if works(s)]
    if not starts:
        return _verdict(got == -1, "No start works, so the answer is -1.")
    return _verdict(got in starts, "Starting at %s runs out of gas." % got)


CHECKERS = {
    1: two_sum(0),
    167: two_sum(1),
    347: top_k,
    973: k_closest,
    134: gas_station,
    5: longest_palindrome,
    210: course_order,
    269: alien_order,
    767: reorganize,
    1054: reorganize,
    451: by_frequency,
    905: parity,
    922: parity_ii,
    324: wiggle,
    1405: happy_string,
    984: no_aaa_bbb,
    1968: not_average,
    1980: unique_binary,
    450: bst_delete,
    701: bst_insert,
    1382: balanced_bst(lambda i: _inorder(_tree(i[0]))),
    108: balanced_bst(lambda i: i[0]),
    109: balanced_bst(lambda i: i[0]),
    1092: supersequence,
}

# Problems LeetCode judges in any order although the statement doesn't say "in any order".
ANY_ORDER = {15, 18, 22, 40, 212, 229, 417, 442, 448}
