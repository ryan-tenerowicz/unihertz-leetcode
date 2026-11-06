"""Rev 38: type-aware completion (titan_complete, Jedi), on the Mac's python3."""
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "main", "python"))
import titan_complete  # noqa: E402


def names(code):
    lines = code.split("\n")
    r = json.loads(titan_complete.complete(json.dumps({"code": code, "line": len(lines), "col": len(lines[-1])})))
    assert "error" not in r, r
    return [i["name"] for i in r["items"]]


class CompleteTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        titan_complete.init(tempfile.mkdtemp())

    def test_list_methods_from_assignment(self):
        self.assertEqual(names("list_name = [1, 2]\nlist_name.")[:4], ["append", "clear", "copy", "count"])

    def test_type_hints(self):
        code = "class Solution:\n    def f(self, nums: List[int], s: str, head: Optional[ListNode]) -> int:\n        "
        self.assertEqual(names(code + "nums.so"), ["sort"])
        self.assertIn("upper", names(code + "s."))
        self.assertEqual(sorted(names(code + "head.next.")), ["next", "val"])

    def test_preimported_types(self):
        self.assertIn("popleft", names("q = deque()\nq."))
        self.assertIn("most_common", names("c = Counter('abc')\nc."))
        self.assertIn("bisect_left", names("sl = SortedList()\nsl."))

    def test_own_names_first_and_no_dunders(self):
        got = names("class Solution:\n    def maxArea(self, height: List[int]) -> int:\n        he")
        self.assertEqual(got[0], "height")
        self.assertIn("heappush", got)
        self.assertFalse(any(n.startswith("_") for n in names("x = []\nx.")))

    def test_argument_counts(self):
        r = json.loads(titan_complete.complete(json.dumps({"code": "x = []\nx.", "line": 2, "col": 2})))
        args = {i["name"]: i.get("args") for i in r["items"]}
        self.assertEqual(args["clear"], 0)
        self.assertEqual(args["append"], 1)
        self.assertEqual(args["pop"], 1)

    def test_collections_classes_beat_typing_aliases(self):
        def kinds(code):
            r = json.loads(titan_complete.complete(json.dumps({"code": code, "line": 1, "col": len(code)})))
            return {i["name"]: i["type"] for i in r["items"]}
        self.assertEqual(kinds("c = Cou")["Counter"], "class")
        self.assertEqual(kinds("d = Ord")["OrderedDict"], "class")
        self.assertEqual(kinds("x = Li")["List"], "statement")  # a hint, not a call: no brackets
        self.assertEqual(kinds("x = inso")["insort"], "function")

    def test_broken_code_is_harmless(self):
        self.assertIsInstance(names("def f(:\n    x = [\nx."), list)


if __name__ == "__main__":
    unittest.main()
