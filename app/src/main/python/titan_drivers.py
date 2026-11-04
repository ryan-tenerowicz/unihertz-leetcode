"""Drivers for problems whose inputs or outputs aren't plain JSON values (PLAN §7, rev 23).

LeetCode runs these with hand-written judge code ("manual": true in metaData), and NeetCode
builds its versions from the same LeetCode problems, so a driver is chosen by the **LeetCode
problem number**, which both sites give us. Each driver turns the test's lines into the objects
the solution expects (a list with a cycle, a graph, an n-ary tree, an API to call…), calls the
solution, and turns the answer back into the text the site shows.

A driver returns an `Out`. `alts` are other spellings of the same answer (NeetCode and LeetCode
word some outputs differently); `ok=False` fails the case whatever the output, with `note`
saying why (e.g. a "copy" that reuses the original nodes); `check=False` marks output that can't
be compared with the example (random answers).
"""

import json
import inspect


class Out:
    def __init__(self, rendered, alts=(), note=None, ok=None, check=True):
        self.rendered = rendered
        self.alts = tuple(alts)
        self.note = note
        self.ok = ok
        self.check = check


def dump(v):
    return json.dumps(v, separators=(",", ":"), ensure_ascii=False)


# ---- classes the judge provides (the user's own class of the same name wins) ---------------

NODE_GRAPH = """
class Node:
    def __init__(self, val=0, neighbors=None):
        self.val = val
        self.neighbors = neighbors if neighbors is not None else []
"""

NODE_RANDOM = """
class Node:
    def __init__(self, x=0, next=None, random=None):
        self.val = int(x)
        self.next = next
        self.random = random
"""

NODE_NEXT = """
class Node:
    def __init__(self, val=0, left=None, right=None, next=None):
        self.val = val
        self.left = left
        self.right = right
        self.next = next
"""

NODE_NARY = """
class Node:
    def __init__(self, val=None, children=None):
        self.val = val
        self.children = children if children is not None else []
"""

NODE_MULTILEVEL = """
class Node:
    def __init__(self, val=0, prev=None, next=None, child=None):
        self.val = val
        self.prev = prev
        self.next = next
        self.child = child
"""

EMPLOYEE = """
class Employee:
    def __init__(self, id=0, importance=0, subordinates=None):
        self.id = id
        self.importance = importance
        self.subordinates = subordinates if subordinates is not None else []
"""

NESTED_INTEGER = """
class NestedInteger:
    def __init__(self, value=None):
        self._int = value
        self._list = None if value is not None else []

    def isInteger(self):
        return self._list is None

    def getInteger(self):
        return self._int if self._list is None else None

    def setInteger(self, value):
        self._int, self._list = value, None

    def add(self, elem):
        if self._list is None:
            self._int, self._list = None, []
        self._list.append(elem)

    def getList(self):
        return self._list
"""

MOUNTAIN_ARRAY = """
class MountainArray:
    def __init__(self, arr):
        self.__arr = list(arr)
        self.calls = 0

    def get(self, index):
        self.calls += 1
        return self.__arr[index]

    def length(self):
        return len(self.__arr)
"""

ITERATOR = """
class Iterator:
    def __init__(self, nums):
        self.__nums = list(nums)
        self.__i = 0

    def hasNext(self):
        return self.__i < len(self.__nums)

    def next(self):
        v = self.__nums[self.__i]
        self.__i += 1
        return v
"""


# ---- builders -------------------------------------------------------------------------------

def args(lines, n):
    if len(lines) < n:
        raise ValueError("this test needs %d lines, it has %d" % (n, len(lines)))
    return [json.loads(l) for l in lines[:n]]


def solution(ns, name):
    if "Solution" not in ns:
        raise NameError("class Solution is not defined")
    return getattr(ns["Solution"](), name)


def build_list(vals, cls):
    """A linked list of `cls` nodes, and the nodes in order."""
    nodes = [cls(v) for v in vals]
    for a, b in zip(nodes, nodes[1:]):
        a.next = b
    return (nodes[0] if nodes else None), nodes


