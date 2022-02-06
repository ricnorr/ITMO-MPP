package msqueue;

import kotlinx.atomicfu.AtomicRef;

public class MSQueue implements Queue {
    private AtomicRef<Node> head = new AtomicRef<>(null);
    private AtomicRef<Node> tail = new AtomicRef<>(null);

    public MSQueue() {
        Node dummy = new Node(0, null);
        this.head.setValue(dummy);
        this.tail.setValue(dummy);
    }

    @Override
    public void enqueue(int x) {
        Node newTail = new Node(x, null);
        while (true) {
            Node curTail = tail.getValue();
            if (curTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(curTail, newTail);
                return;
            } else {
                tail.compareAndSet(curTail, curTail.next.getValue());
            }
        }
    }


    @Override
    public int dequeue() {
        while (true) {
            Node headNode = head.getValue();
            Node headNextNode = headNode.next.getValue();
            Node tailNode =  tail.getValue();
            if (headNode == tailNode) {
                if (headNextNode != null) {
                    tail.compareAndSet(tailNode, headNextNode);
                    continue;
                }
            }
            if (headNextNode == null) {
                return Integer.MIN_VALUE;
            }
            if (head.compareAndSet(headNode, headNextNode)) {
                return headNextNode.x;
            }
        }
    }

    @Override
    public int peek() {
        Node headNextNode = head.getValue().next.getValue();
        if (headNextNode == null) {
            return Integer.MIN_VALUE;
        }
        return headNextNode.x;
    }

private class Node {
    final int x;
    AtomicRef<Node> next;

    Node(int x) {
        this.x = x;
    }

    Node(int x, Node next) {
        this.x = x;
        this.next = new AtomicRef<>(next);
    }
}
}