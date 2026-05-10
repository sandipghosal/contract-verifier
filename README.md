# Symbolic Execution for Contract Validation

A generic verification engine that uses Java PathFinder (JPF) and Symbolic PathFinder (SPF) to automatically verify Hoare Logic contracts on Java data structures through symbolic execution.

Instead of testing a handful of concrete inputs, this tool explores **all** possible execution paths simultaneously using symbolic variables, mathematically proving whether contracts hold or detecting violations with exact counterexamples.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Execution Examples](#execution-examples)
  - [Example 1: Verify BoundedQueue](#example-1-verify-boundedqueue)
  - [Example 2: Verify BoundedList](#example-2-verify-boundedlist)
  - [Example 3: Scalified Library Experiment (Bug Detection)](#example-3-scalified-library-experiment)
  - [Example 4: JGraphT Library Experiment (Incompatibility Finding)](#example-4-jgrapht-library-experiment)
  - [Example 5: Verify a Custom Data Structure](#example-5-verify-a-custom-data-structure)
- [Using SPFVerifierUI (Desktop GUI)](#using-spfverifierui-desktop-gui)
- [Contract Format Reference](#contract-format-reference)
- [Project Structure](#project-structure)
- [How the Pipeline Works](#how-the-pipeline-works)
- [Troubleshooting](#troubleshooting)
- [Authors](#authors)

---

## Prerequisites

| Requirement | Details |
|-------------|---------|
| **Operating System** | Linux (Ubuntu, Debian, Fedora, Arch, openSUSE) **or** Windows 10/11 via WSL |
| **sudo access** | Required for installing system packages |
| **Internet** | Required during first-time setup only |
| **Disk space** | ~500 MB (for JDK, JPF, SPF) |

> **Note**: You do **not** need to install Java, Git, or Ant manually. The setup script handles everything automatically.

---

## Installation

### Windows Users — Install WSL First

This tool runs in a Linux environment. On Windows, you need WSL (Windows Subsystem for Linux), which runs a full Ubuntu terminal inside Windows. This is a one-time setup that takes about 2 minutes.

**Step 1** — Open **PowerShell as Administrator** (right-click → "Run as administrator") and run:

```powershell
wsl --install
```

This installs WSL 2 with Ubuntu. **Restart your PC** when prompted.

**Step 2** — After restart, Ubuntu will open automatically and ask you to create a username and password. Set them up (this is your Linux user, not your Windows login).

**Step 3** — From now on, open the **Ubuntu** app from the Start Menu whenever you want to use this tool. All commands below run inside this Ubuntu terminal.

> **Tip:** Your Windows files are accessible inside WSL at `/mnt/c/Users/<YourName>/`. Your WSL home directory is `~` (which maps to `\\wsl$\Ubuntu\home\<username>` from Windows File Explorer).

### Linux Users — No Extra Setup Needed

Proceed directly to the steps below. The script auto-detects your distribution (Ubuntu, Debian, Fedora, Arch, openSUSE) and uses the correct package manager.

---

### Step 1 — Clone the repository

```bash
git clone https://github.com/<your-username>/contract-verifier.git
cd contract-verifier
```

### Step 2 — Run the setup script

```bash
chmod +x master_setup.sh
./master_setup.sh --setup
```

This single command performs the following (each step is skipped if already done):

1. Detects your Linux distribution and installs **OpenJDK 8**, **Git**, **Apache Ant**, and **wget**
2. Clones and builds **jpf-core** (from the yannicnoller Java 8-compatible fork) into `~/jpf-core/`
3. Clones and builds **jpf-symbc** (Symbolic PathFinder) into `~/jpf-symbc/`
4. Creates `~/.jpf/site.properties` with the correct paths
5. Exports all required environment variables to `~/.bashrc`
6. Downloads **JGraphT 0.9.2** and **Scalified Tree 0.2.5** library JARs into `~/libs/`
7. Compiles the core verification engine and generates the default dispatcher

Expected output on a successful setup:

```
╔══════════════════════════════════════════════════════════╗
║  FULL SYSTEM SETUP — From Scratch                        ║
╚══════════════════════════════════════════════════════════╝

  [*] Detected: debian (amd64)
  [*] Updating apt package list...
  [✓] Package list updated
  [✓] Java 8 installed and activated
  [✓] Java 8 confirmed: openjdk version "1.8.0_432"
  [✓] Git ready
  [✓] Ant ready
  [✓] wget ready
  [✓] All system dependencies ready (debian/amd64)

  ...

  [✓] jpf-core BUILD SUCCESSFUL
  [✓] jpf-symbc BUILD SUCCESSFUL
  [✓] Created ~/.jpf/site.properties
  [✓] Added environment variables to .bashrc
  [✓] JGraphT 0.9.2 downloaded to ~/libs/
  [✓] Scalified Tree 0.2.5 downloaded to ~/libs/
  [✓] Core engine compiled
  [✓] Default dispatcher generated and compiled

╔══════════════════════════════════════════════════════════╗
║  SETUP COMPLETE                                          ║
╚══════════════════════════════════════════════════════════╝
  Everything is ready!
```

### Step 3 — Verify the installation

```bash
./master_setup.sh --health
```

Expected output:

```
╔══════════════════════════════════════════════════════════╗
║  SYSTEM HEALTH CHECK                                     ║
╚══════════════════════════════════════════════════════════╝

  [✓] Java 8: openjdk version "1.8.0_432"
  [✓] jpf-core: built
  [✓] jpf-symbc: built
  [✓] site.properties: exists
  [✓] JGraphT 0.9.2 JAR: present
  [✓] Scalified 0.2.5 JAR: present
  [✓] Project files: present in /home/user/contract-verifier

  [✓] All systems healthy — ready to verify!
```

---

## Execution Examples

### Example 1: Verify BoundedQueue

BoundedQueue is a FIFO queue backed by `LinkedList<Integer>` with capacity 3. Its contracts file (`BoundedQueue_contracts.txt`) contains 8 valid contracts and 3 intentionally buggy ones.

**Run:**

```bash
./master_setup.sh --verify BoundedQueue generic_verify.jpf
```

Or use the interactive menu:

```bash
./master_setup.sh
# Select option 3
```

**What happens behind the scenes:**

1. `BoundedQueue.java` is compiled
2. `DispatcherGenerator` inspects `BoundedQueue` via Java Reflection and generates `GeneratedDispatcher.java`
3. The dispatcher and engine are compiled together
4. JPF runs `GenericContractsTest` with symbolic variables in the range `[-10, 10]`

**Expected output (abbreviated):**

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Verifying: BoundedQueue
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  [✓] Compiled
  [✓] Dispatcher generated
  [✓] Full compilation done

  JPF OUTPUT

JavaPathfinder core system v8.0 ...
====================================================== system under test
GenericContractsTest.main()

[*] Verifying: BoundedQueue
[*] Contracts: BoundedQueue_contracts.txt

[*] Verifying: {!isFull()} enqueue(p1) {size() == oldSize + 1}
[*] Precondition satisfied, executing: enqueue
[+] VALIDATED: {!isFull()} enqueue(p1) {size() == oldSize + 1}

[*] Verifying: {!isFull()} enqueue(p1) {contains(p1)}
[*] Precondition satisfied, executing: enqueue
[+] VALIDATED: {!isFull()} enqueue(p1) {contains(p1)}

[*] Verifying: {isEmpty()} enqueue(p1) {size() == 1}
[*] Precondition satisfied, executing: enqueue
[+] VALIDATED: {isEmpty()} enqueue(p1) {size() == 1}

...

[*] Verifying: {!isFull() && size() == 2} enqueue(p1) {size() == 2}
[*] Precondition satisfied, executing: enqueue

[!!!] VIOLATION DETECTED!
      Contract: {!isFull() && size() == 2} enqueue(p1) {size() == 2}
      State: BoundedQueue[1, -3, 7]

[*] Verifying: {isEmpty()} enqueue(p1) {size() == 0}
[*] Precondition satisfied, executing: enqueue

[!!!] VIOLATION DETECTED!
      Contract: {isEmpty()} enqueue(p1) {size() == 0}
      State: BoundedQueue[-6]

====================================================== results
no errors detected
```

**Interpretation:**

- All 8 valid contracts print `[+] VALIDATED` — the postcondition holds on every feasible symbolic path.
- All 3 buggy contracts print `[!!!] VIOLATION DETECTED!` with a concrete counterexample (e.g., `BoundedQueue[1, -3, 7]` is the queue state when the violation occurred). This is correct — the buggy contracts are *intentionally* wrong to prove the engine catches them.

---

### Example 2: Verify BoundedList

BoundedList is a LIFO structure with a branching `insert(p1, p2)` method — if `p1` exists in the list, `p2` is inserted after `p1`; otherwise `p2` is appended.

**Run:**

```bash
./master_setup.sh
# Select option 4
```

**Expected output (key lines):**

```
[*] Verifying: BoundedList
[*] Contracts: BoundedList_contracts.txt

[+] VALIDATED: {size() > 0} pop() {size() == oldSize - 1}
[+] VALIDATED: {size() == 2} pop() {size() == 1}
[+] VALIDATED: {!isfull()} push(p1) {size() == oldSize + 1}
[+] VALIDATED: {!isfull()} push(p1) {contains(p1)}
[+] VALIDATED: {isempty()} push(p1) {size() == 1}
[+] VALIDATED: {!contains(b1) && p1 != b1} push(p1) {!contains(b1)}
[+] VALIDATED: {!isfull()} insert(p1, p2) {size() == oldSize + 1}

[!!!] VIOLATION DETECTED!
      Contract: {!isfull() && size() == 2} push(p1) {size() == 2}

[!!!] VIOLATION DETECTED!
      Contract: {isempty()} insert(p1, p2) {size() == 0}
```

**Interpretation:**

- 7 valid contracts validated, 2 intentional bugs caught. The `insert()` contract exercises the branching path — symbolic execution explores both the "p1 exists" and "p1 doesn't exist" branches automatically.

---

### Example 3: Scalified Library Experiment

This experiment verifies a **real external library** (Scalified Tree) through the engine. It runs two passes: a buggy wrapper first, then a fixed wrapper.

**Run:**

```bash
./master_setup.sh --experiment scalified
```

**Expected output — Run 1 (BUGGY wrapper):**

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  RUN 1: BUGGY ScalifiedWrapper
  Bug: remove() calls target.remove(target) — wrong API usage
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[+] VALIDATED: {true} add(p1) {size() == oldSize + 1}
[+] VALIDATED: {true} add(p1) {contains(p1)}
[+] VALIDATED: {isEmpty()} add(p1) {size() == 1}
[+] VALIDATED: {isEmpty()} add(p1) {!isEmpty()}

[!!!] VIOLATION DETECTED!
      Contract: {contains(p1)} remove(p1) {!contains(p1)}
      State: ScalifiedWrapper[root=1, children=[2, 3]]
```

The bug: `target.remove(target)` asks node 2 to search its **own children** for value 2. Node 2 is a leaf (no children), so the remove silently fails and `contains(2)` is still true. A conventional unit test (removing the root node) would pass — only symbolic execution found this by exploring all tree states.

**Expected output — Run 2 (FIXED wrapper):**

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  RUN 2: FIXED ScalifiedWrapper
  Fix: remove() finds parent, calls parent.dropSubtree(child)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[+] VALIDATED: {true} add(p1) {size() == oldSize + 1}
[+] VALIDATED: {true} add(p1) {contains(p1)}
[+] VALIDATED: {isEmpty()} add(p1) {size() == 1}
[+] VALIDATED: {isEmpty()} add(p1) {!isEmpty()}
[+] VALIDATED: {contains(p1)} remove(p1) {!contains(p1)}
[+] VALIDATED: {size() == 1 && contains(p1)} remove(p1) {isEmpty()}

[!!!] VIOLATION DETECTED!
      Contract: {size() == 2} add(p1) {size() == 2}

[!!!] VIOLATION DETECTED!
      Contract: {isEmpty()} add(p1) {size() == 0}
```

All valid contracts validated (including `remove()`), and the two intentionally buggy contracts are correctly caught.

---

### Example 4: JGraphT Library Experiment

This experiment attempts to verify a real production library (JGraphT) through JPF. It **intentionally fails** — the failure itself is a research finding.

**Run:**

```bash
./master_setup.sh --experiment jgrapht
```

**Expected output:**

```
╔══════════════════════════════════════════════════════════╗
║  EXPERIMENT: JGraphT Library Verification                ║
╚══════════════════════════════════════════════════════════╝

  This experiment attempts to symbolically verify REAL
  JGraphT library code through JPF/SPF.

  Expected result: JPF will FAIL with:
  'Choco does not support bitwise SHIFT'

  ...

  JPF OUTPUT

gov.nasa.jpf.symbc.numeric.ConstraintException:
    Choco does not support bitwise SHIFT

╔══════════════════════════════════════════════════════════╗
║  ANALYSIS                                                ║
╠══════════════════════════════════════════════════════════╣
║  JGraphT uses HashMap internally.                        ║
║  HashMap.hash() uses bitwise >>> (unsigned right shift). ║
║  Choco constraint solver CANNOT handle symbolic bitwise. ║
║                                                          ║
║  CONCLUSION: Production libs using HashMap/HashSet       ║
║  cannot be symbolically verified via JPF/Choco.          ║
╚══════════════════════════════════════════════════════════╝
```

This is **not** a bug in JGraphT or in the tool. It documents a fundamental incompatibility between production library internals (HashMap's bitwise hashing) and the Choco constraint solver's capabilities.

---

### Example 5: Verify a Custom Data Structure

You can verify any Java class by providing a `.java` file and a `.txt` contracts file.

**Step 1** — Create your data structure. Example `MyStack.java`:

```java
import java.util.LinkedList;

public class MyStack {
    private LinkedList<Integer> data = new LinkedList<>();
    private int capacity = 3;

    public void push(int val) { if (!isFull()) data.addLast(val); }
    public int pop() { return data.removeLast(); }
    public boolean contains(int val) { return data.contains(val); }
    public boolean isEmpty() { return data.isEmpty(); }
    public boolean isFull() { return data.size() >= capacity; }
    public int size() { return data.size(); }
    public String toString() { return "MyStack" + data.toString(); }
}
```

**Step 2** — Write contracts. Example `MyStack_contracts.txt`:

```
# Valid contracts
{!isFull()} push(p1) {size() == oldSize + 1}
{!isFull()} push(p1) {contains(p1)}
{isEmpty()} push(p1) {size() == 1}
{!isEmpty()} pop() {size() == oldSize - 1}

# Intentional bug — push should increase size, not keep it
{!isFull() && size() == 1} push(p1) {size() == 1}
```

**Step 3** — Create a JPF config. Example `verify_mystack.jpf`:

```
target=GenericContractsTest
target.args=MyStack,MyStack_contracts.txt
classpath=.
vm.insn_factory.class=gov.nasa.jpf.symbc.SymbolicInstructionFactory
listener=gov.nasa.jpf.symbc.SymbolicListener
symbolic.min_int=-10
symbolic.max_int=10
symbolic.debug=true
symbolic.lazy=true
search.multiple_errors=true
```

**Step 4** — Place all three files in the repo directory and run:

```bash
./master_setup.sh --verify MyStack verify_mystack.jpf
```

**Expected output:**

```
[+] VALIDATED: {!isFull()} push(p1) {size() == oldSize + 1}
[+] VALIDATED: {!isFull()} push(p1) {contains(p1)}
[+] VALIDATED: {isEmpty()} push(p1) {size() == 1}
[+] VALIDATED: {!isEmpty()} pop() {size() == oldSize - 1}

[!!!] VIOLATION DETECTED!
      Contract: {!isFull() && size() == 1} push(p1) {size() == 1}
      State: MyStack[-6, 3]
```

---

## Using SPFVerifierUI (Desktop GUI)

SPFVerifierUI is a Java Swing desktop application that replaces the 4-step terminal workflow with a single-click interface.

**Launch:**

```bash
./master_setup.sh --ui
```

**Workflow:**

1. Click **"> select file"** under `*.java` and pick your data structure file
2. Click **"> select file"** under `*.txt` and pick your contracts file
3. Click **"[ run verification ]"**
4. Results appear color-coded: green (PASS), red (FAIL), gray (UNKNOWN)
5. Click **"// raw jpf output [toggle]"** to see the full JPF log

**Settings:** Click `[ settings ]` in the top-right to configure JPF paths, Java 8 home, or add extra library JARs. When launched via `master_setup.sh`, all paths are pre-configured automatically through environment variables.

> **Note:** SPFVerifierUI requires a graphical display.
> - **Linux:** Works out of the box on desktop environments (GNOME, KDE, etc.). Over SSH, use `ssh -X user@host` for X forwarding.
> - **Windows (WSL2):** Windows 11 has built-in GUI support for WSL apps — the Swing window opens directly on your Windows desktop. On Windows 10, install [VcXsrv](https://sourceforge.net/projects/vcxsrv/) or [X410](https://x410.dev/), launch it, then set `export DISPLAY=:0` inside WSL before running `--ui`.

---

## Contract Format Reference

Each contract is a Hoare Triple on a single line:

```
{precondition} method(arguments) {postcondition}
```

**Rules:**

- Lines starting with `#` or `//` are comments
- Empty lines are ignored
- `p1`, `p2` are symbolic integer arguments passed to the method
- `b1` is a symbolic background variable (used for preservation contracts)
- `oldSize` captures `size()` before method execution — available in postconditions

**Examples:**

```
# Adding to a non-full structure increases size by 1
{!isFull()} enqueue(p1) {size() == oldSize + 1}

# After adding, the element is findable
{!isFull()} enqueue(p1) {contains(p1)}

# Removing a contained element makes it absent
{contains(p1)} remove(p1) {!contains(p1)}

# Adding p1 doesn't affect unrelated element b1
{!contains(b1) && p1 != b1} enqueue(p1) {!contains(b1)}

# Removing from a single-element structure empties it
{size() == 1 && contains(p1)} remove(p1) {isEmpty()}
```

**Available predicates in contracts:**

| Predicate | Description |
|-----------|-------------|
| `isEmpty()` / `isempty()` | True if the structure has no elements |
| `isFull()` / `isfull()` | True if the structure is at capacity |
| `contains(p1)` | True if value `p1` exists in the structure |
| `size()` | Returns the current number of elements |
| `oldSize` | Snapshot of `size()` taken before method execution |

**Supported operators:** `==`, `!=`, `>`, `<`, `>=`, `<=`, `&&`, `||`, `!`, `+`, `-`

---

## Project Structure

```
contract-verifier/
├── master_setup.sh                  # Setup + run script (entry point)
├── README.md                        # This file
├── .gitignore
│
│   Core Engine
├── GenericContractsTest.java        # Reads contracts, runs symbolic verification
├── DispatcherGenerator.java         # Generates method dispatcher via Reflection
├── GeneratedDispatcher.java         # Sample generated dispatcher (BoundedQueue)
│
│   Data Structures
├── BoundedQueue.java                # FIFO queue, capacity 3
├── BoundedList.java                 # LIFO list with positional insert, capacity 3
│
│   Contracts
├── BoundedQueue_contracts.txt       # 8 valid + 3 buggy contracts
├── BoundedList_contracts.txt        # 7 valid + 2 buggy contracts
├── JGraphTWrapper_contracts.txt     # Contracts for JGraphT experiment
├── JGraphTDirectedWrapper_contracts.txt
├── ScalifiedWrapper_contracts.txt   # Contracts for Scalified experiment
│
│   Library Wrappers
├── JGraphTWrapper.java              # Wraps JGraphT SimpleGraph
├── JGraphTDirectedWrapper.java      # Wraps JGraphT DirectedPseudograph
├── ScalifiedWrapper.java            # Wraps Scalified ArrayMultiTreeNode
├── ScalifiedWrapper_BUGGY.java      # Buggy version (target.remove bug)
├── ScalifiedWrapper_FIXED.java      # Corrected version (parent.dropSubtree)
│
│   Desktop Application
├── SPFVerifierUI.java               # Java Swing GUI
│
│   Configuration
└── generic_verify.jpf               # Default JPF config template
```

External dependencies installed by the setup script (not in this repo):

| Component | Install Location | Purpose |
|-----------|-----------------|---------|
| OpenJDK 8 | System package | JVM required by JPF |
| jpf-core | `~/jpf-core/` | NASA Java PathFinder |
| jpf-symbc | `~/jpf-symbc/` | Symbolic PathFinder extension |
| JGraphT 0.9.2 JAR | `~/libs/` | Graph library (experiment) |
| Scalified 0.2.5 JAR | `~/libs/` | Tree library (experiment) |

### Supported Platforms

| Platform | Method | Tested |
|----------|--------|--------|
| Ubuntu 20.04 / 22.04 / 24.04 | Native | ✓ |
| Debian 11 / 12 | Native | ✓ |
| Fedora 38 / 39 / 40 | Native | ✓ |
| Arch / Manjaro | Native | ✓ |
| openSUSE | Native | ✓ |
| Windows 10 (2004+) | WSL 2 (Ubuntu) | ✓ |
| Windows 11 | WSL 2 (Ubuntu) | ✓ |
| macOS | Not supported | ✗ |

---

## How the Pipeline Works

```
  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────┐
  │ Target Class │────▶│  Dispatcher  │────▶│  Generated   │────▶│  Contracts   │────▶│   JPF    │
  │   (.java)    │     │  Generator   │     │  Dispatcher  │     │  Test Engine │     │   SPF    │
  └──────────────┘     └──────────────┘     └──────────────┘     └──────────────┘     └────┬─────┘
                                                                                          │
  ┌──────────────┐                                                          ┌──────────────┴──────┐
  │  Contracts   │─────────────────────────────────────────────────────────▶│  ✓ VALIDATED        │
  │   (.txt)     │                                                          │  ✗ VIOLATION        │
  └──────────────┘                                                          └─────────────────────┘
```

**Step 1** — `DispatcherGenerator` loads the target class via `Class.forName()`, inspects its methods using `getDeclaredMethods()`, and generates a `GeneratedDispatcher.java` that maps string method names from contracts to actual Java method calls.

**Step 2** — `GenericContractsTest` reads the contracts file, parses each Hoare triple, creates symbolic variables via `Debug.makeSymbolicInteger()`, evaluates preconditions, captures `oldSize`, executes the method through the Dispatcher, and checks postconditions.

**Step 3** — JPF runs the test under symbolic execution. SPF replaces concrete values with symbols and uses the Choco constraint solver to determine path feasibility. Every branch is explored. If any path violates a postcondition, JPF reports the concrete counterexample values.

---

## Troubleshooting

### General

| Problem | Solution |
|---------|----------|
| `openjdk-8-jdk` not found in repos | The script tries Adoptium Temurin as a fallback. If that fails, install manually: `sudo apt install openjdk-8-jdk` |
| `jpf-core build FAILED` | Ensure Java 8 is the active JVM: `java -version` should show `1.8.x`. If another version is active, run `sudo update-alternatives --config java` |
| `Choco does not support bitwise SHIFT` | Expected behavior for JGraphT experiments. Not an error — it is a documented solver limitation |
| SPFVerifierUI does not open | Requires a graphical display. See the [Desktop GUI](#using-spfverifierui-desktop-gui) section for platform-specific instructions |
| `DispatcherGenerator: ClassNotFoundException` | The target `.java` file must be compiled first. The `--verify` command handles this automatically |
| `master_setup.sh: Permission denied` | Run `chmod +x master_setup.sh` first |
| Contracts show `[UNKNOWN]` in UI | The contract format may be incorrect, or JPF timed out. Check the raw log tab for details |

### Windows (WSL) Specific

| Problem | Solution |
|---------|----------|
| `wsl --install` says "not recognized" | You need Windows 10 version 2004+ or Windows 11. Update Windows first, or enable WSL manually via "Turn Windows features on or off" → check "Windows Subsystem for Linux" |
| `./master_setup.sh: /bin/bash^M: bad interpreter` | The script has Windows line endings (CRLF). Fix with: `sed -i 's/\r$//' master_setup.sh` or `dos2unix master_setup.sh` |
| SPFVerifierUI blank/no window on WSL | On Windows 10 WSL, GUI apps need an X server. Install [VcXsrv](https://sourceforge.net/projects/vcxsrv/), launch it with "Disable access control" checked, then run `export DISPLAY=:0` in your WSL terminal before launching the UI. Windows 11 WSL2 handles this automatically |
| `git clone` is very slow in WSL | Clone into the WSL filesystem (`~/`), not into `/mnt/c/`. The `/mnt/c/` mount has significant I/O overhead |
| `ant build` fails with "out of memory" in WSL | WSL has limited default memory. Create `%UserProfile%\.wslconfig` with `[wsl2]` and `memory=4GB`, then restart WSL with `wsl --shutdown` |

---

## CLI Reference

```bash
./master_setup.sh                                   # Interactive menu
./master_setup.sh --setup                            # Full installation
./master_setup.sh --health                           # Check system readiness
./master_setup.sh --run                              # Verify all core data structures
./master_setup.sh --verify <Class> <config.jpf>      # Verify a specific class
./master_setup.sh --experiment scalified              # Run Scalified experiment
./master_setup.sh --experiment jgrapht                # Run JGraphT experiment
./master_setup.sh --experiment all                    # Run all experiments
./master_setup.sh --ui                               # Launch desktop GUI
./master_setup.sh --all                              # Full setup + all verifications
./master_setup.sh --help                             # Show help
```

---

## Authors

- **Aryan Kumar** 
- **Alex Toppo** 

Under the guidance of **Prof. Sandip Ghosal**, Department of Computer Science & Engineering, Birla Institute of Technology, Mesra.

---

## License

This project is for academic and research purposes. JPF and SPF are governed by their respective Apache 2.0 licenses.
