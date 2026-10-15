import importlib.util
import multiprocessing
from pathlib import Path
import tempfile
import time
import unittest
import os

spec = importlib.util.spec_from_file_location("gradle_locked", Path(__file__).parents[1] / "gradle_locked.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def worker(path, queue, crash=False):
    with module.build_lock(path):
        queue.put(("enter", time.monotonic()))
        if crash:
            queue.close()
            queue.join_thread()
            os._exit(7)
        time.sleep(0.2)
        queue.put(("leave", time.monotonic()))


class GradleLockTest(unittest.TestCase):
    def test_concurrent_invocations_are_serialized(self):
        with tempfile.TemporaryDirectory(prefix="raycoll-lock-") as temp:
            path = str(Path(temp).resolve() / "lock")
            queue = multiprocessing.Queue()
            first = multiprocessing.Process(target=worker, args=(path, queue))
            second = multiprocessing.Process(target=worker, args=(path, queue))
            first.start()
            self.assertEqual("enter", queue.get(timeout=5)[0])
            second.start()
            messages = [queue.get(timeout=5) for _ in range(3)]
            first.join(5)
            second.join(5)
            self.assertEqual(["leave", "enter", "leave"], [m[0] for m in messages])
            self.assertEqual(0, first.exitcode)
            self.assertEqual(0, second.exitcode)

    def test_hard_failure_releases_the_os_lock(self):
        with tempfile.TemporaryDirectory(prefix="raycoll-lock-") as temp:
            path = str(Path(temp).resolve() / "lock")
            queue = multiprocessing.Queue()
            first = multiprocessing.Process(target=worker, args=(path, queue, True))
            first.start()
            queue.get(timeout=5)
            first.join(5)
            self.assertEqual(7, first.exitcode)
            second = multiprocessing.Process(target=worker, args=(path, queue))
            second.start()
            self.assertEqual("enter", queue.get(timeout=5)[0])
            queue.get(timeout=5)
            second.join(5)
            self.assertEqual(0, second.exitcode)


if __name__ == "__main__":
    unittest.main()
