"""
Fetch and parse Bluetooth SIG assigned numbers.

This script clones the official Bluetooth SIG repository, extracts company identifiers
and UUIDs from YAML files, and generates Kotlin code for use in the BlueBreeze library.

The generated file (bluebreeze/src/main/java/dev/likemagic/bluebreeze/BBAssignedNumbers.kt)
contains:
- Service UUIDs mapping (BBUUID -> String)
- Characteristic UUIDs mapping (BBUUID -> String)
- Company identifiers mapping (Int -> String)

It fully replaces BBAssignedNumbers.kt -- nothing hand-written should live in that file.
Hand-maintained constants (AD types, the CCCD UUID, the default MTU) live in BBConstants.kt
instead, which this script never touches.

Dependencies:
    - PyYAML>=6.0

Usage:
    python fetch_assigned_numbers.py
"""

import shutil
import subprocess
from pathlib import Path

import yaml

REPO_URL = "https://bitbucket.org/bluetooth-SIG/public.git"

PROJECT_ROOT = Path(__file__).parent.parent
OUTPUT = PROJECT_ROOT / "bluebreeze" / "src" / "main" / "java" / "dev" / "likemagic" / "bluebreeze" / "BBAssignedNumbers.kt"

HEADER = """//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

/**
 * Bluetooth SIG assigned-number lookup tables (service UUIDs, characteristic UUIDs, and
 * company/manufacturer identifiers), keyed by [BBUUID] or numeric ID.
 *
 * Auto-generated. Do not edit by hand.
 */
object BBAssignedNumbers {
"""


def escape_kotlin_string(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"')


def clone_repo(clone_dir: Path) -> None:
    print("Cloning the Bluetooth SIG repository...")
    if clone_dir.exists():
        print("Removing existing repository checkout...")
        shutil.rmtree(clone_dir)
    subprocess.run(["git", "clone", REPO_URL, str(clone_dir)], check=True)


# Kotlin/JVM caps a single method's bytecode at 64KB. Some of these lookup tables (company
# identifiers today, potentially UUID tables too as the SIG registries keep growing) are large
# enough that a single mapOf(...) initializer could overflow that limit. Every generated map is
# split across multiple functions so it stays safely under the cap regardless of table size.
MAP_CHUNK_SIZE = 1000


def write_chunked_map(output_file, property_name: str, key_type: str, value_type: str, entries: list[str]) -> None:
    chunks = [entries[i:i + MAP_CHUNK_SIZE] for i in range(0, len(entries), MAP_CHUNK_SIZE)]
    calls = " + ".join(f"{property_name}{i}()" for i in range(len(chunks)))
    output_file.write(f"        val {property_name}: Map<{key_type}, {value_type}> = {calls}\n\n")
    for i, chunk in enumerate(chunks):
        output_file.write(f"        private fun {property_name}{i}(): Map<{key_type}, {value_type}> = mapOf(\n")
        for entry in chunk:
            output_file.write(f"            {entry},\n")
        output_file.write("        )\n\n")


def write_service_uuids(output_file, clone_dir: Path) -> None:
    print("Parsing service UUIDs...")
    with open(clone_dir / "assigned_numbers/uuids/service_uuids.yaml", "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)

    print("Exporting service UUIDs...")
    output_file.write("    // https://bitbucket.org/bluetooth-SIG/public/src/main/assigned_numbers/uuids/service_uuids.yaml\n")
    output_file.write("    object Service {\n")
    entries = [
        f'BBUUID.fromString("{item["uuid"]:04X}") to "{escape_kotlin_string(item["name"])}"'
        for item in sorted(data["uuids"], key=lambda x: x["uuid"])
    ]
    write_chunked_map(output_file, "knownUUIDs", "BBUUID", "String", entries)
    output_file.write("    }\n\n")


def write_characteristic_uuids(output_file, clone_dir: Path) -> None:
    print("Parsing characteristic UUIDs...")
    with open(clone_dir / "assigned_numbers/uuids/characteristic_uuids.yaml", "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)

    print("Exporting characteristic UUIDs...")
    output_file.write("    // https://bitbucket.org/bluetooth-SIG/public/src/main/assigned_numbers/uuids/characteristic_uuids.yaml\n")
    output_file.write("    object Characteristic {\n")
    entries = []
    for item in sorted(data["uuids"], key=lambda x: x["uuid"]):
        name = escape_kotlin_string(item["name"].replace("\\textsubscript{2}", "2"))
        entries.append(f'BBUUID.fromString("{item["uuid"]:04X}") to "{name}"')
    write_chunked_map(output_file, "knownUUIDs", "BBUUID", "String", entries)
    output_file.write("    }\n\n")


def write_company_identifiers(output_file, clone_dir: Path) -> None:
    print("Parsing company identifiers...")
    with open(clone_dir / "assigned_numbers/company_identifiers/company_identifiers.yaml", "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)

    print("Exporting company identifiers...")
    entries = [
        f'{item["value"]} to "{escape_kotlin_string(item["name"])}"'
        for item in sorted(data["company_identifiers"], key=lambda x: x["value"])
    ]
    output_file.write("    object Manufacturer {\n")
    write_chunked_map(output_file, "knownIds", "Int", "String", entries)
    output_file.write("    }\n")


def main() -> None:
    clone_dir = PROJECT_ROOT / "bluetooth_repo"
    clone_repo(clone_dir)

    print("Generating Kotlin file...")
    with open(OUTPUT, "w", encoding="utf-8") as output_file:
        output_file.write(HEADER)
        output_file.write("\n")
        write_service_uuids(output_file, clone_dir)
        write_characteristic_uuids(output_file, clone_dir)
        write_company_identifiers(output_file, clone_dir)
        output_file.write("}\n")

    print(f"Generated {OUTPUT.relative_to(PROJECT_ROOT)}")

    print("Cleaning up...")
    shutil.rmtree(clone_dir)


if __name__ == "__main__":
    main()
