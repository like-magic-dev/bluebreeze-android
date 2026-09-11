"""
Add or update copyright headers in Kotlin files.

This script scans all .kt files under bluebreeze/src and example/src and ensures they have the
proper copyright header. If a file already has a copyright header, it is replaced with the
standardized one.

Usage:
    python fix_copyright_headers.py
"""

import re
from pathlib import Path

# Define the copyright header
COPYRIGHT_HEADER = """//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

"""

# Pattern to match existing copyright headers
COPYRIGHT_PATTERN = re.compile(r'^//\s*\n(//.*\n)*//\s*\n', re.MULTILINE)


def remove_existing_header(content: str) -> str:
    """Remove existing copyright header from content."""
    match = COPYRIGHT_PATTERN.match(content)
    if match:
        return content[match.end():]
    return content


def add_copyright_header(file_path: Path) -> bool:
    """
    Add or update copyright header in a Kotlin file.

    Args:
        file_path: Path to the Kotlin file

    Returns:
        True if the file was modified, False otherwise
    """
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()

        # Skip empty files
        if not content.strip():
            return False

        # Remove existing header
        content_without_header = remove_existing_header(content)

        # Create new content with copyright header
        new_content = COPYRIGHT_HEADER + content_without_header

        # Only write if content changed
        if new_content != content:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(new_content)
            return True

        return False
    except Exception as e:
        print(f"Error processing {file_path}: {e}")
        return False


def main():
    """Main function to process all Kotlin files."""
    script_dir = Path(__file__).parent
    project_root = script_dir.parent

    # Directories to scan
    directories_to_scan = [
        project_root / "bluebreeze" / "src",
        project_root / "example" / "src",
    ]

    modified_files = []
    skipped_files = []

    for directory in directories_to_scan:
        if not directory.exists():
            print(f"Directory not found: {directory}")
            continue

        print(f"Scanning {directory.relative_to(project_root)}/...")

        for kotlin_file in directory.rglob("*.kt"):
            if add_copyright_header(kotlin_file):
                modified_files.append(kotlin_file.relative_to(project_root))
                print(f"  ✓ Modified: {kotlin_file.relative_to(project_root)}")
            else:
                skipped_files.append(kotlin_file.relative_to(project_root))

    # Print summary
    print("\n" + "="*60)
    print("Summary:")
    print(f"  Modified: {len(modified_files)} file(s)")
    print(f"  Skipped:  {len(skipped_files)} file(s)")
    print("="*60)


if __name__ == "__main__":
    main()
