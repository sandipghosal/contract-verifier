import gov.nasa.jpf.symbc.Debug;

import java.io.*;
import java.util.*;

/**
 * ============================================================
 * GENERIC SYMBOLIC VERIFICATION ENGINE
 * ============================================================
 * Phase 2: Run with JPF/SPF
 * 
 * This engine works with auto-generated dispatchers created by
 * DispatcherGenerator. No hardcoded class-specific code.
 * 
 * Usage:
 *   1. First generate dispatcher: java DispatcherGenerator MyClass
 *   2. Compile all: javac *.java
 *   3. Run with JPF: java -jar RunJPF.jar generic_verify.jpf
 * 
 * JPF args: <ClassName> <ContractsFile>
 * ============================================================
 */
public class GenericContractsTest {

    // ========================================================
    // DISPATCHER INTERFACE
    // ========================================================
    public interface Dispatcher {
        Object createInstance();
        void executeStateInit(Object instance, int stateVar);
        Object executeMethod(Object instance, String methodName, String[] args, Map<String, Integer> symVars);
        boolean evaluatePredicate(Object instance, String predicate, Map<String, Integer> symVars);
        int getSize(Object instance);
        String getClassName();
    }

    // ========================================================
    // CONTRACT REPRESENTATION
    // ========================================================
    static class Contract {
        String precondition;
        String postcondition;
        String methodName;
        String[] arguments;
        String originalLine;

        Contract(String pre, String post, String methodSig, String line) {
            this.precondition = pre.trim();
            this.postcondition = post.trim();
            this.originalLine = line;
            parseMethodSignature(methodSig);
        }

        private void parseMethodSignature(String methodSig) {
            int parenIndex = methodSig.indexOf('(');
            if (parenIndex != -1) {
                this.methodName = methodSig.substring(0, parenIndex).trim();
                String argsStr = methodSig.substring(parenIndex + 1, methodSig.lastIndexOf(')'));
                if (argsStr.trim().length() > 0) {
                    this.arguments = splitByComma(argsStr);
                } else {
                    this.arguments = new String[0];
                }
            } else {
                this.methodName = methodSig.trim();
                this.arguments = new String[0];
            }
        }
        
