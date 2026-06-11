import com.scalified.tree.TreeNode;
import com.scalified.tree.multinode.ArrayMultiTreeNode;

/**
 * ============================================================
 * ScalifiedWrapper — FIXED VERSION
 * ============================================================
 * Bug fix: remove() now finds the PARENT node and calls
 * parent.dropSubtree(child) instead of target.remove(target).
 * 
 * JPF should now VALIDATE all contracts including remove().
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

    // ========== FIXED remove() ==========
    // Fix: Find parent, then call parent.dropSubtree(child)
    public Integer remove(Integer value) {
        if (value == null || root == null) return null;

        if (root.data().equals(value)) {
            root = null;
            return value;
        }

        // Find the parent of the node to remove
        TreeNode<Integer> parent = findParent(root, value);
        if (parent == null) return null;

        // Remove child from parent (correct Scalified API usage)
        for (TreeNode<Integer> child : parent.subtrees()) {
            if (child.data().equals(value)) {
                parent.dropSubtree(child);
                return value;
            }
        }
        return null;
    }

    // Helper: find the parent of a node with given value
    private TreeNode<Integer> findParent(TreeNode<Integer> current, Integer value) {
        for (TreeNode<Integer> child : current.subtrees()) {
            if (child.data().equals(value)) {
                return current;
            }
            TreeNode<Integer> found = findParent(child, value);
            if (found != null) return found;
        }
        return null;
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
