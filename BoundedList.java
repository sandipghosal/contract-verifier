import java.util.LinkedList;

public class BoundedList {
    public static final int DEFAULT_MAX_CAPACITY = 3;
    private LinkedList<Integer> list;
    private int maxCapacity;

    public BoundedList() {
        this.maxCapacity = DEFAULT_MAX_CAPACITY;
        this.list = new LinkedList<Integer>();
    }

    public void push(Integer e) {
        if (maxCapacity > list.size()) {
            list.push(e);
        }
    }

    public Integer pop() {
        if (list.isEmpty()) return null;
        return list.pop();
    }

    public boolean contains(Integer e) {
        return list.contains(e);
    }

    public void insert(Integer e1, Integer e2) {
        if (maxCapacity > list.size()) {
            int i = 0;
            for (Integer el : list) {
                if (el != null && el.equals(e1)) {
                    list.add(i + 1, e2);
                    return;
                }
                i++;
            }
            list.addLast(e2);
        }
    }

    public boolean isempty() {
        return list.isEmpty();
    }

    public boolean isfull() {
        return list.size() >= maxCapacity;
    }

    public int size() {
        return list.size();
    }

    public String toString() {
        return list.toString();
    }
}
