# Symbolic Execution for Contract Validation

This project is a generic verification engine built on top of Java PathFinder (JPF) and Symbolic PathFinder (SPF). It takes a Java data structure and a set of Hoare Logic contracts, then uses symbolic execution to verify whether those contracts hold across all possible inputs. If a contract is violated, it gives you the exact counterexample.

## Prerequisites

You need a Linux system (Ubuntu, Debian, Fedora, Arch, or openSUSE). Windows users need WSL — run `wsl --install` in PowerShell as admin, restart, and use the Ubuntu terminal from there.

You don't need to install Java, Git, or Ant manually. The setup script handles all of that.

## Installation

Clone the repo and run the setup script:

```
git clone https://github.com/sandipghosal/contract-verifier.git
cd contract-verifier
chmod +x master_setup.sh
./master_setup.sh --setup
```

This installs OpenJDK 8, Git, Ant, builds jpf-core and jpf-symbc, downloads the library JARs (JGraphT and Scalified), and compiles the engine. Takes a few minutes on first run.

After setup, check that everything is working:

```
./master_setup.sh --health
./master_setup.sh              # interactive menu
```

## Project structure

```
contract-verifier/
├── master_setup.sh            # setup + run script
├── README.md
├── .gitignore
│
├── src/                       # core engine and data structures
│   ├── GenericContractsTest.java
│   ├── DispatcherGenerator.java
│   ├── GeneratedDispatcher.java
│   ├── BoundedQueue.java
│   ├── BoundedList.java
│   ├── BoundedQueue_contracts.txt
│   ├── BoundedList_contracts.txt
│   ├── SPFVerifierUI.java
│   └── generic_verify.jpf
│
└── exp/                       # library experiment files
    ├── JGraphTWrapper.java
    ├── JGraphTDirectedWrapper.java
    ├── ScalifiedWrapper.java
    ├── ScalifiedWrapper_BUGGY.java
    ├── ScalifiedWrapper_FIXED.java
    └── *_contracts.txt
```

The engine needs all files in a flat directory at runtime. The script handles this by copying files from `src/` and `exp/` into a temporary `build/` folder before each run. You don't need to worry about this — it happens automatically.

## How it works

The engine has three parts. `DispatcherGenerator` loads your Java class using reflection and generates a dispatcher that maps method names from the contracts file to actual method calls. `GenericContractsTest` reads the contracts, creates symbolic variables, evaluates preconditions, runs the method, and checks postconditions. JPF then explores every possible execution path using the Choco constraint solver. If any path breaks a postcondition, it reports the exact values that caused the failure.

## Writing contracts

Each contract is a Hoare triple written on one line:

```
{precondition} method(arguments) {postcondition}
```

`p1` and `p2` are symbolic integer arguments. `b1` is a background variable for preservation checks. `oldSize` captures `size()` before the method runs. Lines starting with `#` or `//` are comments.

Example contracts:

```
{!isFull()} enqueue(p1) {size() == oldSize + 1}
{!isFull()} enqueue(p1) {contains(p1)}
{contains(p1)} remove(p1) {!contains(p1)}
{!contains(b1) && p1 != b1} enqueue(p1) {!contains(b1)}
```

## Examples

### Verifying BoundedQueue

BoundedQueue is a FIFO queue backed by LinkedList with capacity 3. Its contracts file has 8 valid contracts and 3 intentionally wrong ones.

```
./master_setup.sh --verify BoundedQueue generic_verify.jpf
```

The valid contracts all print VALIDATED. The buggy ones get caught with counterexamples:

```
[+] VALIDATED: {!isFull()} enqueue(p1) {size() == oldSize + 1}
[+] VALIDATED: {!isFull()} enqueue(p1) {contains(p1)}
[+] VALIDATED: {isEmpty()} enqueue(p1) {size() == 1}
...
[!!!] VIOLATION DETECTED!
      Contract: {!isFull() && size() == 2} enqueue(p1) {size() == 2}
      State: BoundedQueue[1, -3, 7]
```

The violation makes sense — enqueueing into a queue of size 2 makes it size 3, not 2.

### Verifying BoundedList

BoundedList is a LIFO structure with a branching insert method. If p1 exists in the list, p2 gets inserted after p1, otherwise p2 is appended.

```
./master_setup.sh --verify BoundedList generic_verify.jpf
```

7 valid contracts pass, 2 intentional bugs get caught. The symbolic execution explores both branches of insert() automatically.

### Scalified library experiment

This one verifies a real external library (Scalified Tree) through the engine. It runs a buggy wrapper first, then a fixed one.

```
./master_setup.sh --experiment scalified
```

The buggy wrapper has a `remove()` that calls `target.remove(target)` — which is wrong API usage. It asks a node to search its own children for itself. A normal unit test removing the root would pass, but symbolic execution finds a tree state where it fails (removing a leaf node). The fixed wrapper uses `parent.dropSubtree(child)` and all contracts pass.

### JGraphT library experiment

This one intentionally fails, and that's the point.

```
./master_setup.sh --experiment jgrapht
```

JGraphT uses HashMap internally, and `HashMap.hash()` uses bitwise shift operations. The Choco constraint solver can't handle symbolic bitwise operations, so JPF crashes with "Choco does not support bitwise SHIFT". This documents a real limitation — production libraries that rely on HashMap/HashSet can't be symbolically verified through JPF/Choco.

### Verifying your own data structure

Put your Java file and contracts file in `src/`. Create a JPF config file (copy `generic_verify.jpf` and change the `target.args` line). Then run:

```
./master_setup.sh --verify YourClass your_config.jpf
```

Quick example — create `src/MyStack.java`:

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

Create `src/MyStack_contracts.txt`:

```
{!isFull()} push(p1) {size() == oldSize + 1}
{!isFull()} push(p1) {contains(p1)}
{isEmpty()} push(p1) {size() == 1}
{!isEmpty()} pop() {size() == oldSize - 1}
```

Create `src/verify_mystack.jpf` (copy `generic_verify.jpf`, change `target.args=MyStack,MyStack_contracts.txt`), then:

```
./master_setup.sh --verify MyStack verify_mystack.jpf
```

## Desktop GUI

There's also a Swing GUI if you don't want to use the terminal:

```
./master_setup.sh --ui
```

Pick your .java file, pick your contracts .txt file, hit run. Results show up color-coded. Needs a graphical display — on WSL you might need an X server (Windows 10) or it just works (Windows 11).

## CLI quick reference

```
./master_setup.sh                  # interactive menu
./master_setup.sh --setup          # full installation
./master_setup.sh --health         # check if everything is working
./master_setup.sh --verify X Y     # verify class X with config Y
./master_setup.sh --experiment Z   # run library experiment (scalified/jgrapht/all)
./master_setup.sh --ui             # launch desktop GUI
./master_setup.sh --run            # verify all built-in data structures
./master_setup.sh --all            # setup + run everything
```


## License

Academic and research use. JPF and SPF are under Apache 2.0.