def build_tree(vals, cls):
    """LeetCode's level-order notation (`null` for missing children) → a tree of `cls` nodes."""
    if not vals or vals[0] is None:
        return None
    root = cls(vals[0])
    queue, qi, i = [root], 0, 1
    while i < len(vals) and qi < len(queue):
        node = queue[qi]
        qi += 1
        for side in ("left", "right"):
            if i < len(vals) and vals[i] is not None:
                child = cls(vals[i])
                setattr(node, side, child)
                queue.append(child)
            i += 1
    return root


def tree_nodes(root):
    out, stack = [], [root] if root else []
    while stack:
        n = stack.pop()
        out.append(n)
        for c in (getattr(n, "right", None), getattr(n, "left", None)):
            if c is not None:
                stack.append(c)
    return out


def find_val(root, val, what):
    for n in tree_nodes(root):
        if n.val == val:
            return n
    raise ValueError("no node with value %r in the tree for %s" % (val, what))


def tree_list(root, limit=200000):
    """A tree back to level-order notation, trailing nulls dropped."""
    out, queue, qi = [], [root], 0
    while qi < len(queue) and len(out) < limit:
        n = queue[qi]
        qi += 1
        if n is None:
            out.append(None)
            continue
        out.append(n.val)
        queue.append(n.left)
        queue.append(n.right)
    while out and out[-1] is None:
        out.pop()
    return out


def list_vals(head, limit=100000):
    out, seen = [], 0
    while head is not None and seen < limit:
        out.append(head.val)
        head = head.next
        seen += 1
    if head is not None:
        raise ValueError("the returned list doesn't end (a cycle?) after %d nodes" % limit)
    return out


def nested(v, cls):
    if isinstance(v, list):
        n = cls()
        for x in v:
            n.add(nested(x, cls))
        return n
    return cls(v)


def nested_text(n, depth=0):
    if depth > 10000:
        raise ValueError("NestedInteger nests too deep")
    if n.isInteger():
        return dump(n.getInteger())
    return "[" + ",".join(nested_text(x, depth + 1) for x in (n.getList() or [])) + "]"


# ---- drivers ----------------------------------------------------------------------------------

def has_cycle(ns, lines, meta):
    vals, pos = args(lines, 2)
    head, nodes = build_list(vals, ns["ListNode"])
    if 0 <= pos < len(nodes):
        nodes[-1].next = nodes[pos]
    return Out(dump(bool(solution(ns, "hasCycle")(head))))


def detect_cycle(ns, lines, meta):
    vals, pos = args(lines, 2)
    head, nodes = build_list(vals, ns["ListNode"])
    if 0 <= pos < len(nodes):
        nodes[-1].next = nodes[pos]
    r = solution(ns, "detectCycle")(head)
    if r is None:
        return Out("no cycle", alts=["null", "-1"])
    idx = next((i for i, n in enumerate(nodes) if n is r), None)
    if idx is None:
        return Out("a node that isn't in the list", ok=False, note="Return a node of the given list.")
    return Out("tail connects to node index %d" % idx, alts=[str(idx)])


def intersection(ns, lines, meta):
    val, a, b, skip_a, skip_b = args(lines, 5)
    head_a, nodes_a = build_list(a, ns["ListNode"])
    head_b, nodes_b = build_list(b[:skip_b], ns["ListNode"])
    shared = nodes_a[skip_a] if skip_a < len(nodes_a) and val != 0 else None
    if shared is not None:
        if nodes_b:
            nodes_b[-1].next = shared
        else:
            head_b = shared
    r = solution(ns, "getIntersectionNode")(head_a, head_b)
    if r is None:
        return Out("No intersection", alts=["null"])
    note = None if r is shared else "That node has the right value but isn't the node both lists share."
    return Out("Intersected at '%s'" % r.val, alts=[dump(r.val)], ok=False if note else None, note=note)


