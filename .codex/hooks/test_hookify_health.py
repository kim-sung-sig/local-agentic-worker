import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("hookify-health.py")


class HookifyHealthTest(unittest.TestCase):
    def run_hook(self, root, payload, *args):
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--root", str(root), *args],
            input=json.dumps(payload), text=True, capture_output=True, check=False,
        )

    def test_doctor_accepts_current_project_rules(self):
        result = self.run_hook(Path(__file__).parents[2], {}, "--doctor")
        self.assertEqual(0, result.returncode, result.stderr)

    def test_unhealthy_rules_block_commands_but_allow_rule_repair(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            rules = root / ".codex"
            rules.mkdir()
            rule = rules / "hookify.broken.local.md"
            rule.write_text("---\nname: broken\nconditions:\n  - pattern: '[" + "'\n---\n", encoding="utf-8")

            blocked = self.run_hook(root, {"tool_name": "Bash", "tool_input": {"command": "git status"}})
            self.assertEqual(2, blocked.returncode)
            self.assertIn("Hookify recovery mode", blocked.stderr)

            allowed = self.run_hook(root, {"tool_name": "Edit", "tool_input": {"file_path": str(rule)}})
            self.assertEqual(0, allowed.returncode, allowed.stderr)

    def test_unhealthy_rules_block_unknown_mutating_tools(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / ".codex").mkdir()
            blocked = self.run_hook(root, {"tool_name": "NotebookEdit", "tool_input": {}})
            self.assertEqual(2, blocked.returncode)

    def test_gate_registration_has_no_matcher(self):
        config = json.loads((Path(__file__).parents[1] / "hooks.json").read_text(encoding="utf-8"))
        gate = next(hook for hook in config["hooks"]["PreToolUse"]
                    if "hookify-health.py" in hook["hooks"][0]["command"])
        self.assertNotIn("matcher", gate)

    def test_doctor_bypass_rejects_shell_chaining(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / ".codex").mkdir()
            blocked = self.run_hook(root, {"tool_name": "Bash", "tool_input": {
                "command": "python .codex/hooks/hookify-health.py --doctor; git status"
            }})
            self.assertEqual(2, blocked.returncode)


if __name__ == "__main__":
    unittest.main()
