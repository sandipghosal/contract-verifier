import java.util.LinkedList;

public class BoundedQueue {
    
    public static final int DEFAULT_MAX_CAPACITY = 3;
    private LinkedList<Integer> queue;
    private int maxCapacity;

    public BoundedQueue() {
        this.maxCapacity = DEFAULT_MAX_CAPACITY;
        this.queue = new LinkedList<>();
    }

    public BoundedQueue(int capacity) {
        this.maxCapacity = capacity;
        this.queue = new LinkedList<>();
    }

    public void enqueue(Integer e) {
        if (!isFull()) {
            queue.addLast(e);
        }
    }

    public Integer dequeue() {
        if (isEmpty()) {
            return null;
        }
        return queue.removeFirst();
    }

    public Integer peek() {
        if (isEmpty()) {
            return null;
        }
        return queue.getFirst();
    }

    public boolean contains(Integer e) {
        return queue.contains(e);
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public boolean isFull() {
        return queue.size() >= maxCapacity;
    }

    public int size() {
        return queue.size();
    }

    @Override
    public String toString() {
        return "BoundedQueue" + queue.toString();
    }
}
