"""Tests for the local runner harness (PLAN §7). Run: scripts/test-python.sh"""
import json
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "main", "python"))
import titan_runner  # noqa: E402

TWO_SUM_META = json.dumps({"name": "twoSum", "params": [{"name": "nums", "type": "integer[]"}, {"name": "target", "type": "integer"}], "return": {"type": "integer[]"}})
TWO_SUM = """
class Solution:
    def twoSum(self, nums: List[int], target: int) -> List[int]:
        seen = {}
        for i, n in enumerate(nums):
            if target - n in seen:
                return [seen[target - n], i]
            seen[n] = i
"""


def run(code, meta, inputs, expected=None, mode="exact"):
    return json.loads(titan_runner.run(json.dumps({"code": code, "metaData": meta, "inputs": inputs, "expected": expected or [], "mode": mode})))


class RunnerTest(unittest.TestCase):
    def test_two_sum(self):
        r = run(TWO_SUM, TWO_SUM_META, ["[2,7,11,15]\n9", "[3,2,4]\n6"], ["[0,1]", "[1, 2]"])
        self.assertEqual(r["status"], "ok")
        self.assertEqual([c["output"] for c in r["cases"]], ["[0,1]", "[1,2]"])
        self.assertEqual([c["ok"] for c in r["cases"]], [True, True])

    def test_wrong_answer_and_stdout(self):
        code = TWO_SUM.replace("return [seen[target - n], i]", "print('dbg', i)\n                return [0, 0]")
        r = run(code, TWO_SUM_META, ["[3,2,4]\n6"], ["[1,2]"])
        self.assertFalse(r["cases"][0]["ok"])
        self.assertEqual(r["cases"][0]["stdout"], "dbg 2\n")

    def test_runtime_error_traceback_has_user_line(self):
        code = "class Solution:\n    def twoSum(self, nums, target):\n        return nums[99]\n"
        r = run(code, TWO_SUM_META, ["[1]\n1"])
        err = r["cases"][0]["error"]
        self.assertIn('File "solution.py", line 3', err)
        self.assertIn("IndexError", err)

    def test_syntax_error(self):
        r = run("class Solution:\n    def f(self)\n        pass\n", TWO_SUM_META, ["[1]\n1"])
        self.assertEqual(r["status"], "compile_error")
        self.assertIn("line 2", r["error"])

    def test_prelude_names(self):
        code = "class Solution:\n    def twoSum(self, nums, target):\n        return [len(SortedList(nums)), int(inf > 0), len(ascii_lowercase), len(Counter(nums)), heappop([target])]\n"
        r = run(code, TWO_SUM_META, ["[1,1]\n7"])
        self.assertEqual(r["cases"][0]["output"], "[2,1,26,1,7]")

    def test_any_order(self):
        meta = json.dumps({"name": "groupAnagrams", "params": [{"name": "strs", "type": "string[]"}], "return": {"type": "list<list<string>>"}})
        code = "class Solution:\n    def groupAnagrams(self, strs):\n        d = defaultdict(list)\n        for s in strs: d[''.join(sorted(s))].append(s)\n        return list(d.values())\n"
        r = run(code, meta, ['["eat","tea","tan","ate","nat","bat"]'], ['[["bat"],["nat","tan"],["ate","eat","tea"]]'], mode="any_order")
        self.assertTrue(r["cases"][0]["ok"])
        r = run(code, meta, ['["eat","tea","tan","ate","nat","bat"]'], ['[["bat"],["nat","tan"],["ate","eat","tea"]]'])
        self.assertFalse(r["cases"][0]["ok"])

    def test_linked_list_and_tree(self):
        meta = json.dumps({"name": "reverseList", "params": [{"name": "head", "type": "ListNode"}], "return": {"type": "ListNode"}})
        code = "class Solution:\n    def reverseList(self, head):\n        prev = None\n        while head:\n            head.next, prev, head = prev, head, head.next\n        return prev\n"
        self.assertEqual(run(code, meta, ["[1,2,3]"])["cases"][0]["output"], "[3,2,1]")
        meta = json.dumps({"name": "invertTree", "params": [{"name": "root", "type": "TreeNode"}], "return": {"type": "TreeNode"}})
        code = "class Solution:\n    def invertTree(self, root):\n        if root: root.left, root.right = self.invertTree(root.right), self.invertTree(root.left)\n        return root\n"
        r = run(code, meta, ["[4,2,7,1,3,6,9]", "[2,null,3]"], ["[4,7,2,9,6,3,1]", "[2,3]"])
        self.assertEqual([c["ok"] for c in r["cases"]], [True, True])

    def test_float_tolerance_and_void(self):
        meta = json.dumps({"name": "avg", "params": [{"name": "a", "type": "integer[]"}], "return": {"type": "double"}})
        code = "class Solution:\n    def avg(self, a):\n        return sum(a) / len(a)\n"
        r = run(code, meta, ["[1,2]"], ["1.50000"])
        self.assertEqual(r["cases"][0]["output"], "1.50000")
        self.assertTrue(r["cases"][0]["ok"])
        meta = json.dumps({"name": "sortColors", "params": [{"name": "nums", "type": "integer[]"}], "return": {"type": "void"}})
        code = "class Solution:\n    def sortColors(self, nums):\n        nums.sort()\n"
        r = run(code, meta, ["[2,0,1]"], ["[0,1,2]"])
        self.assertTrue(r["cases"][0]["ok"])

    def test_k_first(self):
        meta = json.dumps({"name": "removeElement", "params": [{"name": "nums", "type": "integer[]"}, {"name": "val", "type": "integer"}], "return": {"type": "integer"}})
        code = "class Solution:\n    def removeElement(self, nums, val):\n        nums[:] = [x for x in nums if x != val]\n        return len(nums)\n"
        r = run(code, meta, ["[3,2,2,3]\n3"], ["2, nums = [2,2,_,_]"], mode="k_first")
        self.assertTrue(r["cases"][0]["ok"])

    def test_design(self):
        meta = json.dumps({"classname": "Counter2", "constructor": {"params": []}, "methods": []})
        code = "class Counter2:\n    def __init__(self): self.n = 0\n    def inc(self, k): self.n += k; return self.n\n"
        r = run(code, meta, ['["Counter2","inc","inc"]\n[[],[2],[3]]'], ["[null,2,5]"])
        self.assertEqual(r["cases"][0]["output"], "[null,2,5]")
        self.assertTrue(r["cases"][0]["ok"])

    def test_deep_recursion(self):
        meta = json.dumps({"name": "depth", "params": [{"name": "n", "type": "integer"}], "return": {"type": "integer"}})
        code = "class Solution:\n    def depth(self, n):\n        return 0 if n == 0 else 1 + self.depth(n - 1)\n"
        self.assertEqual(run(code, meta, ["50000"])["cases"][0]["output"], "50000")

    def test_unsupported_type(self):
        meta = json.dumps({"name": "cloneGraph", "params": [{"name": "node", "type": "Node"}], "return": {"type": "Node"}})
        r = run("class Solution:\n    def cloneGraph(self, node): return node\n", meta, ["[[2],[1]]"])
        self.assertEqual(r["status"], "unsupported")

    # ---- special types (titan_drivers, rev 23) ----

    def drive(self, number, code, inputs, expected=None, meta=None, mode="exact"):
        return json.loads(titan_runner.run(json.dumps({"code": code, "metaData": json.dumps(meta or {}), "inputs": inputs,
                                                       "expected": expected or [], "mode": mode, "number": number})))

    def assertCases(self, r, oks):
        self.assertEqual(r["status"], "ok", r.get("error"))
        self.assertEqual([c["ok"] for c in r["cases"]], oks, [(c["output"], c["error"], c.get("note")) for c in r["cases"]])

    def test_cycle(self):
        code = "class Solution:\n    def hasCycle(self, head):\n        s = f = head\n        while f and f.next:\n            s, f = s.next, f.next.next\n            if s is f: return True\n        return False\n"
        self.assertCases(self.drive(141, code, ["[3,2,0,-4]\n1", "[1]\n-1", "[1,2,3,4]\n1"], ["true", "false", "true"]), [True, True, True])
        code = "class Solution:\n    def detectCycle(self, head):\n        seen = set()\n        while head and id(head) not in seen:\n            seen.add(id(head)); head = head.next\n        return head\n"
        r = self.drive(142, code, ["[3,2,0,-4]\n1", "[1]\n-1"], ["tail connects to node index 1", "no cycle"])
        self.assertCases(r, [True, True])

    def test_intersection_both_spellings(self):
        code = "class Solution:\n    def getIntersectionNode(self, a, b):\n        x, y = a, b\n        while x is not y:\n            x = x.next if x else b\n            y = y.next if y else a\n        return x\n"
        r = self.drive(160, code, ["8\n[4,1,8,4,5]\n[5,6,1,8,4,5]\n2\n3", "0\n[2,6,4]\n[1,5]\n3\n2", "8\n[4,1,8,4,5]\n[5,6,1,8,4,5]\n2\n3"],
                       ["Intersected at '8'", "No intersection", "8"])
        self.assertCases(r, [True, True, True])
        # The shared node, reached through B.
        code = "class Solution:\n    def getIntersectionNode(self, a, b):\n        return b.next.next.next\n"
        self.assertCases(self.drive(160, code, ["8\n[4,1,8,4,5]\n[5,6,1,8,4,5]\n2\n3"], ["Intersected at '8'"]), [True])
        # Right value, wrong node.
        r = self.drive(160, "class Solution:\n    def getIntersectionNode(self, a, b):\n        return ListNode(8)\n", ["8\n[4,1,8,4,5]\n[5,6,1,8,4,5]\n2\n3"], ["8"])
        self.assertCases(r, [False])

    def test_clone_graph(self):
        code = ("class Solution:\n    def cloneGraph(self, node):\n        m = {}\n        def c(n):\n            if n in m: return m[n]\n"
                "            m[n] = Node(n.val)\n            m[n].neighbors = [c(x) for x in n.neighbors]\n            return m[n]\n        return c(node) if node else None\n")
        r = self.drive(133, code, ["[[2,4],[1,3],[2,4],[1,3]]", "[[]]", "[]"], ["[[2,4],[1,3],[2,4],[1,3]]", "[[]]", "[]"])
        self.assertCases(r, [True, True, True])
        r = self.drive(133, "class Solution:\n    def cloneGraph(self, node): return node\n", ["[[2],[1]]"], ["[[2],[1]]"])
        self.assertCases(r, [False])
        self.assertIn("Not a copy", r["cases"][0]["note"])

    def test_clone_graph_users_own_node_class(self):
        code = ("class Node:\n    def __init__(self, val = 0, neighbors = None):\n        self.val = val\n        self.neighbors = neighbors if neighbors is not None else []\n\n"
                "class Solution:\n    def cloneGraph(self, node):\n        m = {}\n        def c(n):\n            if n in m: return m[n]\n"
                "            m[n] = Node(n.val)\n            m[n].neighbors = [c(x) for x in n.neighbors]\n            return m[n]\n        return c(node) if node else None\n")
        self.assertCases(self.drive(133, code, ["[[2],[1,3],[2]]"], ["[[2],[1,3],[2]]"]), [True])

    def test_copy_random_list(self):
        code = ("class Solution:\n    def copyRandomList(self, head):\n        m = {None: None}\n        n = head\n        while n: m[n] = Node(n.val); n = n.next\n"
                "        n = head\n        while n: m[n].next = m[n.next]; m[n].random = m[n.random]; n = n.next\n        return m[head]\n")
        r = self.drive(138, code, ["[[7,null],[13,0],[11,4],[10,2],[1,0]]", "[]"], ["[[7,null],[13,0],[11,4],[10,2],[1,0]]", "[]"])
        self.assertCases(r, [True, True])
        r = self.drive(138, "class Solution:\n    def copyRandomList(self, head): return head\n", ["[[1,1],[2,1]]"], ["[[1,1],[2,1]]"])
        self.assertCases(r, [False])

    def test_connect_next_pointers(self):
        code = ("class Solution:\n    def connect(self, root):\n        level = [root] if root else []\n        while level:\n"
                "            for a, b in zip(level, level[1:]): a.next = b\n            level = [c for n in level for c in (n.left, n.right) if c]\n        return root\n")
        r = self.drive(117, code, ["[1,2,3,4,5,null,7]", "[]"], ["[1,#,2,3,#,4,5,7,#]", "[]"])
        self.assertCases(r, [True, True])
        self.assertEqual(r["cases"][0]["output"], "[1,#,2,3,#,4,5,7,#]")

    def test_nary_tree(self):
        code = "class Solution:\n    def maxDepth(self, root):\n        return 1 + max((self.maxDepth(c) for c in root.children), default=0) if root else 0\n"
        r = self.drive(559, code, ["[1,null,3,2,4,null,5,6]", "[1,null,2,3,4,5,null,null,6,7,null,8,null,9,10,null,null,11,null,12,null,13,null,null,14]"], ["3", "5"],
                       meta={"name": "maxDepth"})
        self.assertCases(r, [True, True])
        code = "class Solution:\n    def postorder(self, root):\n        if not root: return []\n        return [x for c in root.children for x in self.postorder(c)] + [root.val]\n"
        self.assertCases(self.drive(590, code, ["[1,null,3,2,4,null,5,6]"], ["[5,6,3,2,4,1]"], meta={"name": "postorder"}), [True])

    def test_nested_integer(self):
        code = ("class NestedIterator:\n    def __init__(self, l):\n        self.out = []\n        def go(xs):\n            for x in xs:\n"
                "                if x.isInteger(): self.out.append(x.getInteger())\n                else: go(x.getList())\n        go(l)\n        self.i = 0\n"
                "    def next(self):\n        self.i += 1\n        return self.out[self.i - 1]\n    def hasNext(self): return self.i < len(self.out)\n")
        r = self.drive(341, code, ["[[1,1],2,[1,1]]", "[[2,[]],[-1,[5]],3]", "[[],[[]],[]]"], ["[1,1,2,1,1]", "[2,-1,5,3]", "[]"])
        self.assertCases(r, [True, True, True])
        code = ("class Solution:\n    def deserialize(self, s):\n        def b(v):\n            if isinstance(v, int): return NestedInteger(v)\n"
                "            n = NestedInteger()\n            for x in v: n.add(b(x))\n            return n\n        return b(eval(s))\n")
        self.assertCases(self.drive(385, code, ['"324"', '"[123,[456,[789]]]"'], ["324", "[123,[456,[789]]]"]), [True, True])

    def test_interactive_apis(self):
        code = ("class Solution:\n    def firstBadVersion(self, n):\n        lo, hi = 1, n\n        while lo < hi:\n            m = (lo + hi) // 2\n"
                "            if isBadVersion(m): hi = m\n            else: lo = m + 1\n        return lo\n")
        r = self.drive(278, code, ["5\n4", "1\n1"], ["4", "1"])
        self.assertCases(r, [True, True])
        self.assertIn("called", r["cases"][0]["note"])
        code = ("class Solution:\n    def guessNumber(self, n):\n        lo, hi = 1, n\n        while True:\n            m = (lo + hi) // 2\n            g = guess(m)\n"
                "            if g == 0: return m\n            if g < 0: hi = m - 1\n            else: lo = m + 1\n")
        self.assertCases(self.drive(374, code, ["10\n6", "1\n1", "2\n1"], ["6", "1", "1"]), [True, True, True])
        linear = ("class Solution:\n    def findInMountainArray(self, target, m):\n        for i in range(m.length()):\n"
                  "            if m.get(i) == target: return i\n        return -1\n")
        self.assertCases(self.drive(1095, linear, ["[1,2,3,4,5,3,1]\n3", "[0,1,2,4,2,1]\n3"], ["2", "-1"]), [True, True])
        self.assertCases(self.drive(1095, linear, ["2\n[2,4,5,2,1]"], ["0"]), [True])
        r = self.drive(1095, linear, [json.dumps(list(range(200)) + [0]) + "\n-5"], ["-1"])
        self.assertCases(r, [False])
        self.assertIn("limit is 100", r["cases"][0]["note"])

    def test_delete_node_and_multilevel(self):
        code = "class Solution:\n    def deleteNode(self, node):\n        node.val, node.next = node.next.val, node.next.next\n"
        self.assertCases(self.drive(237, code, ["[4,5,1,9]\n5", "[4,5,1,9]\n1"], ["[4,1,9]", "[4,5,9]"]), [True, True])
        code = ("class Solution:\n    def flatten(self, head):\n        def go(n):\n            last = None\n            while n:\n                nxt = n.next\n"
                "                if n.child:\n                    ct = go(n.child)\n                    n.next, n.child.prev = n.child, n\n                    ct.next = nxt\n"
                "                    if nxt: nxt.prev = ct\n                    n.child = None\n                    last = ct\n                else: last = n\n                n = nxt\n            return last\n"
                "        go(head)\n        return head\n")
        r = self.drive(430, code, ["[1,2,3,4,5,6,null,null,null,7,8,9,10,null,null,11,12]", "[1,2,null,3]", "[]"],
                       ["[1,2,3,7,8,11,12,9,10,4,5,6]", "[1,3,2]", "[]"])
        self.assertCases(r, [True, True, True])
        r = self.drive(430, "class Solution:\n    def flatten(self, head): return head\n", ["[1,2,null,3]"], ["[1,3,2]"])
        self.assertCases(r, [False])

    def test_tree_node_arguments(self):
        code = ("class Solution:\n    def lowestCommonAncestor(self, root, p, q):\n        if root in (None, p, q): return root\n"
                "        l, r = self.lowestCommonAncestor(root.left, p, q), self.lowestCommonAncestor(root.right, p, q)\n        return root if l and r else l or r\n")
        self.assertCases(self.drive(236, code, ["[3,5,1,6,2,0,8,null,null,7,4]\n5\n1", "[3,5,1,6,2,0,8,null,null,7,4]\n5\n4"], ["3", "5"]), [True, True])
        code = ("class Solution:\n    def distanceK(self, root, target, k):\n        par = {}\n        st = [root]\n        while st:\n            n = st.pop()\n"
                "            for c in (n.left, n.right):\n                if c: par[c] = n; st.append(c)\n        seen, lvl = {target}, [target]\n"
                "        for _ in range(k):\n            nx = []\n            for n in lvl:\n                for m in (n.left, n.right, par.get(n)):\n"
                "                    if m and m not in seen: seen.add(m); nx.append(m)\n            lvl = nx\n        return [n.val for n in lvl]\n")
        self.assertCases(self.drive(863, code, ["[3,5,1,6,2,0,8,null,null,7,4]\n5\n2"], ["[7,4,1]"], mode="any_order"), [True])

    def test_codec_and_encode_decode(self):
        code = ("class Codec:\n    def serialize(self, root):\n        return json.dumps(self.l(root))\n    def l(self, n):\n        return [n.val, self.l(n.left), self.l(n.right)] if n else None\n"
                "    def deserialize(self, s):\n        def b(v):\n            if v is None: return None\n            t = TreeNode(v[0]); t.left = b(v[1]); t.right = b(v[2]); return t\n"
                "        return b(json.loads(s))\nimport json\n")
        self.assertCases(self.drive(297, code, ["[1,2,3,null,null,4,5]", "[]"], ["[1,2,3,null,null,4,5]", "[]"]), [True, True])
        code = ("class Solution:\n    def encode(self, strs):\n        return ''.join('%d#%s' % (len(s), s) for s in strs)\n"
                "    def decode(self, s):\n        out, i = [], 0\n        while i < len(s):\n            j = s.index('#', i)\n            n = int(s[i:j])\n"
                "            out.append(s[j + 1:j + 1 + n])\n            i = j + 1 + n\n        return out\n")
        self.assertCases(self.drive(271, code, ['["Hello","World"]', '[""]'], ['["Hello","World"]', '[""]'], meta={"classname": "Solution"}), [True, True])

    def test_employees_and_rand10(self):
        code = ("class Solution:\n    def getImportance(self, es, id):\n        m = {e.id: e for e in es}\n"
                "        def f(i): return m[i].importance + sum(f(s) for s in m[i].subordinates)\n        return f(id)\n")
        self.assertCases(self.drive(690, code, ["[[1,5,[2,3]],[2,3,[]],[3,3,[]]]\n1", "[[1,2,[5]],[5,-3,[]]]\n5"], ["11", "-3"]), [True, True])
        code = ("class Solution:\n    def rand10(self):\n        while True:\n            x = (rand7() - 1) * 7 + rand7()\n            if x <= 40: return (x - 1) % 10 + 1\n")
        r = self.drive(470, code, ["3"], ["[3,8,10]"])
        self.assertCases(r, [True])
        self.assertTrue(r["cases"][0]["unchecked"])
        r = self.drive(470, "class Solution:\n    def rand10(self): return 11\n", ["2"], ["[2,8]"])
        self.assertCases(r, [False])

    def test_design_with_tree_and_list_arguments(self):
        code = ("class BSTIterator:\n    def __init__(self, root):\n        self.st = []\n        self.push(root)\n    def push(self, n):\n"
                "        while n: self.st.append(n); n = n.left\n    def next(self):\n        n = self.st.pop(); self.push(n.right); return n.val\n"
                "    def hasNext(self): return bool(self.st)\n")
        meta = {"classname": "BSTIterator", "constructor": {"params": [{"type": "TreeNode", "name": "root"}]},
                "methods": [{"params": [], "name": "next"}, {"params": [], "name": "hasNext"}]}
        case = '["BSTIterator","next","next","hasNext","next","hasNext","next","hasNext","next","hasNext"]\n[[[7,3,15,null,null,9,20]],[],[],[],[],[],[],[],[],[]]'
        want = "[null,3,7,true,9,true,15,true,20,false]"
        self.assertCases(self.drive(173, code, [case], [want], meta=meta), [True])
        # NeetCode: no constructor types in metaData; read from the annotation.
        hinted = code.replace("def __init__(self, root):", "def __init__(self, root: Optional[TreeNode]):")
        self.assertCases(self.drive(173, hinted, [case], [want], meta={"classname": "BSTIterator"}), [True])
        code = "import random\nclass Solution:\n    def __init__(self, head):\n        self.v = []\n        while head: self.v.append(head.val); head = head.next\n    def getRandom(self): return random.choice(self.v)\n"
        meta = {"classname": "Solution", "constructor": {"params": [{"type": "ListNode", "name": "head"}]}, "methods": [{"params": [], "name": "getRandom"}]}
        r = self.drive(382, code, ['["Solution","getRandom","getRandom"]\n[[[1,2,3]],[],[]]'], ["[null,1,3]"], meta=meta)
        self.assertEqual(r["cases"][0]["error"], None)
        self.assertTrue(r["cases"][0]["unchecked"])

    # ---- limits (titan_limits, rev 24) ----

    def limited(self, code, inputs, **extra):
        req = {"code": code, "metaData": TWO_SUM_META, "inputs": inputs, "expected": [], "mode": "exact"}
        req.update(extra)
        return json.loads(titan_runner.run(json.dumps(req)))

    def test_time_limit_stops_the_case_at_its_line_and_runs_the_rest(self):
        code = "class Solution:\n    def twoSum(self, nums, target):\n        while target == 1:\n            nums.append(1)\n        return [0, 1]\n"
        r = self.limited(code, ["[1]\n1", "[1]\n2"], caseLimitMs=300)
        a, b = r["cases"]
        self.assertEqual(a["limit"], "time")
        self.assertIn("Time Limit Exceeded: still running after 0.3 s", a["error"])
        self.assertRegex(a["error"], r'line [34]')
        self.assertTrue("while target == 1" in a["error"] or "nums.append(1)" in a["error"], a["error"])
        self.assertEqual(b["output"], "[0,1]")
        self.assertNotIn("restart", r)

    def test_except_exception_cant_swallow_the_stop(self):
        code = "class Solution:\n    def twoSum(self, nums, target):\n        try:\n            while True: pass\n        except Exception:\n            return [9]\n"
        r = self.limited(code, ["[1]\n1"], caseLimitMs=200)
        self.assertEqual(r["cases"][0]["limit"], "time")

    def test_uninterruptible_call_gives_up_and_asks_for_a_restart(self):
        code = "import time\nclass Solution:\n    def twoSum(self, nums, target):\n        time.sleep(30)\n"
        r = self.limited(code, ["[1]\n1", "[2]\n2"], caseLimitMs=100)
        self.assertTrue(r["restart"])
        self.assertIn("can't be interrupted", r["cases"][0]["error"])
        self.assertIn("Not run", r["cases"][1]["error"])

    def test_memory_limit(self):
        code = "class Solution:\n    def twoSum(self, nums, target):\n        a = []\n        while True:\n            a.append([0] * 10000)\n"
        r = self.limited(code, ["[1]\n1"], memLimitMb=60, caseLimitMs=10000)
        self.assertEqual(r["cases"][0]["limit"], "memory")
        self.assertIn("Memory Limit Exceeded", r["cases"][0]["error"])

    def test_output_limit_and_capped_stdout(self):
        code = "class Solution:\n    def twoSum(self, nums, target):\n        for i in range(target):\n            print('x' * 99)\n        return []\n"
        r = self.limited(code, ["[1]\n300", "[1]\n20000"])
        small, big = r["cases"]
        self.assertIn("more characters not shown", small["stdout"])
        self.assertLessEqual(len(small["stdout"]), 20100)
        self.assertEqual(big["limit"], "output")
        self.assertIn("line 4", big["error"])

    def test_progress_file(self):
        import tempfile
        path = os.path.join(tempfile.mkdtemp(), "progress.json")
        self.limited(TWO_SUM, ["[2,7]\n9", "[3,3]\n6"], progressPath=path)
        with open(path) as f:
            self.assertEqual([c["output"] for c in json.load(f)["cases"]], ["[0,1]", "[0,1]"])

    def test_traceback_shows_source_lines(self):
        r = self.limited("class Solution:\n    def twoSum(self, nums, target):\n        return nums[99]\n", ["[1]\n1"])
        self.assertIn("return nums[99]", r["cases"][0]["error"])

    # ---- checkers (titan_checkers, rev 24) ----

    def test_checker_accepts_any_valid_answer(self):
        meta = json.dumps({"name": "longestPalindrome", "params": [{"name": "s", "type": "string"}], "return": {"type": "string"}})
        aba = "class Solution:\n    def longestPalindrome(self, s): return 'aba'\n"
        r = self.drive(5, aba, ['"babad"', '"babad"'], ['"bab"'], meta=json.loads(meta))
        self.assertCases(r, [True, True])  # the second has no expected output: the checker decides
        bb = "class Solution:\n    def longestPalindrome(self, s): return 'b'\n"
        r = self.drive(5, bb, ['"babad"'], ['"bab"'], meta=json.loads(meta))
        self.assertCases(r, [False])
        self.assertIn("length 3", r["cases"][0]["note"])

    def test_course_schedule_and_bst_checkers(self):
        meta = {"name": "findOrder", "params": [{"name": "n", "type": "integer"}, {"name": "p", "type": "integer[][]"}], "return": {"type": "integer[]"}}
        code = "class Solution:\n    def findOrder(self, n, p): return [0, 2, 1, 3]\n"
        self.assertCases(self.drive(210, code, ["4\n[[1,0],[2,0],[3,1],[3,2]]"], ["[0,1,2,3]"], meta=meta), [True])
        code = "class Solution:\n    def findOrder(self, n, p): return [3, 2, 1, 0]\n"
        self.assertCases(self.drive(210, code, ["4\n[[1,0],[2,0],[3,1],[3,2]]"], ["[0,1,2,3]"], meta=meta), [False])
        meta = {"name": "insertIntoBST", "params": [{"name": "root", "type": "TreeNode"}, {"name": "val", "type": "integer"}], "return": {"type": "TreeNode"}}
        code = ("class Solution:\n    def insertIntoBST(self, root, val):\n        n = TreeNode(val)\n        n.left = root\n        return n\n")
        self.assertCases(self.drive(701, code, ["[4,2,7,1,3]\n5"], ["[4,2,7,1,3,5]"], meta=meta), [False])  # 5 above 7: not a BST
        code = ("class Solution:\n    def insertIntoBST(self, root, val):\n        if not root: return TreeNode(val)\n"
                "        if val < root.val: root.left = self.insertIntoBST(root.left, val)\n        else: root.right = self.insertIntoBST(root.right, val)\n        return root\n")
        self.assertCases(self.drive(701, code, ["[4,2,7,1,3]\n5", "[40,20,60,10,30,50,70]\n25"], ["[4,2,7,1,3,5]"], meta=meta), [True, True])

    def test_any_order_override(self):
        meta = {"name": "threeSum", "params": [{"name": "nums", "type": "integer[]"}], "return": {"type": "list<list<integer>>"}}
        code = "class Solution:\n    def threeSum(self, nums): return [[0,1,-1],[-1,-1,2]]\n"
        self.assertCases(self.drive(15, code, ["[-1,0,1,2,-1,-4]"], ["[[-1,-1,2],[-1,0,1]]"], meta=meta), [True])

    # ---- stress tests (titan_stress, rev 24) ----

    def stress(self, code, ref, meta, constraints, number=0, inputs=(), expected=(), mode="exact", seconds=3):
        req = {"code": code, "metaData": json.dumps(meta), "inputs": list(inputs), "expected": list(expected), "mode": "stress",
               "number": number, "references": [{"label": "1. Reference", "code": ref}], "constraints": constraints, "seconds": seconds,
               "checkMode": mode}
        r = json.loads(titan_runner.run(json.dumps(req)))
        self.assertEqual(r["status"], "ok", r.get("error"))
        return r["stress"]

    TWO_SUM_CONS = "Constraints:\n2 <= nums.length <= 10^4\n-10^9 <= nums[i] <= 10^9\n-10^9 <= target <= 10^9\nOnly one valid answer exists."

    def test_stress_finds_a_small_counterexample(self):
        buggy = ("class Solution:\n    def twoSum(self, nums, target):\n        for i in range(len(nums)):\n            for j in range(len(nums)):\n"
                 "                if nums[i] + nums[j] == target: return [i, j]\n")
        st = self.stress(buggy, TWO_SUM, json.loads(TWO_SUM_META), self.TWO_SUM_CONS, number=1, inputs=["[2,7,11,15]\n9"], expected=["[0,1]"])
        m = st["mismatch"]
        self.assertIsNotNone(m)
        nums, target = [json.loads(x) for x in m["input"].split("\n")]
        self.assertTrue(2 <= len(nums) <= 8, m)
        self.assertTrue(all(-10 <= x <= 10 for x in nums))
        self.assertIn("nums: integer[], length 2…10^4, values -10^9…10^9", st["assumptions"])
        self.assertTrue(any("exactly one answer" in w for w in st["warnings"]))

    def test_stress_passes_and_times_a_big_input(self):
        st = self.stress(TWO_SUM, TWO_SUM, json.loads(TWO_SUM_META), self.TWO_SUM_CONS, number=1)
        self.assertIsNone(st["mismatch"])
        self.assertGreater(st["tried"], 50)
        self.assertEqual(st["big"]["size"], "nums: 10000")
        self.assertIsNotNone(st["big"]["ms"])

    def test_stress_reports_a_slow_solution(self):
        slow = ("class Solution:\n    def twoSum(self, nums, target):\n        for i in range(len(nums)):\n            for j in range(i + 1, len(nums)):\n"
                "                if nums[i] + nums[j] == target: return [i, j]\n        for _ in range(10**8): pass\n")
        req_limit = 400
        req = {"code": slow, "metaData": TWO_SUM_META, "inputs": [], "expected": [], "mode": "stress", "number": 1, "caseLimitMs": req_limit,
               "references": [{"label": "r", "code": TWO_SUM}], "constraints": self.TWO_SUM_CONS, "seconds": 1}
        st = json.loads(titan_runner.run(json.dumps(req)))["stress"]
        # Small inputs already take too long: reported as a difference with a time limit.
        self.assertIn("Time Limit Exceeded", (st["mismatch"] or {}).get("error") or (st["big"] or {}).get("error") or "")

    def test_stress_strings_grids_trees_and_checkers(self):
        meta = {"name": "groupAnagrams", "params": [{"name": "strs", "type": "string[]"}], "return": {"type": "list<list<string>>"}}
        ref = "class Solution:\n    def groupAnagrams(self, strs):\n        d = defaultdict(list)\n        for s in strs: d[''.join(sorted(s))].append(s)\n        return list(d.values())\n"
        cons = "Constraints:\n1 <= strs.length <= 10^4\n0 <= strs[i].length <= 100\nstrs[i] consists of lowercase English letters."
        req = {"code": ref.replace("sorted(s)", "s"), "metaData": json.dumps(meta), "inputs": [], "expected": [], "mode": "stress",
               "references": [{"label": "r", "code": ref}], "constraints": cons, "seconds": 2}
        r = json.loads(titan_runner.run(json.dumps(req)))
        self.assertIsNotNone(r["stress"]["mismatch"])

        meta = {"name": "numIslands", "params": [{"name": "grid", "type": "character[][]"}], "return": {"type": "integer"}}
        ref = ("class Solution:\n    def numIslands(self, grid):\n        m, n, c = len(grid), len(grid[0]), 0\n        def f(i, j):\n"
               "            if 0 <= i < m and 0 <= j < n and grid[i][j] == '1':\n                grid[i][j] = '0'\n                f(i+1,j); f(i-1,j); f(i,j+1); f(i,j-1)\n"
               "        for i in range(m):\n            for j in range(n):\n                if grid[i][j] == '1': c += 1; f(i, j)\n        return c\n")
        cons = "Constraints:\nm == grid.length\nn == grid[i].length\n1 <= m, n <= 300\ngrid[i][j] is '0' or '1'."
        st = self.stress(ref, ref, meta, cons, seconds=2)
        self.assertIsNone(st["mismatch"])
        self.assertEqual(st["big"]["size"], "grid: 300")
        st = self.stress("class Solution:\n    def numIslands(self, grid): return sum(r.count('1') for r in grid)\n", ref, meta, cons, seconds=2)
        self.assertIsNotNone(st["mismatch"])

        meta = {"name": "maxDepth", "params": [{"name": "root", "type": "TreeNode"}], "return": {"type": "integer"}}
        ref = "class Solution:\n    def maxDepth(self, root):\n        return 1 + max(self.maxDepth(root.left), self.maxDepth(root.right)) if root else 0\n"
        cons = "Constraints:\nThe number of nodes in the tree is in the range [0, 10^4].\n-100 <= Node.val <= 100"
        st = self.stress("class Solution:\n    def maxDepth(self, root):\n        return 1 + self.maxDepth(root.left) if root else 0\n", ref, meta, cons, seconds=2)
        self.assertIsNotNone(st["mismatch"])

        # Course Schedule II: a different valid order is not a difference (the checker decides).
        meta = {"name": "findOrder", "params": [{"name": "numCourses", "type": "integer"}, {"name": "prerequisites", "type": "integer[][]"}], "return": {"type": "integer[]"}}
        ref = ("class Solution:\n    def findOrder(self, n, pre):\n        g = [[] for _ in range(n)]\n        d = [0] * n\n        for a, b in pre: g[b].append(a); d[a] += 1\n"
               "        q = [i for i in range(n) if d[i] == 0]\n        out = []\n        while q:\n            x = q.pop(0); out.append(x)\n"
               "            for y in g[x]:\n                d[y] -= 1\n                if d[y] == 0: q.append(y)\n        return out if len(out) == n else []\n")
        mine = ref.replace("q.pop(0)", "q.pop()")
        cons = ("Constraints:\n1 <= numCourses <= 2000\n0 <= prerequisites.length <= numCourses * (numCourses - 1)\nprerequisites[i].length == 2\n"
                "0 <= a_i, b_i < numCourses\na_i != b_i\nAll the pairs [a_i, b_i] are distinct.")
        st = self.stress(mine, ref, meta, cons, number=210, seconds=2)
        self.assertIsNone(st["mismatch"], st)
        self.assertGreater(st["tried"], 20)

    def test_stress_without_a_reference_or_with_unknown_types(self):
        req = {"code": TWO_SUM, "metaData": TWO_SUM_META, "inputs": [], "expected": [], "mode": "stress", "references": [], "constraints": ""}
        r = json.loads(titan_runner.run(json.dumps(req)))
        self.assertEqual(r["status"], "unsupported")
        self.assertIn("reference", r["error"])

    # ---- stdin/stdout (Codeforces, rev 13) ----

    def test_stdin_program(self):
        code = "import sys\ninput = sys.stdin.readline\n\ndef main():\n    n = int(input())\n    print('YES' if n % 2 == 0 and n > 2 else 'NO')\n\nmain()\n"
        r = run(code, "", ["8\n", "5"], ["YES\n", "NO"], mode="stdin")
        self.assertEqual(r["status"], "ok")
        self.assertEqual([c["output"] for c in r["cases"]], ["YES\n", "NO\n"])
        self.assertEqual([c["ok"] for c in r["cases"]], [True, True])

    def test_stdin_fast_input_idioms(self):
        code = "import sys\ndata = sys.stdin.buffer.read().split()\nprint(sum(map(int, data)))\n"
        self.assertEqual(run(code, "", ["1 2\n3"], ["6"], mode="stdin")["cases"][0]["ok"], True)
        code = "a = open(0).read().split()\nprint(len(a))\nexit()\nprint('never')\n"
        r = run(code, "", ["x y z"], ["3"], mode="stdin")
        self.assertEqual(r["cases"][0]["output"], "3\n")
        self.assertEqual(r["cases"][0]["ok"], True)

    def test_stdin_tokens_whitespace_floats_and_errors(self):
        code = "print(' 1   2 ')\nprint(0.3333333)\n"
        self.assertTrue(run(code, "", [""], ["1 2\n0.333333333"], mode="stdin")["cases"][0]["ok"])
        self.assertFalse(run(code, "", [""], ["1 2 3"], mode="stdin")["cases"][0]["ok"])
        r = run("x = int(input())\nprint(10 // x)\n", "", ["0"], ["?"], mode="stdin")
        self.assertIn("ZeroDivisionError", r["cases"][0]["error"])
        self.assertIn('line 2', r["cases"][0]["error"])
        self.assertEqual(run("print(", "", ["1"], mode="stdin")["status"], "compile_error")


if __name__ == "__main__":
    unittest.main()
