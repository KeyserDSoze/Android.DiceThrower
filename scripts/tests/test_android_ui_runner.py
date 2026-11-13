"""Runner orchestration tests: no SDK, emulator, Java or third-party Python packages required."""

import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
RUNNER = ROOT / "scripts" / "run-android-ui-tests.sh"


class AndroidUiRunnerTest(unittest.TestCase):
    def run_runner(self, gradle_status, adb_status, *args):
        with tempfile.TemporaryDirectory(prefix="dice ui runner ") as directory:
            directory = Path(directory)
            calls = directory / "calls.jsonl"
            for name in ("gradle", "adb"):
                tool = directory / f"fake {name}"
                tool.write_text(
                    "#!/usr/bin/env python3\n"
                    "import json, os, sys\n"
                    "with open(os.environ['RUNNER_CALLS'], 'a') as output:\n"
                    f"    output.write(json.dumps({{'tool': '{name}', 'cwd': os.getcwd(), 'args': sys.argv[1:]}}) + '\\n')\n"
                    f"sys.exit(int(os.environ['FAKE_{name.upper()}_STATUS']))\n",
                    encoding="utf-8",
                )
                tool.chmod(0o755)
            env = os.environ.copy()
            env.update(
                GRADLE_BIN=str(directory / "fake gradle"),
                ADB_BIN=str(directory / "fake adb"),
                RUNNER_CALLS=str(calls),
                FAKE_GRADLE_STATUS=str(gradle_status),
                FAKE_ADB_STATUS=str(adb_status),
            )
            completed = subprocess.run(
                ["bash", str(RUNNER), *args], cwd=directory, env=env,
                text=True, capture_output=True, timeout=30, check=False,
            )
            recorded = [json.loads(line) for line in calls.read_text(encoding="utf-8").splitlines()]
            return completed, recorded

    def test_failed_tests_still_collect_artifacts_and_keep_the_original_exit_code(self):
        completed, calls = self.run_runner(7, 0)
        self.assertEqual(7, completed.returncode, completed.stderr)
        self.assertEqual(["gradle", "adb"], [call["tool"] for call in calls])
        self.assertEqual("pull", calls[1]["args"][0])

    def test_artifact_failure_cannot_turn_passed_or_failed_tests_into_a_different_result(self):
        for gradle_status in (0, 9):
            with self.subTest(gradle_status=gradle_status):
                completed, calls = self.run_runner(gradle_status, 17)
                self.assertEqual(gradle_status, completed.returncode, completed.stderr)
                self.assertEqual("adb", calls[-1]["tool"])
                self.assertIn("keeping the Android test result", completed.stderr)

    def test_filters_are_forwarded_verbatim_from_any_working_directory(self):
        selected = "-Pandroid.testInstrumentationRunnerArguments.class=com.example.Scenario#myTest"
        completed, calls = self.run_runner(0, 0, selected)
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertEqual(str(ROOT / "src"), calls[0]["cwd"])
        self.assertEqual(selected, calls[0]["args"][-1])
        self.assertIn(":app:connectedDebugAndroidTest", calls[0]["args"])
        self.assertIn("-Pandroid.testInstrumentationRunnerArguments.e2eScreenshots=true", calls[0]["args"])


if __name__ == "__main__":
    unittest.main()
