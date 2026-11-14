package bo.nexoplay.app.retro;

/** Actual Java checks for pinned slot intent, hash isolation and FIFO limits. */
public final class RetroSlotRequestsTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String first = "ab".repeat(32);
        String second = "cd".repeat(32);
        RetroSlotRequests queue = new RetroSlotRequests();
        check(queue.poll() == null, "empty queue");
        check(queue.offer(RetroSlotRequests.Action.SAVE, first, 1), "save slot 1");
        check(queue.offer(RetroSlotRequests.Action.LOAD, first, 3), "load slot 3");
        check(queue.offer(RetroSlotRequests.Action.SAVE, second, 2),
                "second cartridge is independently pinned");

        RetroSlotRequests.Request saved = queue.poll();
        check(saved.action == RetroSlotRequests.Action.SAVE, "save command");
        check(saved.slot == 1 && saved.romHash.equals(first), "first slot immutable");
        RetroSlotRequests.Request loaded = queue.poll();
        check(loaded.action == RetroSlotRequests.Action.LOAD, "load command");
        check(loaded.slot == 3 && loaded.romHash.equals(first), "load slot pinned");
        RetroSlotRequests.Request other = queue.poll();
        check(other.slot == 2 && other.romHash.equals(second), "ROM identity pinned");
        check(queue.poll() == null, "FIFO exhausted");

        check(!queue.offer(null, first, 1), "null action rejected");
        check(!queue.offer(RetroSlotRequests.Action.SAVE, null, 1), "null ROM rejected");
        check(!queue.offer(RetroSlotRequests.Action.SAVE, "ROM", 1), "bad ROM rejected");
        check(!queue.offer(RetroSlotRequests.Action.LOAD, first, 0), "bad slot 0 rejected");
        check(!queue.offer(RetroSlotRequests.Action.LOAD, first, 4), "bad slot 4 rejected");
        for (int n=0; n<RetroSlotRequests.MAX_PENDING; n++) {
            check(queue.offer(RetroSlotRequests.Action.SAVE, first, n%3+1),
                    "bounded queue must admit requests until capacity");
        }
        check(queue.size() == RetroSlotRequests.MAX_PENDING, "fixed capacity");
        check(!queue.offer(RetroSlotRequests.Action.LOAD, first, 2),
                "capacity exceeded: reject, never overwrite earlier command");
        check(queue.size() == RetroSlotRequests.MAX_PENDING, "no silent replacement");
        queue.clear();
        check(queue.size() == 0 && queue.poll() == null,
                "clear pending requests after backup restoration");
        check(queue.offer(RetroSlotRequests.Action.LOAD, second, 1),
                "queue reusable after clearing");
        check(queue.poll().romHash.equals(second), "fresh request only");
        System.out.println("RetroSlotRequestsTest: OK");
    }
}
