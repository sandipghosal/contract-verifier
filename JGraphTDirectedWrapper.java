import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

/**
 * ============================================================
 * JGraphT Directed Graph Wrapper for Symbolic Verification
 * ============================================================
 * 
 * Uses REAL JGraphT SimpleDirectedGraph internally.
 * Directed edges: put(v1, v2) creates v1 → v2 only.
 * 
 * Every call goes through actual JGraphT code.
 * ============================================================
 */
public class JGraphTDirectedWrapper {

    private Graph<Integer, DefaultEdge> graph;

    public JGraphTDirectedWrapper() {
        this.graph = new SimpleDirectedGraph<>(DefaultEdge.class);
    }

    public void add(Integer vertex) {
        if (vertex == null) return;
        graph.addVertex(vertex);
    }

    public Integer remove(Integer vertex) {
        if (vertex == null) return null;
        if (!graph.containsVertex(vertex)) return null;
        graph.removeVertex(vertex);
        return vertex;
    }

    public void put(Integer v1, Integer v2) {
        if (v1 == null || v2 == null) return;
        if (v1.equals(v2)) return;
        graph.addVertex(v1);
        graph.addVertex(v2);
        if (!graph.containsEdge(v1, v2)) {
            graph.addEdge(v1, v2);
        }
    }

    public boolean contains(Integer vertex) {
        if (vertex == null) return false;
        return graph.containsVertex(vertex);
    }

    public boolean isEmpty() {
        return graph.vertexSet().isEmpty();
    }

    public int size() {
        return graph.vertexSet().size();
    }

    @Override
    public String toString() {
        return graph.toString();
    }
}
