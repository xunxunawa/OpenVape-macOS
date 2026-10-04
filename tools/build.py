#!/usr/bin/env python3
"""Build Forge or Lunar macOS app images from the published source tree."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
TARGETS = {
    "forge": {
        "app_name": "OpenVape Forge Injector",
        "agent_class": "gg.vape.mac.bootstrap.MacAgent",
        "identifier": "local.openvape.macos.forge.injector",
        "app_name_property": "OpenVapeForgeInjector",
    },
    "lunar": {
        "app_name": "OpenVape Lunar Injector",
        "agent_class": "gg.vape.mac.lunar.LunarEntryAgent",
        "identifier": "local.openvape.macos.lunar.injector",
        "app_name_property": "OpenVapeLunarInjector",
    },
}
EXPORTER_CLASS = "gg.vape.mac.rescue.ConfigExportAgent"
INJECTOR_CLASS = "gg.vape.mac.injector.Injector"


def run(command: list[str], *, cwd: Path = ROOT, capture: bool = False) -> str:
    result = subprocess.run(
        command,
        cwd=cwd,
        check=True,
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
    )
    return result.stdout.strip() if capture else ""


def find_jdk17() -> Path:
    configured = os.environ.get("JAVA_HOME")
    if configured:
        home = Path(configured).expanduser().resolve()
    else:
        helper = Path("/usr/libexec/java_home")
        if not helper.is_file():
            raise RuntimeError("Set JAVA_HOME to a JDK 17 installation")
        home = Path(run([str(helper), "-v", "17"], capture=True)).expanduser().resolve()

    javac = home / "bin" / "javac"
    java = home / "bin" / "java"
    jar_tool = home / "bin" / "jar"
    jpackage = home / "bin" / "jpackage"
    for tool in (javac, java, jar_tool, jpackage):
        if not tool.is_file():
            raise FileNotFoundError(f"JDK 17 tool not found: {tool}")
    version = run([str(javac), "-version"], capture=True)
    if "17." not in version and not version.endswith("17"):
        raise RuntimeError(f"Expected JDK 17 from JAVA_HOME, found: {version}")
    return home


def parse_coordinates(document: object) -> list[tuple[str, str, str, str | None]]:
    if not isinstance(document, list):
        raise ValueError("tools/dependencies.json must contain an array of Maven coordinate arrays")
    coordinates: list[tuple[str, str, str, str | None]] = []
    for item in document:
        if not isinstance(item, list) or len(item) not in (3, 4):
            raise ValueError(f"Invalid Maven coordinate; expected [group, artifact, version, (classifier)]: {item!r}")
        group, artifact, version = item[:3]
        classifier = item[3] if len(item) == 4 else None
        if not all(isinstance(value, str) and value.strip() for value in (group, artifact, version)):
            raise ValueError(f"Maven coordinate fields must be non-empty strings: {item!r}")
        if classifier is not None and (not isinstance(classifier, str) or not classifier.strip()):
            raise ValueError(f"Maven classifier must be a non-empty string: {item!r}")
        coordinates.append((group, artifact, version, classifier))
    return coordinates


def maven_dependencies(build_root: Path) -> list[Path]:
    manifest = TOOLS / "dependencies.json"
    if not manifest.is_file():
        raise FileNotFoundError(f"Dependency manifest not found: {manifest}")
    try:
        coordinates = parse_coordinates(json.loads(manifest.read_text(encoding="utf-8")))
    except json.JSONDecodeError as failure:
        raise ValueError(f"Invalid dependency manifest JSON: {failure}") from failure

    curl = shutil.which("curl")
    if coordinates and curl is None:
        raise RuntimeError("curl is required to download dependencies; alternatively pass --deps-dir")
    destination = build_root / "deps"
    destination.mkdir(parents=True, exist_ok=True)
    downloaded: list[Path] = []
    for group, artifact, version, classifier in coordinates:
        suffix = f"-{classifier}" if classifier else ""
        filename = f"{artifact}-{version}{suffix}.jar"
        relative = f"{group.replace('.', '/')}/{artifact}/{version}/{filename}"
        url = f"https://repo.maven.apache.org/maven2/{relative}"
        jar_path = destination / filename
        with tempfile.NamedTemporaryFile(prefix=f".{filename}.", suffix=".part", dir=destination, delete=False) as temporary:
            temporary_path = Path(temporary.name)
        try:
            run([curl, "--fail", "--location", "--silent", "--show-error", "--output", str(temporary_path), url])
            expected_sha1 = run([curl, "--fail", "--location", "--silent", "--show-error", f"{url}.sha1"], capture=True)
            expected_sha1 = "".join(expected_sha1.split()).lower()
            actual_sha1 = hashlib.sha1(temporary_path.read_bytes()).hexdigest()
            if not expected_sha1 or actual_sha1 != expected_sha1:
                raise ValueError(f"SHA-1 verification failed for {filename}")
            os.replace(temporary_path, jar_path)
        finally:
            temporary_path.unlink(missing_ok=True)
        downloaded.append(jar_path)
    return downloaded


def resolve_dependencies(args: argparse.Namespace) -> list[Path]:
    if args.deps_dir is None:
        return maven_dependencies(ROOT / "build")
    directory = args.deps_dir.expanduser().resolve()
    if not directory.is_dir():
        raise NotADirectoryError(f"Dependency directory not found: {directory}")
    jars = sorted(path for path in directory.glob("*.jar") if path.is_file())
    if not jars:
        raise FileNotFoundError(f"No .jar dependencies found in: {directory}")
    return jars


def class_path(paths: Iterable[Path]) -> str:
    return os.pathsep.join(str(path) for path in paths)


def compile_java(javac: Path, sources: list[Path], output: Path, *, release: str, dependencies: list[Path] = (), modules: str | None = None) -> None:
    if not sources:
        raise FileNotFoundError("No Java source files found")
    output.mkdir(parents=True, exist_ok=True)
    command = [str(javac), "--release", release, "-encoding", "UTF-8"]
    if modules:
        command.extend(["--add-modules", modules])
    if dependencies:
        command.extend(["-classpath", class_path(dependencies)])
    command.extend(["-d", str(output), *(str(source) for source in sources)])
    run(command)


def write_agent_jar(jar_tool: Path, java: Path, output: Path, classes: Path, resources: Path, assembler_source: Path, dependencies: list[Path], merged_dependencies: list[Path], build_root: Path) -> None:
    assembler_classes = build_root / "assembler-classes"
    compile_java(jar_tool.parent / "javac", [assembler_source], assembler_classes, release="8", dependencies=dependencies)

    output.parent.mkdir(parents=True, exist_ok=True)
    output.unlink(missing_ok=True)
    runtime_classpath = class_path([assembler_classes, *dependencies])
    run([str(java), "-classpath", runtime_classpath, "JarAssembler", str(output), str(classes), str(resources), *(str(path) for path in merged_dependencies)])


def update_manifest_agent_class(jar_path: Path, expected: str, replacement: str) -> None:
    temporary = jar_path.with_name(jar_path.name + ".manifest.tmp")
    changed = False
    try:
        with zipfile.ZipFile(jar_path, "r") as source, zipfile.ZipFile(temporary, "w") as destination:
            for entry in source.infolist():
                content = source.read(entry.filename)
                if entry.filename.upper() == "META-INF/MANIFEST.MF":
                    old = f"Agent-Class: {expected}".encode("ascii")
                    new = f"Agent-Class: {replacement}".encode("ascii")
                    if content.count(old) != 1:
                        raise ValueError(f"Expected one Agent-Class entry for {expected} in {jar_path}")
                    content = content.replace(old, new, 1)
                    changed = True
                destination.writestr(entry, content)
        if not changed:
            raise ValueError(f"Manifest not found in {jar_path}")
        os.replace(temporary, jar_path)
    finally:
        temporary.unlink(missing_ok=True)


def write_manifest(path: Path, agent_class: str) -> None:
    path.write_text(
        "Manifest-Version: 1.0\n"
        f"Agent-Class: {agent_class}\n"
        "Can-Redefine-Classes: true\n"
        "Can-Retransform-Classes: true\n\n",
        encoding="utf-8",
    )


def make_jar(jar_tool: Path, output: Path, classes: Path, manifest: Path | None = None) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    output.unlink(missing_ok=True)
    command = [str(jar_tool), "--create", "--file", str(output)]
    if manifest is not None:
        command.extend(["--manifest", str(manifest)])
    command.extend(["-C", str(classes), "."])
    run(command)


def copy_notices(source: Path, destination: Path) -> None:
    if not source.is_dir():
        raise FileNotFoundError(f"Third-party notices directory not found: {source}")
    if destination.exists():
        shutil.rmtree(destination)
    shutil.copytree(source, destination)


def build(target: str, args: argparse.Namespace) -> Path:
    config = TARGETS[target]
    target_build = ROOT / "build" / target
    target_dist = ROOT / "dist" / target
    bundle_dir = target_dist / "bundle"
    app_path = bundle_dir / f"{config['app_name']}.app"
    if app_path.exists():
        raise FileExistsError(f"Refusing to replace existing app: {app_path}")

    jdk = find_jdk17()
    javac, java = jdk / "bin" / "javac", jdk / "bin" / "java"
    jar_tool, jpackage = jdk / "bin" / "jar", jdk / "bin" / "jpackage"
    dependencies = resolve_dependencies(args)
    merged_dependencies = [
        path for path in dependencies
        if not path.name.lower().startswith(("annotations-", "lwjgl"))
    ]

    payload_source = ROOT / target / "src" / "main" / "java"
    payload_resources = ROOT / target / "src" / "main" / "resources"
    injector_source = ROOT / target / "injector-src" / "gg" / "vape" / "mac" / "injector"
    exporter_source = ROOT / "common" / "config-export-src" / "gg" / "vape" / "mac" / "rescue" / "ConfigExportAgent.java"
    assembler_source = TOOLS / "JarAssembler.java"
    license_file = ROOT / "LICENSE"
    notices_summary = ROOT / "THIRD_PARTY_NOTICES.md"
    notices_directory = ROOT / "third-party-notices"
    for path in (payload_source, payload_resources, injector_source, exporter_source, assembler_source, license_file, notices_summary, notices_directory):
        if not path.exists():
            raise FileNotFoundError(f"Required release input not found: {path}")

    if target_build.exists():
        shutil.rmtree(target_build)
    target_build.mkdir(parents=True)
    payload_classes = target_build / "payload-classes"
    injector_classes = target_build / "injector-classes"
    exporter_classes = target_build / "exporter-classes"
    compile_java(javac, sorted(payload_source.rglob("*.java")), payload_classes, release="8", dependencies=dependencies)

    agent_jar = target_dist / "openvape-macos-agent.jar"
    write_agent_jar(jar_tool, java, agent_jar, payload_classes, payload_resources, assembler_source, dependencies, merged_dependencies, target_build)
    if target == "lunar":
        update_manifest_agent_class(agent_jar, TARGETS["forge"]["agent_class"], config["agent_class"])

    injector_sources = [injector_source / "Injector.java", injector_source / "CompactInjector.java"]
    compile_java(javac, injector_sources, injector_classes, release="17", modules="jdk.attach")
    injector_jar = target_build / "openvape-macos-injector.jar"
    make_jar(jar_tool, injector_jar, injector_classes)

    compile_java(javac, [exporter_source], exporter_classes, release="8", dependencies=dependencies)
    exporter_jar = target_build / "openvape-config-export.jar"
    exporter_manifest = target_build / "exporter-manifest.mf"
    write_manifest(exporter_manifest, EXPORTER_CLASS)
    make_jar(jar_tool, exporter_jar, exporter_classes, exporter_manifest)

    bundle_input = target_dist / "bundle-input"
    bundle_input.mkdir(parents=True, exist_ok=True)
    for source, filename in (
        (agent_jar, "openvape-macos-agent.jar"),
        (injector_jar, "openvape-macos-injector.jar"),
        (exporter_jar, "openvape-config-export.jar"),
        (license_file, "LICENSE"),
        (notices_summary, "THIRD_PARTY_NOTICES.md"),
    ):
        shutil.copy2(source, bundle_input / filename)
    copy_notices(notices_directory, bundle_input / "third-party-notices")

    bundle_dir.mkdir(parents=True, exist_ok=True)
    app_name_property = config["app_name_property"]
    run([
        str(jpackage), "--type", "app-image", "--name", config["app_name"],
        "--app-version", "1.0.0", "--mac-package-identifier", config["identifier"],
        "--add-modules", "java.desktop,jdk.attach,java.management,java.logging",
        "--input", str(bundle_input), "--dest", str(bundle_dir),
        "--main-jar", "openvape-macos-injector.jar", "--main-class", INJECTOR_CLASS,
        "--java-options", "--add-modules=jdk.attach",
        "--java-options", "-Dopenvape.portable=true",
        "--java-options", f"-Dapple.awt.application.name={app_name_property}",
    ])
    if not app_path.is_dir():
        raise RuntimeError(f"jpackage did not create expected app image: {app_path}")
    return app_path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("target", choices=sorted(TARGETS))
    parser.add_argument("--deps-dir", type=Path, help="Use local .jar dependencies instead of downloading from Maven Central")
    args = parser.parse_args()
    try:
        app = build(args.target, args)
    except (OSError, RuntimeError, ValueError, subprocess.CalledProcessError) as failure:
        print(f"Build failed: {failure}", file=sys.stderr)
        return 1
    print(f"Created {args.target} app image: {app}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
