package linked_list_set;

import kotlinx.atomicfu.AtomicRef;

public class SetImpl implements Set {

    private class Window {
        Node cur, next;
    }

    private final Node head = new Node(Integer.MIN_VALUE, new Node(Integer.MAX_VALUE, null));

    private Window findWindow(int x) {
        retry:
        while (true) {
            Window w = new Window();
            w.cur = head;
            w.next = getNext(w.cur);
            Node node;
            while (w.next.x < x) {
                node = w.next.next.getValue();
                if (node instanceof Removed) {
                    if (!w.cur.next.compareAndSet(w.next, node.next.getValue())) {
                        continue retry;
                    } else {
                        w.next = node.next.getValue();
                    }
                } else {
                    w.cur = w.next;
                    w.next = getNext(w.cur);
                }
            }
            node = w.next.next.getValue();
            if (node instanceof Removed) {
                w.cur.next.compareAndSet(w.next, node.next.getValue());
            } else {
                return w;
            }
        }
    }

    @Override
    public boolean add(int x) {
        while (true) {
            Window w = findWindow(x);
            if (w.next.x == x) {
                return false;
            } else {
                Node newNode = new Node(x, w.next);
                if (w.cur.next.compareAndSet(w.next, newNode)) {
                    return true;
                }
            }
        }
    }

    @Override
    public boolean remove(int x) {
        while (true) {
            Window w = findWindow(x);
            if (w.next.x != x) {
                return false;
            } else {
                Node node = getNext(w.next);
                if (w.next.next.compareAndSet(node, new Removed(node))) {
                    w.cur.next.compareAndSet(w.next, node);
                    return true;
                }
            }
        }
    }

    @Override
    public boolean contains(int x) {
        Window w = findWindow(x);
        return w.next.x == x;
    }

    private Node getNext(Node node) {
        Node next = node.next.getValue();
        if (next instanceof Removed) {
            return next.next.getValue();
        }
        return next;
    }

    class Node {
        AtomicRef<Node> next;
        int x;

        Node(int x, Node next) {
            this.next = new AtomicRef<>(next);
            this.x = x;
        }
    }

    class Removed extends Node {
        Removed(Node next) {
            super(Integer.MIN_VALUE, next);
        }
    }
}