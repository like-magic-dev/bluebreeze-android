"""
Prepare a new release of BlueBreeze.

This is a verifying gate, not just a file-rewriter: it runs the full release preparation
sequence, but checks its own work at each step and stops at the first problem instead of leaving
you to discover it after tagging and pushing.

Steps:
1. Verifies the git working tree is clean (so unrelated changes don't get swept into the release).
2. Verifies the requested version is valid and newer than every existing tag.
3. bump_version.py            - updates bluebreeze/build.gradle.kts's currentVersion.
4. Verifies build.gradle.kts was actually updated to the requested version.
5. fetch_assigned_numbers.py  - refreshes the bundled Bluetooth SIG assigned-numbers tables.
6. fix_copyright_headers.py   - ensures every Kotlin file has the correct copyright header.
7. ./gradlew :bluebreeze:assembleRelease   - verifies the library still builds.
8. ./gradlew :bluebreeze:testDebugUnitTest - verifies the unit test suite still passes.

Nothing is committed, tagged, published, or pushed automatically -- this only prepares and
verifies the working tree, and prints the remaining manual steps at the end.

Usage:
    python prepare_release.py <version>

Example:
    python prepare_release.py 1.0.0
"""

import re
import subprocess
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).parent.parent
TOOLS_DIR = Path(__file__).parent

VERSION_PATTERN = re.compile(r'^\d+\.\d+\.\d+$')


def fail(message: str) -> None:
    print(f"\n✗ {message}")
    sys.exit(1)


def run_script(script_name: str, args: list = None) -> None:
    """Run a Tools script from the project root, failing the release if it doesn't succeed."""
    print(f"\n{'=' * 60}")
    print(f"Running {script_name}...")
    print('=' * 60)

    cmd = [sys.executable, str(TOOLS_DIR / script_name)]
    if args:
        cmd.extend(args)

    result = subprocess.run(cmd, cwd=PROJECT_ROOT)
    if result.returncode != 0:
        fail(f"{script_name} failed with exit code {result.returncode}")

    print(f"✓ {script_name} completed successfully")


def run_command(description: str, cmd: list) -> None:
    """Run a plain shell command from the project root, failing the release if it doesn't succeed."""
    print(f"\n{'=' * 60}")
    print(f"{description}...")
    print('=' * 60)

    result = subprocess.run(cmd, cwd=PROJECT_ROOT)
    if result.returncode != 0:
        fail(f"{description} failed with exit code {result.returncode}")

    print(f"✓ {description} succeeded")


def check_git_tree_clean() -> None:
    result = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=PROJECT_ROOT,
        capture_output=True,
        text=True,
        check=True,
    )
    if result.stdout.strip():
        fail(
            "Working tree is not clean. Commit or stash pending changes before preparing a "
            "release -- otherwise they'll get mixed into the release commit.\n\n"
            f"{result.stdout}"
        )


def existing_tags() -> list:
    result = subprocess.run(
        ["git", "tag"],
        cwd=PROJECT_ROOT,
        capture_output=True,
        text=True,
        check=True,
    )
    return [tag for tag in result.stdout.splitlines() if tag.strip()]


def check_version_is_new(version: str) -> None:
    tags = existing_tags()

    if version in tags:
        fail(f"Tag {version} already exists.")

    version_tuple = tuple(int(part) for part in version.split("."))
    for tag in tags:
        if not VERSION_PATTERN.match(tag):
            continue

        if version_tuple <= tuple(int(part) for part in tag.split(".")):
            fail(
                f"Version {version} is not newer than existing tag {tag}. Double-check you "
                "passed the intended version."
            )


def check_gradle_version(version: str) -> None:
    gradle_path = PROJECT_ROOT / "bluebreeze" / "build.gradle.kts"
    content = gradle_path.read_text(encoding="utf-8")

    if f'val currentVersion = "{version}"' not in content:
        fail(f"bluebreeze/build.gradle.kts's currentVersion was not updated to {version} as expected.")

    print(f"✓ bluebreeze/build.gradle.kts correctly reflects version {version}")


def main():
    if len(sys.argv) != 2:
        print("Usage: python prepare_release.py <version>")
        print("Example: python prepare_release.py 1.0.0")
        sys.exit(1)

    version = sys.argv[1]

    if not VERSION_PATTERN.match(version):
        fail(f"Invalid version format: {version}. Version should be in format: X.Y.Z (e.g., 1.0.0)")

    print(f"Preparing release version {version}...")

    check_git_tree_clean()
    check_version_is_new(version)

    run_script("bump_version.py", [version])
    check_gradle_version(version)

    run_script("fetch_assigned_numbers.py")
    run_script("fix_copyright_headers.py")

    run_command("Building BlueBreeze", ["./gradlew", ":bluebreeze:assembleRelease"])
    run_command("Running BlueBreeze unit tests", ["./gradlew", ":bluebreeze:testDebugUnitTest"])

    print(f"\n{'=' * 60}")
    print(f"✓ Release {version} prepared successfully!")
    print("\nNext steps:")
    print("  1. Review changes: git diff")
    print(f"  2. Commit changes: git add . && git commit -m 'Prepare release {version}'")
    print(f"  3. Tag release: git tag {version}")
    print("  4. Publish to Maven Central: ./gradlew publish")
    print("  5. Push: git push && git push --tags")
    print('=' * 60)


if __name__ == "__main__":
    main()
