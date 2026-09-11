"""
Update the version number in bluebreeze/build.gradle.kts.

This script updates the `currentVersion` declaration that bluebreeze/build.gradle.kts uses for
both the Android library's `version` and every field of the Maven Central publication (the
podspec-equivalent single source of truth for this repo).

Usage:
    python bump_version.py <version>

Example:
    python bump_version.py 0.1.2
"""

import sys
import re
from pathlib import Path


def update_gradle_version(version: str) -> bool:
    """
    Update the currentVersion declaration in bluebreeze/build.gradle.kts.

    Args:
        version: Version string (e.g., "0.1.2")

    Returns:
        True if successful, False otherwise
    """
    script_dir = Path(__file__).parent
    project_root = script_dir.parent
    gradle_path = project_root / "bluebreeze" / "build.gradle.kts"

    if not gradle_path.exists():
        print(f"✗ build.gradle.kts not found: {gradle_path}")
        return False

    print(f"\n{'='*60}")
    print(f"Updating bluebreeze/build.gradle.kts to version {version}...")
    print('='*60)

    try:
        with open(gradle_path, 'r', encoding='utf-8') as f:
            content = f.read()

        new_content, count = re.subn(
            r'val currentVersion = "[\d.]+"',
            f'val currentVersion = "{version}"',
            content,
        )

        if count == 0:
            print("✗ Did not find a `val currentVersion = \"...\"` declaration to update")
            return False

        with open(gradle_path, 'w', encoding='utf-8') as f:
            f.write(new_content)

        print(f"✓ Updated build.gradle.kts to version {version}")
        return True

    except Exception as e:
        print(f"✗ Failed to update build.gradle.kts: {e}")
        return False


def main():
    """Main function to update version."""
    if len(sys.argv) != 2:
        print("Usage: python bump_version.py <version>")
        print("Example: python bump_version.py 0.1.2")
        sys.exit(1)

    version = sys.argv[1]

    # Validate version format (simple check)
    if not re.match(r'^\d+\.\d+\.\d+$', version):
        print(f"✗ Invalid version format: {version}")
        print("Version should be in format: X.Y.Z (e.g., 0.1.2)")
        sys.exit(1)

    if not update_gradle_version(version):
        sys.exit(1)

    print('='*60)


if __name__ == "__main__":
    main()