def clone_graph(ns, lines, meta):
    (adj,) = args(lines, 1)
    Node = ns["Node"]
    nodes = [Node(i + 1) for i in range(len(adj))]
    for n, nbrs in zip(nodes, adj):
        n.neighbors = [nodes[j - 1] for j in nbrs]
    originals = {id(n) for n in nodes}
    r = solution(ns, "cloneGraph")(nodes[0] if nodes else None)
    if r is None:
        return Out("[]")
    seen, order, stack = {}, [], [r]
    while stack:
        n = stack.pop()
        if id(n) in seen:
            continue
        seen[id(n)] = n
        order.append(n)
        stack.extend(reversed(n.neighbors))
    by_val = {n.val: n for n in order}
    out = [[m.val for m in by_val[v].neighbors] if v in by_val else [] for v in range(1, max(by_val) + 1)]
    reused = [n.val for n in order if id(n) in originals]
    note = "Not a copy: node %s is from the original graph." % reused[0] if reused else None
    return Out(dump(out), ok=False if note else None, note=note)


def copy_random(ns, lines, meta):
    (pairs,) = args(lines, 1)
    head, nodes = build_list([p[0] for p in pairs], ns["Node"])
    for n, p in zip(nodes, pairs):
        n.random = nodes[p[1]] if p[1] is not None else None
    originals = {id(n) for n in nodes}
    r = solution(ns, "copyRandomList")(head)
    copied, n = [], r
    while n is not None and len(copied) <= 100000:
        copied.append(n)
        n = n.next
    index = {id(n): i for i, n in enumerate(copied)}
    out = [[n.val, index.get(id(n.random)) if n.random is not None else None] for n in copied]
    note = None
    if any(id(n) in originals for n in copied):
        note = "Not a copy: the result uses nodes of the original list."
    elif any(n.random is not None and id(n.random) not in index for n in copied):
        note = "A random pointer points outside the copied list."
    return Out(dump(out), ok=False if note else None, note=note)


def connect(ns, lines, meta):
    (vals,) = args(lines, 1)
    root = build_tree(vals, ns["Node"])
    # The first node of each level, from the tree's shape (not from the user's next pointers).
    starts, level = [], [root] if root else []
    while level:
        starts.append(level[0])
        level = [c for n in level for c in (n.left, n.right) if c is not None]
    solution(ns, "connect")(root)
    out = []
    for s in starts:
        n, steps = s, 0
        while n is not None and steps <= 100000:
            out.append(str(n.val))
            n = n.next
            steps += 1
        out.append("#")
    return Out("[" + ",".join(out) + "]")


def nary_tree(vals, Node):
    """LeetCode's n-ary notation: the root, then each node's children in order, null after each group."""
    if not vals:
        return None
    root = Node(vals[0])
    root.children = []
    queue, qi, i = [root], 0, 2
    while i < len(vals) and qi < len(queue):
        parent = queue[qi]
        qi += 1
        while i < len(vals) and vals[i] is not None:
            c = Node(vals[i])
            c.children = []
            parent.children.append(c)
            queue.append(c)
            i += 1
        i += 1
    return root


def nary(ns, lines, meta):
    (vals,) = args(lines, 1)
    name = meta.get("name") or "maxDepth"
    return Out(dump(solution(ns, name)(nary_tree(vals, ns["Node"]))))


def nested_iterator(ns, lines, meta):
    (vals,) = args(lines, 1)
    if "NestedIterator" not in ns:
        raise NameError("class NestedIterator is not defined")
    it = ns["NestedIterator"]([nested(v, ns["NestedInteger"]) for v in vals])
    out = []
    while it.hasNext():
        out.append(it.next())
        if len(out) > 1000000:
            raise ValueError("hasNext() never returned False")
    return Out(dump(out))


def mini_parser(ns, lines, meta):
    (s,) = args(lines, 1)
    return Out(nested_text(solution(ns, "deserialize")(s)))