        private static String[] splitByComma(String s) {
            List<String> parts = new ArrayList<String>();
            int start = 0;
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) == ',') {
                    parts.add(s.substring(start, i).trim());
                    start = i + 1;
                }
            }
            parts.add(s.substring(start).trim());
            return parts.toArray(new String[0]);
        }
    }

    // ========================================================
    // RESERVED KEYWORDS
    // ========================================================
    private static final Set<String> RESERVED_KEYWORDS = new HashSet<String>();
    static {
        String[] keywords = {"int", "void", "boolean", "String", "Integer", "Double", "Boolean",
            "true", "false", "null", "return", "result", "oldSize", "size"};
        for (String k : keywords) {
            RESERVED_KEYWORDS.add(k);
        }
    }

    // Instance fields
    private Dispatcher dispatcher;
    private Object targetInstance;
    private Map<String, Integer> symbolicVars;
    private Object lastMethodResult;
    private int oldSize;

    // ========================================================
    // MAIN ENTRY POINT
    // ========================================================
    public static void main(String[] args) {
        String className = args.length > 0 ? args[0] : "BoundedList";
        String contractsFile = args.length > 1 ? args[1] : "contracts.txt";

        GenericContractsTest test = new GenericContractsTest();

        // Load the generated dispatcher
        test.dispatcher = new GeneratedDispatcher();

        System.out.println("[*] Verifying: " + test.dispatcher.getClassName());
        System.out.println("[*] Contracts: " + contractsFile);

        // Add method names from contracts to reserved keywords
        List<Contract> tempContracts = test.readContracts(contractsFile);
        for (Contract c : tempContracts) {
            RESERVED_KEYWORDS.add(c.methodName);
        }

        // Extract variable names from contracts
        Set<String> varNames = extractVariableNames(contractsFile);

        // Create symbolic variables
        test.symbolicVars = new HashMap<String, Integer>();
        test.symbolicVars.put("stateVar", Debug.makeSymbolicInteger("stateVar"));

        for (String name : varNames) {
            test.symbolicVars.put(name, Debug.makeSymbolicInteger(name));
        }

        // Create symbolic contract choice
        int contractIndex = Debug.makeSymbolicInteger("ContractChoice");

        // Run verification
        test.runVerification(contractIndex, contractsFile);
    }

    // ========================================================
    // VARIABLE NAME EXTRACTION (No Regex - JPF Safe)
    // ========================================================
    private static Set<String> extractVariableNames(String filename) {
        Set<String> vars = new HashSet<String>();

        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().startsWith("#") || line.trim().length() == 0) continue;

                StringBuilder word = new StringBuilder();
                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (Character.isLetterOrDigit(c) || c == '_') {
                        word.append(c);
                    } else {
                        if (word.length() > 0) {
                            String w = word.toString();
                            if (!RESERVED_KEYWORDS.contains(w) && !isNumeric(w) && Character.isLetter(w.charAt(0))) {
                                vars.add(w);
                            }
                            word = new StringBuilder();
                        }
                    }
                }
                if (word.length() > 0) {
                    String w = word.toString();
                    if (!RESERVED_KEYWORDS.contains(w) && !isNumeric(w) && Character.isLetter(w.charAt(0))) {
                        vars.add(w);
                    }
                }
            }
            br.close();
        } catch (IOException e) {
            System.err.println("[WARN] Could not read contracts file: " + e.getMessage());
        }

        return vars;
    }

    private static boolean isNumeric(String s) {
        if (s.length() == 0) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    // ========================================================
    // MAIN VERIFICATION LOGIC
    // ========================================================
    private void runVerification(int contractIndex, String contractsFile) {
        List<Contract> contracts = readContracts(contractsFile);

        if (contracts.isEmpty()) {
            System.out.println("[WARN] No contracts found in " + contractsFile);
            return;
        }

        Debug.assume(contractIndex >= 0 && contractIndex < contracts.size());

        Contract contract = contracts.get(contractIndex);
        System.out.println("\n[*] Verifying: " + contract.originalLine);

        int stateVar = symbolicVars.get("stateVar").intValue();
        targetInstance = dispatcher.createInstance();
        dispatcher.executeStateInit(targetInstance, stateVar);

        oldSize = dispatcher.getSize(targetInstance);

        boolean preHolds = evaluatePredicate(contract.precondition);

        Debug.assume(preHolds);

        System.out.println("[*] Precondition satisfied, executing: " + contract.methodName);

        lastMethodResult = dispatcher.executeMethod(
            targetInstance,
            contract.methodName,
            contract.arguments,
            symbolicVars
        );

        boolean postHolds = evaluatePredicate(contract.postcondition);

        if (!postHolds) {
            System.out.println("\n[!!!] VIOLATION DETECTED!");
            System.out.println("      Contract: " + contract.originalLine);
            System.out.println("      State: " + targetInstance.toString());
            assert false : "Contract violated: " + contract.originalLine;
        } else {
            System.out.println("[+] VALIDATED: " + contract.originalLine);
        }
    }

    // ========================================================
    // PREDICATE EVALUATION (No Regex - JPF Safe)
    // ========================================================
    private boolean evaluatePredicate(String pred) {
        pred = pred.trim();

        // Handle OR
        int orIdx = findOutsideParens(pred, "||");
        if (orIdx != -1) {
            String left = pred.substring(0, orIdx).trim();
            String right = pred.substring(orIdx + 2).trim();
            return evaluatePredicate(left) || evaluatePredicate(right);
        }

        // Handle AND
        int andIdx = findOutsideParens(pred, "&&");
        if (andIdx != -1) {
            String left = pred.substring(0, andIdx).trim();
            String right = pred.substring(andIdx + 2).trim();
            return evaluatePredicate(left) && evaluatePredicate(right);
        }

        // Handle NOT
        if (pred.startsWith("!") && !pred.startsWith("!=")) {
            String inner = pred.substring(1).trim();
            if (inner.startsWith("(") && inner.endsWith(")")) {
                inner = inner.substring(1, inner.length() - 1);
            }
            return !evaluatePredicate(inner);
        }

        // Handle parentheses
        if (pred.startsWith("(") && pred.endsWith(")") && isBalanced(pred)) {
            return evaluatePredicate(pred.substring(1, pred.length() - 1));
        }

        // Handle comparisons
        int idx;
        if ((idx = pred.indexOf("==")) != -1) {
            return evaluateComparison(pred, "==", idx);
        }
        if ((idx = pred.indexOf("!=")) != -1) {
            return evaluateComparison(pred, "!=", idx);
        }
        if ((idx = pred.indexOf(">=")) != -1) {
            return evaluateComparison(pred, ">=", idx);
        }
        if ((idx = pred.indexOf("<=")) != -1) {
            return evaluateComparison(pred, "<=", idx);
        }
        if ((idx = findSingleOperator(pred, '>')) != -1) {
            return evaluateComparison(pred, ">", idx);
        }
        if ((idx = findSingleOperator(pred, '<')) != -1) {
            return evaluateComparison(pred, "<", idx);
        }

        // Handle boolean literals
        if (pred.equals("true")) return true;
        if (pred.equals("false")) return false;

        // Delegate to dispatcher
        return dispatcher.evaluatePredicate(targetInstance, pred, symbolicVars);
    }

    private int findSingleOperator(String s, char op) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == op) {
                if (i + 1 < s.length() && s.charAt(i + 1) == '=') {
                    continue;
                }
                return i;
            }
        }
        return -1;
    }

    // ========================================================
    // COMPARISON EVALUATION
    // ========================================================
    private boolean evaluateComparison(String pred, String operator, int idx) {
        String leftStr = pred.substring(0, idx).trim();
        String rightStr = pred.substring(idx + operator.length()).trim();

        int left = evaluateExpression(leftStr);
        int right = evaluateExpression(rightStr);

        if (operator.equals("==")) return left == right;
        if (operator.equals("!=")) return left != right;
        if (operator.equals(">"))  return left > right;
        if (operator.equals("<"))  return left < right;
        if (operator.equals(">=")) return left >= right;
        if (operator.equals("<=")) return left <= right;

        return false;
    }

    // ========================================================
    // EXPRESSION EVALUATION
    // ========================================================
    private int evaluateExpression(String expr) {
        expr = expr.trim();

        // Handle addition
        int plusIdx = expr.lastIndexOf('+');
        if (plusIdx > 0) {
            String left = expr.substring(0, plusIdx);
            String right = expr.substring(plusIdx + 1);
            return evaluateExpression(left) + evaluateExpression(right);
        }

        // Handle subtraction
        int minusIdx = expr.lastIndexOf('-');
        if (minusIdx > 0) {
            String left = expr.substring(0, minusIdx);
            String right = expr.substring(minusIdx + 1);
            return evaluateExpression(left) - evaluateExpression(right);
        }

        // Special variables
        if (expr.equals("oldSize")) return oldSize;

        if (expr.equals("result")) {
            if (lastMethodResult instanceof Integer) {
                return ((Integer) lastMethodResult).intValue();
            }
            if (lastMethodResult == null) {
                return -9999;
            }
            return 0;
        }

        // Handle size()
        if (expr.equals("size()") || expr.equals("size")) {
            return dispatcher.getSize(targetInstance);
        }

        // Handle symbolic variables
        if (symbolicVars.containsKey(expr)) {
            return symbolicVars.get(expr).intValue();
        }

        // Handle literals
        try {
            return Integer.parseInt(expr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ========================================================
    // STRING HELPERS
    // ========================================================
    private int findOutsideParens(String s, String op) {
        int depth = 0;
        for (int i = 0; i <= s.length() - op.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (depth == 0 && s.substring(i, i + op.length()).equals(op)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isBalanced(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '(') depth++;
            else if (s.charAt(i) == ')') depth--;
            if (depth == 0 && i < s.length() - 1) return false;
        }
        return depth == 0;
    }

    // ========================================================
    // CONTRACT READER
    // ========================================================
    private List<Contract> readContracts(String filename) {
        List<Contract> contracts = new ArrayList<Contract>();

        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0 || line.startsWith("#")) continue;

                int firstOpen = line.indexOf('{');
                int firstClose = line.indexOf('}');
                int lastOpen = line.lastIndexOf('{');
                int lastClose = line.lastIndexOf('}');

                if (firstOpen != -1 && firstClose != -1 && lastOpen != -1 && lastClose != -1
                    && firstClose < lastOpen) {
                    String pre = line.substring(firstOpen + 1, firstClose).trim();
                    String method = line.substring(firstClose + 1, lastOpen).trim();
                    String post = line.substring(lastOpen + 1, lastClose).trim();

                    contracts.add(new Contract(pre, post, method, line));
                }
            }
            br.close();
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to read contracts: " + e.getMessage());
        }

        return contracts;
    }
}
