# BOJ IntelliJ Plugin

BOJ IntelliJ is a tool window plugin for IntelliJ IDEA that fetches a BOJ problem page, fills parsed fields, and runs local sample commands against selected sample input and output.

## Target IDE

- IntelliJ target line: 25.3 (build 253)
- Plugin compatibility: since-build `253`, until-build `253.*`

## Setup

1. Use Java 21.
2. Use the Gradle wrapper from this repository.
3. For stable local verification, set this environment variable before Gradle commands:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

Run from the project root:

```bash
./gradlew runIde
```

This launches a sandbox IntelliJ instance with the plugin loaded.

## Usage Flow: Fetch Problem and Run Sample

1. In the sandbox IDE, open the `BOJ` tool window.
2. Optionally enter a numeric BOJ problem number in the problem number field.
3. Click the fetch button to load the problem page.
   - If the field is blank, the plugin tries to extract a problem number from the currently opened class/file name (for example `Boj1000`, `Main_1000`).
   - On tool window open and whenever the currently opened code file changes, the same class-name extraction is attempted automatically.
4. Confirm parsed fields are filled:
   - title
   - limits/statistics in separate rows (time limit, memory limit, submit, answer, solved, correct rate)
   - body sections split into separate panels (`문제`, `입력`, `출력`) in BOJ-like layout
   - section content renders rich HTML/MathJax when JCEF is available (fallback to plain text otherwise)
5. In the sample section, choose a sample from the sample selector.
6. Confirm the sample input and expected output text areas are updated to the selected sample.
7. Optionally enter a local execution command in the command field.
8. Click run.

The command runs with sample input sent to stdin, and the result is compared against the selected expected output.
If the command field is left blank, the plugin first infers a command from the currently opened file (for example `java ".../Main.java"`, `kotlin ...`, or `python3 ...`), and falls back to `./main` when inference is not possible.

## Concrete Sample Run Command

For BOJ `1000` style input (`1 2`) and output (`3`), this command is a reliable local example:

```bash
sh -c "awk '{print $1 + $2}'"
```

Paste the command into the run command field, then run it after selecting a sample.

## How to Interpret Output

- Pass: output matches expected sample output after normalization.
- Fail (timeout): command exceeded timeout.
- Fail (exit code): process ended with non-zero exit code.
- Fail (mismatch): command finished, but output does not match expected output.

Normalization used for comparison:

- CRLF and CR are normalized to LF.
- trailing spaces and tabs per line are ignored.
- trailing blank lines are ignored.

Use the `Actual` output area and `stderr` area to debug failures.

## Troubleshooting

- If Gradle uses the wrong JDK, set `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` and rerun.
- If BOJ fetch fails, confirm the problem number is numeric and check network access.
- If sample run fails with command errors, validate quoting and command availability in your shell environment.
- Kotlin LSP diagnostics are not available in this environment, so Kotlin diagnostics are validated with Gradle test and build commands instead.