def first_bad(ns, lines, meta):
    n, bad = args(lines, 2)
    calls = [0]

    def isBadVersion(v):
        calls[0] += 1
        return v >= bad
    ns["isBadVersion"] = isBadVersion
    r = solution(ns, "firstBadVersion")(n)
    return Out(dump(r), note="isBadVersion called %d times." % calls[0])


def guess_number(ns, lines, meta):
    n, pick = args(lines, 2)
    calls = [0]

    def guess(num):
        calls[0] += 1
        return 0 if num == pick else (-1 if num > pick else 1)
    ns["guess"] = guess
    r = solution(ns, "guessNumber")(n)
    return Out(dump(r), note="guess called %d times." % calls[0])


def mountain(ns, lines, meta):
    arr, target = args(lines, 2)
    if isinstance(arr, int):  # NeetCode stores the inputs in the function's order (target first)
        arr, target = target, arr
    m = ns["MountainArray"](arr)
    r = solution(ns, "findInMountainArray")(target, m)
    if m.calls > 100:
        return Out(dump(r), ok=False, note="MountainArray.get called %d times; the limit is 100." % m.calls)
    return Out(dump(r), note="MountainArray.get called %d times (limit 100)." % m.calls)


def delete_node(ns, lines, meta):
    vals, val = args(lines, 2)
    head, nodes = build_list(vals, ns["ListNode"])
    node = next((n for n in nodes if n.val == val), None)
    if node is None:
        raise ValueError("no node with value %r in the list" % val)
    solution(ns, "deleteNode")(node)
    return Out(dump(list_vals(head)))


def flatten_multilevel(ns, lines, meta):
    (vals,) = args(lines, 1)
    Node = ns["Node"]
    head, prev_level, i = None, [], 0
    while i < len(vals):
        offset = 0
        if head is not None:
            i += 1  # the null that ends the previous level
            while i < len(vals) and vals[i] is None:
                offset += 1
                i += 1
        level = []
        while i < len(vals) and vals[i] is not None:
            level.append(Node(vals[i]))
            i += 1
        for a, b in zip(level, level[1:]):
            a.next, b.prev = b, a
        if head is None:
            head = level[0] if level else None
        elif level and offset < len(prev_level):
            prev_level[offset].child = level[0]
        prev_level = level
    r = solution(ns, "flatten")(head)
    out, n, prev, note = [], r, None, None
    while n is not None and len(out) <= 100000:
        out.append(n.val)
        if note is None and n.prev is not prev:
            note = "The prev pointer of %s is wrong." % n.val
        if note is None and n.child is not None:
            note = "Node %s still has a child." % n.val
        prev, n = n, n.next
    return Out(dump(out), ok=False if note else None, note=note)


def lca(ns, lines, meta):
    vals, p, q = args(lines, 3)
    root = build_tree(vals, ns["TreeNode"])
    r = solution(ns, "lowestCommonAncestor")(root, find_val(root, p, "p"), find_val(root, q, "q"))
    return Out(dump(r.val if r is not None else None))


def distance_k(ns, lines, meta):
    vals, target, k = args(lines, 3)
    root = build_tree(vals, ns["TreeNode"])
    return Out(dump(solution(ns, "distanceK")(root, find_val(root, target, "target"), k)))


def target_copy(ns, lines, meta):
    vals, target = args(lines, 2)
    original = build_tree(vals, ns["TreeNode"])
    cloned = build_tree(vals, ns["TreeNode"])
    r = solution(ns, "getTargetCopy")(original, cloned, find_val(original, target, "target"))
    if r is not None and not any(n is r for n in tree_nodes(cloned)):
        return Out(dump(r.val), ok=False, note="Return the node from the cloned tree.")
    return Out(dump(r.val if r is not None else None))


def codec(ns, lines, meta):
    (vals,) = args(lines, 1)
    if "Codec" not in ns:
        raise NameError("class Codec is not defined")
    s = ns["Codec"]().serialize(build_tree(vals, ns["TreeNode"]))
    if not isinstance(s, str):
        return Out(dump(None), ok=False, note="serialize should return a str, not %s." % type(s).__name__)
    return Out(dump(tree_list(ns["Codec"]().deserialize(s))), note="Serialized to %d characters." % len(s))


