"""LeetCode's pre-imported names (PLAN §7), run before each solution and shown to the completer.

The node classes carry type hints so the completer knows `node.next` is a ListNode (rev 38).
"""

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
        self.val: int = val
        self.next: Optional["ListNode"] = next

class TreeNode:
    def __init__(self, val=0, left=None, right=None):
        self.val: int = val
        self.left: Optional["TreeNode"] = left
        self.right: Optional["TreeNode"] = right
"""
