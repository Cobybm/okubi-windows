package bo.nexoplay.app.retro;

/** Deterministic performance regression checks without Android SDK. */
public final class RetroPerformancePolicyTest {
    private static void yes(boolean value, String description) {
        if (!value) throw new AssertionError(description);
    }
    private static void equal(long got, long want, String description) {
        if (got != want) throw new AssertionError(description + ": " + got + " != " + want);
    }
    private static void window(RetroRenderPolicy policy, int misses) {
        for (int i=0;i<120;i++)
            policy.observeFrame(i<misses ? 20_000_000L : 0L, 16_700_000L);
    }
    public static void main(String[] args) {
        RetroRenderPolicy policy=new RetroRenderPolicy();
        yes(RetroRenderPolicy.parseMode("INVALID")==RetroRenderPolicy.Mode.AUTOMATIC,
                "safe corrupt setting");
        yes(RetroRenderPolicy.parseMode(null)==RetroRenderPolicy.Mode.AUTOMATIC,
                "safe missing setting");
        yes(policy.shouldPresent(true), "first frame must render");
        yes(policy.shouldPresent(true), "quality initial");
        yes(!policy.shouldPresent(false), "no surface must skip presentation");
        window(policy,23);
        yes(!policy.isReduced(), "isolated lateness does not lower quality");
        window(policy,24);
        yes(policy.isReduced(), "sustained late frames switch to 30fps display");
        policy.reset();
        window(policy,24);
        int displayed=0;
        for(int i=0;i<120;i++) if(policy.shouldPresent(true))displayed++;
        equal(displayed,60,"only visual frames are halved");
        window(policy,4);window(policy,4);
        yes(policy.isReduced(),"two stable windows not enough");
        window(policy,4);
        yes(!policy.isReduced(),"three stable windows restore full frames");
        policy.setMode(RetroRenderPolicy.Mode.BATTERY);
        displayed=0;
        for(int i=0;i<120;i++)if(policy.shouldPresent(true))displayed++;
        equal(displayed,60,"battery display half cadence");
        policy.setMode(RetroRenderPolicy.Mode.QUALITY);
        displayed=0;
        for(int i=0;i<120;i++)if(policy.shouldPresent(true))displayed++;
        equal(displayed,120,"quality full cadence");
        window(policy,120);
        yes(!policy.isReduced(),"quality mode ignores lag");
        policy.setMode(RetroRenderPolicy.Mode.AUTOMATIC);
        yes(!policy.isReduced(),"changing modes clears lag history");
        yes(policy.shouldPresent(true),"mode switch first frame visible");

        RetroAudioRetry retry=new RetroAudioRetry();
        yes(retry.mayAttempt(0),"initial attempt allowed");
        retry.failed(1000);
        equal(retry.retryAfterMs(),1750,"first delay");
        yes(!retry.mayAttempt(1749),"reject rapid retry");
        yes(retry.mayAttempt(1750),"retry on threshold");
        retry.failed(1750);
        equal(retry.retryAfterMs(),3250,"exponential backoff");
        for(int i=0;i<20;i++)retry.failed(100000);
        equal(retry.retryAfterMs(),115000,"bounded backoff");
        retry.failed(Long.MAX_VALUE - 5);
        equal(retry.retryAfterMs(),Long.MAX_VALUE,"overflow safe");
        retry.reset();
        equal(retry.failures(),0,"reset failure count");
        yes(retry.mayAttempt(1),"recovery allowed");
        System.out.println("OKUBI performance: render cadence, adaptive hysteresis and audio retry passed");
    }
}