def encode_decode(ns, lines, meta):
    (strs,) = args(lines, 1)
    cls = ns.get("Codec") or ns.get("Solution")
    if cls is None:
        raise NameError("class Solution is not defined")
    s = cls().encode(list(strs))
    if not isinstance(s, str):
        return Out(dump(None), ok=False, note="encode should return a str, not %s." % type(s).__name__)
    return Out(dump(cls().decode(s)))


def employees(ns, lines, meta):
    rows, ident = args(lines, 2)
    emps = [ns["Employee"](r[0], r[1], list(r[2])) for r in rows]
    return Out(dump(solution(ns, "getImportance")(emps, ident)))


def rand10(ns, lines, meta):
    (n,) = args(lines, 1)
    import random
    calls = [0]

    def rand7():
        calls[0] += 1
        return random.randint(1, 7)
    ns["rand7"] = rand7
    f = solution(ns, "rand10")
    out = [f() for _ in range(n)]
    bad = [x for x in out if not (isinstance(x, int) and 1 <= x <= 10)]
    if bad:
        return Out(dump(out), ok=False, note="rand10 returned %r, outside 1..10." % bad[0])
    return Out(dump(out), ok=True, check=False, note="All in 1..10; rand7 called %.2f times per rand10." % (calls[0] / max(n, 1)))


# LeetCode number → (driver, classes the judge provides).
DRIVERS = {
    141: (has_cycle, ""),
    142: (detect_cycle, ""),
    160: (intersection, ""),
    133: (clone_graph, NODE_GRAPH),
    138: (copy_random, NODE_RANDOM),
    116: (connect, NODE_NEXT),
    117: (connect, NODE_NEXT),
    559: (nary, NODE_NARY),
    429: (nary, NODE_NARY),
    589: (nary, NODE_NARY),
    590: (nary, NODE_NARY),
    341: (nested_iterator, NESTED_INTEGER),
    385: (mini_parser, NESTED_INTEGER),
    278: (first_bad, ""),
    374: (guess_number, ""),
    1095: (mountain, MOUNTAIN_ARRAY),
    237: (delete_node, ""),
    430: (flatten_multilevel, NODE_MULTILEVEL),
    235: (lca, ""),
    236: (lca, ""),
    863: (distance_k, ""),
    1379: (target_copy, ""),
    297: (codec, ""),
    449: (codec, ""),
    271: (encode_decode, ""),
    690: (employees, EMPLOYEE),
    470: (rand10, ""),
}

# Design problems whose answers are random: they run, but can't be compared with the example.
RANDOM_OUTPUT = {380, 381, 382, 384, 398, 478, 497, 519, 528, 710}

# Classes a design problem's constructor or methods may take (PeekingIterator takes an Iterator).
DESIGN_CLASSES = {284: ITERATOR}


# ---- design problems: arguments by declared type ---------------------------------------------

def hinted_types(f):
    """Parameter types from a method's annotations (NeetCode gives no metaData for classes)."""
    try:
        params = [p for p in inspect.signature(f).parameters.values() if p.name != "self"]
    except Exception:  # an annotation naming an undefined class (3.14 evaluates them lazily)
        return []
    out = []
    for p in params:
        a = p.annotation
        s = a if isinstance(a, str) else repr(a)
        out.append("TreeNode" if "TreeNode" in s else "ListNode" if "ListNode" in s else "Iterator" if "Iterator" in s else "")
    return out


def convert(values, types, ns):
    out = []
    for i, v in enumerate(values):
        t = types[i] if i < len(types) else ""
        if t == "TreeNode":
            v = build_tree(v, ns["TreeNode"])
        elif t == "ListNode":
            v = build_list(v, ns["ListNode"])[0]
        elif t == "Iterator" and "Iterator" in ns:
            v = ns["Iterator"](v)
        out.append(v)
    return out
