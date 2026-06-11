import com.scalified.tree.TreeNode;
import com.scalified.tree.multinode.ArrayMultiTreeNode;

/**
 * ============================================================
 * ScalifiedWrapper — BUGGY VERSION (for demonstration)
 * ============================================================
 * This version has a bug in remove() that JPF will catch.
 * The bug: target.remove(target) asks a node to remove itself
 * from its own children — which doesn't work in Scalified's API.
 * ============================================================
 */
public class ScalifiedWrapper {

    private ArrayMultiTreeNode<Integer> root;

    public ScalifiedWrapper() {
        this.root = null;
    }

    public void add(Integer value) {
        if (value == null) return;
        if (root == null) {
            root = new ArrayMultiTreeNode<>(value);
            return;
        }
        ArrayMultiTreeNode<Integer> newNode = new ArrayMultiTreeNode<>(value);
        root.add(newNode);
    }

    // ========== BUGGY remove() ==========
    // Bug: target.remove(target) doesn't actually remove the node!
    // Scalified's API requires calling remove on the PARENT, not on the node itself.
    public Integer remove(Integer value) {
        if (value == null || root == null) return null;

        if (root.data().equals(value)) {
            root = null;
            return value;
        }

        TreeNode<Integer> target = findNode(root, value);
        if (target == null) return null;

        // BUG: This asks node to remove itself from its own children list
        // Correct approach: find parent, then parent.dropSubtree(target)
        target.remove(target);
        return value;
    }

    public boolean contains(Integer value) {
        if (value == null || root == null) return false;
        return findNode(root, value) != null;
    }

    private TreeNode<Integer> findNode(TreeNode<Integer> current, Integer value) {
        if (current.data().equals(value)) return current;
        for (TreeNode<Integer> child : current.subtrees()) {
            TreeNode<Integer> found = findNode(child, value);
            if (found != null) return found;
        }
        return null;
    }

    public boolean isLeaf() {
        if (root == null) return true;
        return root.isLeaf();
    }

    public boolean isEmpty() {
        return root == null;
    }

    public int size() {
        if (root == null) return 0;
        return (int) root.size();
    }

    @Override
    public String toString() {
        if (root == null) return "ScalifiedWrapper{empty}";
        return root.toString();
    }
}
