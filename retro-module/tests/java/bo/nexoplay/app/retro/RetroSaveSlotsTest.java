package bo.nexoplay.app.retro;

/** Actual pure-Java regression tests, run in GitHub before Android builds. */
public final class RetroSaveSlotsTest {
    private static void check(boolean value,String reason) {
        if(!value)throw new AssertionError(reason);
    }
    private static void eq(String expected,String actual,String reason) {
        if(!expected.equals(actual))throw new AssertionError(
                reason+": expected="+expected+" actual="+actual);
    }
    private static void reject(String hash,int slot) {
        boolean rejected=false;
        try {RetroSaveSlots.filename(hash,slot);}
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"invalid slot/hash must be rejected");
    }
    public static void main(String[] args) {
        String hash="0123456789abcdef".repeat(4);
        eq(hash+".state",RetroSaveSlots.filename(hash,1),
                "legacy checkpoint must keep filename unchanged");
        eq(hash+".slot2.state",RetroSaveSlots.filename(hash,2),"second slot");
        eq(hash+".slot3.state",RetroSaveSlots.filename(hash,3),"third slot");
        for(int slot=1;slot<=3;slot++){
            check(RetroSaveSlots.validSlot(slot),"valid slot");
            check(RetroSaveSlots.filename(hash,slot).startsWith(hash),
                    "per-cartridge identity");
            eq("Ranura "+slot,RetroSaveSlots.label(slot),"label");
        }
        for(int slot:new int[]{0,-1,4,Integer.MIN_VALUE,Integer.MAX_VALUE}){
            check(!RetroSaveSlots.validSlot(slot),"reject invalid slot");
            check(RetroSaveSlots.normalized(slot)==1,"fallback never writes unknown file");
            reject(hash,slot);
        }
        reject("../" + hash,1);
        reject(hash.toUpperCase(),2);
        reject("random",3);
        reject("",1);
        reject(null,1);
        check(RetroSaveSlots.validStateSize(1),"minimum");
        check(RetroSaveSlots.validStateSize(16L*1024*1024),"maximum");
        check(!RetroSaveSlots.validStateSize(0),"zero");
        check(!RetroSaveSlots.validStateSize(-1),"negative");
        check(!RetroSaveSlots.validStateSize(16L*1024*1024+1),"over maximum");
        check(RetroSaveSlots.canCopy(1,2,4096,false),"copy into empty slot");
        check(RetroSaveSlots.canCopy(3,1,1,false),"legacy slot may be copied only if empty");
        check(!RetroSaveSlots.canCopy(2,2,100,false),"cannot duplicate onto itself");
        check(!RetroSaveSlots.canCopy(1,2,100,true),"never overwrite an occupied slot");
        check(!RetroSaveSlots.canCopy(0,2,100,false),"reject bad source");
        check(!RetroSaveSlots.canCopy(1,4,100,false),"reject bad target");
        check(!RetroSaveSlots.canCopy(1,2,0,false),"reject empty source");
        check(!RetroSaveSlots.canCopy(1,2,RetroSaveSlots.MAX_STATE_BYTES+1,false),
                "reject oversized state");
        System.out.println("RetroSaveSlotsTest: OK");
    }
}
