package stack;

import kotlinx.atomicfu.AtomicArray;
import kotlinx.atomicfu.AtomicRef;

import java.util.Random;


public class StackImpl implements Stack {
    private static class Node {
        final AtomicRef<Node> next;
        final int x;

        Node(int x, Node next) {
            this.next = new AtomicRef<>(next);
            this.x = x;
        }
    }

    private AtomicArray<AtomicRef<Node>> elimination = new AtomicArray<>(100);
    private Random random = new Random();

    // head pointer
    private AtomicRef<Node> head = new AtomicRef<>(null);

    @Override
    public void push(int x) {
        Node node = new Node(x, null);
        AtomicRef<Node> ref = new AtomicRef<>(node);
        int rand = random.nextInt(100);
        for (int i = 0; i < 5; i++) {
            int index = (rand + i) % 100;
            if (elimination.get(index).compareAndSet(null, ref)) {
                for (int j = 0; j < 5000; j++) ;
                if (!elimination.get(index).compareAndSet(ref, null)) {
                    return;
                }
                break;
            }
        }
        while (true) {
            Node headNode = head.getValue();
            node = new Node(x, headNode);
            if (head.compareAndSet(headNode, node)) {
                return;
            }
        }
    }

    @Override
    public int pop() {
        int rand = random.nextInt(100);
        for (int i = 0; i < 5; i++) {
            int index = (rand + i) % 100;
            AtomicRef<Node> curNode = elimination.get(index).getValue();
            if (curNode != null) {
                if (elimination.get(index).compareAndSet(curNode, null)) {
                    return curNode.getValue().x;
                }
            }
        }
        while (true) {
            Node headNode = head.getValue();
            if (headNode == null) {
                return Integer.MIN_VALUE;
            }
            Node nextNode = headNode.next.getValue();
            if (head.compareAndSet(headNode, nextNode)) {
                return headNode.x;
            }
        }
    }
}
