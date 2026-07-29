#!/usr/bin/env python3
"""Independent fail-closed recovery gate for project Hookify rules."""

import argparse
import importlib
import json
import os
import re
import sys
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PLUGIN = Path.home() / ".codex/plugins/cache/user-local/hookify/0.1.0"


def rule_files(root):
    return sorted((root / ".codex").glob("hookify.*.local.md"))


def frontmatter(path):
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError) as error:
        return None, f"{path}: UTF-8 read failed: {error}"
    if not text.startswith("---"):
        return None, f"{path}: missing YAML frontmatter"
    parts = text.split("---", 2)
    if len(parts) < 3:
        return None, f"{path}: frontmatter is not closed"
    return parts[1], None


def regexes(yaml):
    """Return regex values from Hookify's small condition YAML dialect."""
    values = []
    for line in yaml.splitlines():
        stripped = line.strip().lstrip("-").strip()
        if stripped.startswith("pattern:"):
            value = stripped.split(":", 1)[1].strip().strip("\"'")
            values.append(value)
    return values


REPRESENTATIVE_INPUTS = {
    "require-harness-for-plans": ({"tool_name": "Write", "hook_event_name": "PreToolUse", "tool_input": {"file_path": "docs/01-plan/example.md", "new_text": "# plan"}}, "deny"),
    "require-request-validation": ({"tool_name": "Edit", "hook_event_name": "PreToolUse", "tool_input": {"file_path": "src/main/java/example/api/ExampleApi.java", "new_text": "@RequestBody"}}, "warn"),
    "require-swagger-operation": ({"tool_name": "Edit", "hook_event_name": "PreToolUse", "tool_input": {"file_path": "src/main/java/example/api/ExampleApi.java", "new_text": "@GetMapping"}}, "warn"),
    "warn-korean-documents": ({"tool_name": "Edit", "hook_event_name": "PreToolUse", "tool_input": {"file_path": "docs/example.md", "new_text": "내용"}}, "warn"),
}


def plugin_health(plugin, files, root):
    loader = plugin / "core/config_loader.py"
    engine = plugin / "core/rule_engine.py"
    if not loader.is_file() or not engine.is_file():
        return "Hookify loader or rule engine is missing"
    try:
        if "encoding='utf-8'" not in loader.read_text(encoding="utf-8").replace('encoding =', 'encoding='):
            return "Hookify loader does not explicitly read UTF-8 rules"
        sys.path.insert(0, str(plugin))
        config_loader = importlib.import_module("core.config_loader")
        rule_engine = importlib.import_module("core.rule_engine")
        loaded = config_loader.load_rules()
        if len(loaded) != len(files):
            return f"Hookify loader discovered {len(loaded)} rules; expected {len(files)}"
        engine = rule_engine.RuleEngine()
        for rule in loaded:
            sample = REPRESENTATIVE_INPUTS.get(rule.name)
            if sample is None:
                return f"no representative payload for active rule {rule.name}"
            payload, expected = sample
            result = engine.evaluate_rules([rule], payload)
            if expected == "deny":
                if result.get("hookSpecificOutput", {}).get("permissionDecision") != "deny":
                    return f"active rule {rule.name} did not block its representative payload"
            elif not result.get("systemMessage"):
                return f"active rule {rule.name} did not match its representative payload"
    except Exception as error:
        return f"Hookify loader/engine failed: {error}"
    finally:
        if sys.path and sys.path[0] == str(plugin):
            sys.path.pop(0)
    return None


def health(root, plugin):
    files = rule_files(root)
    if not files:
        return False, ["no .codex/hookify.*.local.md rules found"]
    errors = []
    for path in files:
        yaml, error = frontmatter(path)
        if error:
            errors.append(error)
            continue
        if not re.search(r"(?m)^name:\s*\S+", yaml):
            errors.append(f"{path}: frontmatter requires name")
        for pattern in regexes(yaml):
            try:
                re.compile(pattern, re.IGNORECASE)
            except re.error as regex_error:
                errors.append(f"{path}: invalid regex {pattern!r}: {regex_error}")
    if not errors:
        previous_directory = Path.cwd()
        try:
            os.chdir(root)
            plugin_error = plugin_health(plugin, files, root)
        finally:
            os.chdir(previous_directory)
        if plugin_error:
            errors.append(plugin_error)
    return not errors, errors


def allowed_recovery(payload, root):
    tool = payload.get("tool_name", "")
    data = payload.get("tool_input", {})
    if tool in {"Read", "Glob", "Grep", "Search"}:
        return True
    path = data.get("file_path", "")
    try:
        relative = Path(path).resolve().relative_to(root.resolve()).as_posix()
    except (OSError, ValueError):
        relative = ""
    if tool in {"Edit", "Write"} and re.fullmatch(r"\.codex/hookify\..+\.local\.md", relative):
        return True
    command = data.get("command", "").strip()
    if tool != "Bash" or re.search(r"[;&|`$()]", command):
        return False
    script = r'(?:"[^"]*[\\/]\.codex[\\/]hooks[\\/]hookify-health\.py"|(?:\.?[\\/])?\.codex[\\/]hooks[\\/]hookify-health\.py)'
    return bool(re.fullmatch(r"(?:python|python3|py)\s+" + script + r"\s+--doctor", command, re.IGNORECASE))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--doctor", action="store_true")
    parser.add_argument("--session-start", action="store_true")
    parser.add_argument("--root", type=Path, default=PROJECT_ROOT, help=argparse.SUPPRESS)
    parser.add_argument("--plugin-dir", type=Path, default=Path(os.environ.get("HOOKIFY_PLUGIN_DIR", DEFAULT_PLUGIN)), help=argparse.SUPPRESS)
    args = parser.parse_args()
    healthy, errors = health(args.root, args.plugin_dir)
    if args.doctor:
        stream = sys.stdout if healthy else sys.stderr
        print("Hookify healthy" if healthy else "Hookify unhealthy: " + "; ".join(errors), file=stream)
        return 0 if healthy else 1
    if args.session_start:
        if not healthy:
            print("[hookify-health] Hookify recovery mode: " + "; ".join(errors), file=sys.stderr)
        return 0
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        payload = {}
    if not healthy and not allowed_recovery(payload, args.root):
        print("[hookify-health] Hookify recovery mode: " + "; ".join(errors), file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
