#!/usr/bin/env bash
# Runs the instrumented tests on the emulator started by android-emulator-runner and, when they
# fail, says WHICH tests failed. Gradle's own failure is only "connectedDebugAndroidTest FAILED,
# see the report", and the report is not in the job log.
#
# It lives in a file (not in the workflow's `script:`) because that action runs every line of
# `script:` as a separate `sh -c`, so multi-line shell such as an if/fi block cannot be written
# there.
#
# Outputs, all collected by the workflow's failure-artifact step:
#   gradle-instrumented-tests.log  the complete Gradle --info output (also streamed to the job log)
#   logcat-tail.txt                the last part of the device log, if the device is still reachable
# On failure it also prints each failed test with its assertion message, and repeats the first ones
# as workflow annotations so they show up on the run page and its check-run annotations.

set -uo pipefail

LOG=gradle-instrumented-tests.log
RESULTS_DIR=app/build/outputs/androidTest-results
MAX_ANNOTATIONS=10 # GitHub only shows the first 10 annotations of a kind per step.

# Prints every failed/errored test found in the JUnit XML files, as text and as annotations.
print_failures() {
  python3 - "$RESULTS_DIR" "$MAX_ANNOTATIONS" <<'PY'
import os, sys, xml.etree.ElementTree as ET

results_dir, max_annotations = sys.argv[1], int(sys.argv[2])
files = sorted(
    os.path.join(root, name)
    for root, _, names in os.walk(results_dir)
    for name in names
    if name.startswith("TEST-") and name.endswith(".xml")
)
if not files:
    print(f"No JUnit XML under {results_dir}: the test run ended before reporting any result "
          "(instrumentation crash, timeout or install failure). See the Gradle log above.")
    sys.exit(0)

def escape(value):  # workflow-command escaping
    return value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")

total = failed = 0
failures = []
for path in files:
    try:
        suite = ET.parse(path).getroot()
    except ET.ParseError as error:
        print(f"Could not parse {path}: {error}")
        continue
    for case in suite.iter("testcase"):
        total += 1
        problem = case.find("failure")
        if problem is None:
            problem = case.find("error")
        if problem is None:
            continue
        failed += 1
        name = f'{case.get("classname")}.{case.get("name")}'
        text = (problem.get("message") or problem.text or "").strip()
        failures.append((name, text))

print(f"Instrumented results: {total} tests, {failed} failed")
for name, text in failures:
    print(f"\nFAILED {name}")
    for line in text.splitlines()[:20]:
        print(f"    {line}")

summary = os.environ.get("GITHUB_STEP_SUMMARY")
if summary and failures:
    with open(summary, "a", encoding="utf-8") as out:
        out.write(f"### Failed instrumented tests ({failed} of {total})\n\n")
        for name, text in failures:
            first = (text.splitlines() or [""])[0][:300]
            out.write(f"- `{name}`: {first}\n")

for name, text in failures[:max_annotations]:
    first = (text.splitlines() or [""])[0][:300]
    print(f"::error title=Failed instrumented test::{escape(name + ': ' + first)}")
PY
}

if ./gradlew connectedDebugAndroidTest --stacktrace --info 2>&1 | tee "$LOG"; then
  exit 0
fi

echo
echo "==================== Instrumented tests FAILED ===================="
# "device" here means the emulator survived and the failure is in the tests; "offline" or an error
# means the emulator itself went away.
echo "adb state after failure: $(adb get-state 2>&1)"
print_failures
adb logcat -d -v threadtime -t 5000 > logcat-tail.txt 2>&1 || true
exit 1
