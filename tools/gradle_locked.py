"""Run one Gradle process across checkouts; the OS releases the lock after a crash."""
import contextlib
import os
from pathlib import Path
import subprocess
import sys
import time


@contextlib.contextmanager
def build_lock(path):
    with open(path, "a+b") as lock:
        lock.seek(0, os.SEEK_END)
        if lock.tell() == 0:
            lock.write(b"\0")
            lock.flush()
        while True:
            try:
                lock.seek(0)
                if os.name == "nt":
                    import msvcrt
                    msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
                else:
                    import fcntl
                    fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except OSError:
                time.sleep(0.25)
        try:
            yield
        finally:
            lock.seek(0)
            if os.name == "nt":
                msvcrt.locking(lock.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(lock.fileno(), fcntl.LOCK_UN)


def main():
    root = Path(__file__).resolve().parent.parent
    common = subprocess.check_output(
        ["git", "rev-parse", "--path-format=absolute", "--git-common-dir"], cwd=root, text=True
    ).strip()
    with build_lock(Path(common) / "raycoll-gradle.lock"):
        wrapper = root / ("gradlew.bat" if os.name == "nt" else "gradlew")
        return subprocess.run([str(wrapper), *sys.argv[1:]], cwd=root).returncode


if __name__ == "__main__":
    sys.exit(main())
