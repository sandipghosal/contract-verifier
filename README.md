<h1>Symbolic Execution for Contract Validation</h1>

This project is a generic verification engine built on top of Java PathFinder (JPF) and Symbolic PathFinder (SPF). It takes a Java data structure and a set of Hoare Logic contracts, then uses symbolic execution to verify whether those contracts hold across all possible inputs. If a contract is violated, it gives you the exact counterexample.

<h2>Prerequisites</h2>
You need a Linux system (Ubuntu, Debian, Fedora, Arch, or openSUSE). Windows users need WSL run `wsl --install` in PowerShell as admin, restart, and use the Ubuntu terminal from there.
You don't need to install Java, Git, or Ant manually. The setup script handles all of that.

<h2>Installation</h2>
Clone the repo and run the setup script:

```
Step 1:git clone https://github.com/sandipghosal/contract-verifier.git
Step 2:cd contract-verifier
Step 3:chmod +x master_setup.sh
Step 4:./master_setup.sh --setup
```
This installs OpenJDK 8, Git, Ant, builds jpf-core and jpf-symbc, downloads the library JARs (JGraphT and Scalified), and compiles the engine. Takes a few minutes on first run.
After setup, check that everything is working:

```
Step 6:./master_setup.sh --health
Step 7:./master_setup.sh (For interactive menu)
```
<h2>How it works</h2>
The engine has three parts:
DispatcherGenerator loads your Java class using reflection and generates a dispatcher that maps method names from the contracts file to actual method calls. GenericContractsTest reads the contracts, creates symbolic variables, evaluates preconditions, runs the method, and checks postconditions. JPF then explores every possible execution path using the Choco constraint solver. If any path breaks a postcondition, it reports the exact values that caused the failure.

<h2>Writing contracts</h2>
Each contract is a Hoare triple written on one line:

```
{precondition} method(arguments) {postcondition}
```
p1 and p2 are symbolic integer arguments. b1 is a background variable for preservation checks. oldSize captures size() before the method runs. Lines starting with # or // are comments.
Example contracts:
```
{!isFull()} enqueue(p1) {size() == oldSize + 1}
{!isFull()} enqueue(p1) {contains(p1)}
{contains(p1)} remove(p1) {!contains(p1)}
{!contains(b1) && p1 != b1} enqueue(p1) {!contains(b1)}
```

<h2>Examples</h2>
<h3>Verifying BoundedQueue</h3>
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
The violation makes sense, enqueueing into a queue of size 2 makes it size 3, not 2.

<h3>Verifying BoundedList</h3>
BoundedList is a LIFO structure with a branching insert method. If p1 exists in the list, p2 gets inserted after p1, otherwise p2 is appended.

```
./master_setup.sh --verify BoundedList generic_verify.jpf
```
7 valid contracts pass, 2 intentional bugs get caught. The symbolic execution explores both branches of insert() automatically.

<h3>Scalified library experiment</h3>
This one verifies a real external library (Scalified Tree) through the engine. It runs a buggy wrapper first, then a fixed one.

```
./master_setup.sh --experiment scalified
```
The buggy wrapper has a remove() that calls target.remove(target) which is wrong API usage, it asks a node to search its own children for itself. A normal unit test removing the root would pass, but symbolic execution finds a tree state where it fails (removing a leaf node). The fixed wrapper uses parent.dropSubtree(child) and all contracts pass.

<h3>JGraphT library experiment</h3>
This one intentionally fails and that's the point.

```
./master_setup.sh --experiment jgrapht
```
JGraphT uses HashMap internally, and HashMap.hash() uses bitwise shift operations. The Choco constraint solver can't handle symbolic bitwise operations, so JPF crashes with "Choco does not support bitwise SHIFT". This documents a real limitation - production libraries that rely on HashMap/HashSet can't be symbolically verified through JPF/Choco.

<h3>Verifying your own data structure</h3>
Put your Java file and contracts file in the repo directory. Create a JPF config file (copy generic_verify.jpf and change the target.args line). Then run:

```
./master_setup.sh --verify YourClass your_config.jpf
```
There's a full MyStack example in the original documentation if you need a template.

<h2>Desktop GUI</h2>
There's also a Swing GUI if you don't want to use the terminal:

```
./master_setup.sh --ui
```
Pick your .java file, pick your contracts .txt file, hit run. Results show up color-coded. Needs a graphical display, on WSL you might need an X server (Windows 10) or it just works (Windows 11).

<h2>CLI quick reference</h2>

```
./master_setup.sh                  - interactive menu
./master_setup.sh --setup          - full installation
./master_setup.sh --health         - check if everything is working
./master_setup.sh --verify X Y     - verify class X with config Y
./master_setup.sh --experiment Z   - run library experiment (scalified/jgrapht/all)
./master_setup.sh --ui             - launch desktop GUI
./master_setup.sh --run            - verify all built-in data structures
./master_setup.sh --all            - setup + run everything
```

<h2>License</h2>

Academic and research use. JPF and SPF are under Apache 2.0.
