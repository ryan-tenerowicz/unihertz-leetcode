"""Per-case limits for local runs (PLAN §7, rev 24): time, memory and output.

The solution runs on a worker thread; the calling thread is the watchdog. When a case runs past
its time limit, or the process grows past the memory limit, the watchdog raises an exception
*inside the worker thread* (`PyThreadState_SetAsyncExc`), so the traceback shows the line the
code was on, the other cases still run, and the process stays warm.

What this can't stop: a single built-in call that never returns to Python (`sum(range(10**12))`)
holds the interpreter lock, so the watchdog can't run either. The app's overall timeout kills
the process then, and the cases finished so far come back through the progress file.
"""

import io
import os
import threading
import time

try:
    import ctypes
    _set_async_exc = ctypes.pythonapi.PyThreadState_SetAsyncExc
except Exception:  # no ctypes: the app's overall timeout is the only limit
    ctypes = None
    _set_async_exc = None


class Limit(BaseException):
    """Base for the limit exceptions. Not an `Exception`, so `except Exception:` can't swallow it."""
    kind = ""


class TimeLimitExceeded(Limit):
    kind = "time"


class MemoryLimitExceeded(Limit):
    kind = "memory"


class OutputLimitExceeded(Limit):
    kind = "output"


LIMITS = (TimeLimitExceeded, MemoryLimitExceeded, OutputLimitExceeded)


def rss_mb():
    """The process's resident memory in MB, or None if it can't be read."""
    try:
        with open("/proc/self/statm") as f:
            pages = int(f.read().split()[1])
        return pages * os.sysconf("SC_PAGE_SIZE") / 1048576.0
    except Exception:
        pass
    try:  # the Mac, for tests: the peak, which only grows
        import resource
        peak = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
        import sys
        return peak / 1048576.0 if sys.platform == "darwin" else peak / 1024.0
    except Exception:
        return None


class Watch:
    """Shared between the worker (begin/end around each case) and the watchdog (poll)."""

    def __init__(self, time_limit_s, mem_limit_mb):
        self.time_limit_s = time_limit_s
        self.mem_limit_mb = mem_limit_mb
        self.token = 0          # changes for every case, so a late signal is never aimed at the next one
        self.active = None      # the token of the running case, or None between cases
        self.started = 0.0
        self.fired_at = None
        self.fires = 0
        self.base_mb = rss_mb()
        self.thread_id = None
        self.stuck = False      # the case ignored every signal: the harness gives up on it

    # ---- worker side ----
    def begin(self):
        self.token += 1
        self.started = time.monotonic()
        self.fired_at = None
        self.fires = 0
        self.active = self.token

    def end(self):
        self.active = None

    def elapsed_s(self):
        return time.monotonic() - self.started

    # ---- watchdog side ----
    def poll(self):
        """Called every few ms by the watchdog. Returns True if the case is stuck for good."""
        token = self.active
        if token is None or _set_async_exc is None or self.thread_id is None:
            return False
        now = time.monotonic()
        if self.fired_at is not None:
            # Signalled already. A bare `except:` can catch it, so signal again every 0.3 s,
            # and give up after 2 s (a call that never comes back to Python, like time.sleep).
            if now - self.fired_at > 2.0:
                self.stuck = True
                return True
            if now - self.last_fire > 0.3:
                self._fire(token, self.pending)
            return False
        exc = None
        if now - self.started > self.time_limit_s:
            exc = TimeLimitExceeded
        elif self.mem_limit_mb and self.base_mb is not None:
            mb = rss_mb()
            if mb is not None and mb - self.base_mb > self.mem_limit_mb:
                exc = MemoryLimitExceeded
        if exc is not None:
            self.fired_at = now
            self.pending = exc
            self._fire(token, exc)
        return False

    def _fire(self, token, exc):
        if self.active != token:
            return
        self.last_fire = time.monotonic()
        self.fires += 1
        _set_async_exc(ctypes.c_ulong(self.thread_id), ctypes.py_object(exc))

    def message(self, kind):
        if kind == "time":
            return "still running after %g s" % self.time_limit_s
        if kind == "memory":
            return "used more than %d MB" % self.mem_limit_mb
        return ""


class CappedOutput(io.TextIOBase):
    """stdout for one case: keeps the first [keep] characters, and stops the code past [limit]."""

    def __init__(self, keep=20000, limit=1000000):
        self.keep = keep
        self.limit = limit
        self.parts = []
        self.kept = 0
        self.total = 0

    def writable(self):
        return True

    def write(self, s):
        if not isinstance(s, str):
            raise TypeError("write() argument must be str, not %s" % type(s).__name__)
        self.total += len(s)
        if self.kept < self.keep:
            piece = s[: self.keep - self.kept]
            self.parts.append(piece)
            self.kept += len(piece)
        if self.total > self.limit:
            raise OutputLimitExceeded("printed more than %d characters" % self.limit)
        return len(s)

    def getvalue(self):
        text = "".join(self.parts)
        if self.total > self.kept:
            text += "\n… (%d more characters not shown)" % (self.total - self.kept)
        return text


def shorten(text, keep=20000):
    """An output too long to show in full (it's compared in full first)."""
    if text is None or len(text) <= keep:
        return text
    return text[:keep] + "… (%d more characters)" % (len(text) - keep)


def run_watched(target, watch, stack_mb=(256, 64, 0)):
    """Run [target] on a worker thread with a big stack, polling [watch] until it ends.

    Returns True if it ended, False if a case got stuck (the worker is left running; the app
    restarts the process after reading the result).
    """
    t = None
    for size in stack_mb:
        try:
            threading.stack_size(size * 1024 * 1024)
            t = threading.Thread(target=target, daemon=True)
            t.start()
            break
        except (ValueError, RuntimeError):
            continue
    threading.stack_size(0)
    watch.thread_id = t.ident
    while True:
        t.join(0.02)
        if not t.is_alive():
            return True
        if watch.poll():
            return False
