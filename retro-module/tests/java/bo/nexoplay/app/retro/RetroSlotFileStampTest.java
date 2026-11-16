package bo.nexoplay.app.retro;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/** JVM checks: no silent overwrite if a save changes while queued. */
public final class RetroSlotFileStampTest {
    private static void check(boolean ok,String reason) {
        if(!ok)throw new AssertionError(reason);
    }
    public static void main(String[] args) throws Exception {
        String hash="ab".repeat(32);
        Path temp=Files.createTempDirectory("okubi-slotstamp-");
        File state=temp.resolve(hash+".state").toFile();
        File backup=new File(state.getPath()+".bak");
        File unfinished=new File(state.getPath()+".new");
        try {
            RetroSlotRequests.FileStamp empty =
                    RetroSlotRequests.FileStamp.capture(state);
            check(empty!=null&&!empty.exists,"empty target");
            check(empty.matches(state),"empty remains empty");
            RetroSlotRequests q=new RetroSlotRequests();
            check(q.offerGuardedSave(hash,1,empty),"queue protected new save");
            RetroSlotRequests.Request requested=q.poll();
            check(requested.slot==1&&requested.action==RetroSlotRequests.Action.SAVE,
                    "action slot pinned");
            check(requested.expectedSaveTarget==empty,"exact immutable expectation");
            check(!q.offerGuardedSave(hash,1,null),"ungarded save forbidden");
            check(!q.offerGuardedSave("invalid",1,empty),"invalid ROM");
            check(!q.offerGuardedSave(hash,4,empty),"invalid slot");

            Files.write(state.toPath(),new byte[]{1,2,3,4,5});
            check(!empty.matches(state),"new save cannot overwrite a newly occupied slot");
            RetroSlotRequests.FileStamp occupied=RetroSlotRequests.FileStamp.capture(state);
            check(occupied!=null&&occupied.exists,"occupied target");
            check(occupied.matches(state),"unchanged occupied target");
            check(q.offerGuardedSave(hash,1,occupied),"user-confirmed overwrite queued");
            check(q.poll().expectedSaveTarget==occupied,"confirmed stamp remains pinned");

            check(state.setLastModified(state.lastModified()+2000L),"alter metadata");
            check(!occupied.matches(state),"file changed while dialog open");
            RetroSlotRequests.FileStamp updated=RetroSlotRequests.FileStamp.capture(state);
            check(updated!=null&&updated.matches(state),"new expectation can match");
            Files.write(state.toPath(),new byte[]{1,2,3,4,5,6});
            check(!updated.matches(state),"changed size prevents overwrite");

            Files.write(backup.toPath(),new byte[]{1});
            check(RetroSlotRequests.FileStamp.capture(state)==null,
                    "AtomicFile recovery backup blocks overwrite");
            check(!updated.matches(state),"outstanding stamp blocked by backup");
            Files.delete(backup.toPath());
            Files.write(unfinished.toPath(),new byte[]{1});
            check(RetroSlotRequests.FileStamp.capture(state)==null,
                    "incomplete AtomicFile write blocks overwrite");
            Files.delete(unfinished.toPath());
            Files.write(state.toPath(),new byte[0]);
            check(RetroSlotRequests.FileStamp.capture(state)==null,
                    "zero-byte save is protected rather than overwritten");
            Files.delete(state.toPath());
            check(!q.offerGuardedSave(hash,1,null),"null stamp");
            check(RetroSlotRequests.FileStamp.capture(null)==null,"null path");
        } finally {
            Files.deleteIfExists(unfinished.toPath());
            Files.deleteIfExists(backup.toPath());
            Files.deleteIfExists(state.toPath());
            Files.deleteIfExists(temp);
        }
        System.out.println("RetroSlotFileStampTest: OK");
    }
}
