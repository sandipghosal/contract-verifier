import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

/**
 * ============================================================
 * JGraphT Wrapper for Symbolic Verification
 * ============================================================
 * 
 * This class uses the REAL JGraphT library internally.
 * Every method call goes through actual JGraphT code.
 * 
 * If JGraphT has a bug in addVertex(), containsVertex(), 
 * removeVertex(), or edge handling — our Hoare Logic 
 * contracts will detect it via symbolic execution.
 * 
 * The wrapper is needed because:
 *   - JGraphT constructor needs Class<E> arg → we provide no-arg
 *   - JGraphT method names (addVertex) → mapped to engine-compatible names (add)
 *   - JGraphT uses generics → wrapper uses Integer primitives
 *   - JGraphT returns complex objects → wrapper returns primitives
 * 
 * Every single operation delegates to REAL JGraphT code:
 *   add(v)       → graph.addVertex(v)        [REAL JGraphT call]
 *   remove(v)    → graph.removeVertex(v)      [REAL JGraphT call]
 *   put(v1,v2)   → graph.addEdge(v1,v2)      [REAL JGraphT call]
 *   contains(v)  → graph.containsVertex(v)    [REAL JGraphT call]
 *   size()       → graph.vertexSet().size()   [REAL JGraphT call]
 *   isEmpty()    → graph.vertexSet().isEmpty() [REAL JGraphT call]
 * ============================================================
 */
public class JGraphTWrapper {

    // REAL JGraphT graph object
    private Graph<Integer, DefaultEdge> graph;

    public JGraphTWrapper() {
        // Calls REAL JGraphT constructor
        this.graph = new SimpleGraph<>(DefaultEdge.class);
    }

    // ========== Delegates to: graph.addVertex(vertex) ==========
    public void add(Integer vertex) {
        if (vertex == null) return;
        graph.addVertex(vertex);  // REAL JGraphT method
    }

    // ========== Delegates to: graph.removeVertex(vertex) ==========
    public Integer remove(Integer vertex) {
        if (vertex == null) return null;
        if (!graph.containsVertex(vertex)) return null;
        graph.removeVertex(vertex);  // REAL JGraphT method
        return vertex;
    }

    // ========== Delegates to: graph.addEdge(v1, v2) ==========
    public void put(Integer v1, Integer v2) {
        if (v1 == null || v2 == null) return;
        if (v1.equals(v2)) return;  // SimpleGraph: no self-loops
        graph.addVertex(v1);   // REAL JGraphT method
        graph.addVertex(v2);   // REAL JGraphT method
        if (!graph.containsEdge(v1, v2)) {
            graph.addEdge(v1, v2);  // REAL JGraphT method
        }
    }

    // ========== Delegates to: graph.containsVertex(vertex) ==========
    public boolean contains(Integer vertex) {
        if (vertex == null) return false;
        return graph.containsVertex(vertex);  // REAL JGraphT method
    }

    // ========== Delegates to: graph.vertexSet().isEmpty() ==========
    public boolean isEmpty() {
        return graph.vertexSet().isEmpty();  // REAL JGraphT method
    }

    // ========== Delegates to: graph.vertexSet().size() ==========
    public int size() {
        return graph.vertexSet().size();  // REAL JGraphT method
    }

    @Override
    public String toString() {
        return graph.toString();  // REAL JGraphT method
    }
}
