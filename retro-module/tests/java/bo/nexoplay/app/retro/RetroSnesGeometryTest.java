package bo.nexoplay.app.retro;

/** Real Java measurements for SNES 256:224 and all mobile thumb lanes. */
public final class RetroSnesGeometryTest {
    private static void check(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }
    private static void verify(int w, int h, float density) {
        RetroCinemaGeometry.Layout v=RetroCinemaGeometry.fit(
                w,h,256f/224f,density);
        check(v.stageWidth==w&&v.stageHeight==h,"stage dimensions");
        check(v.gameWidth>0&&v.gameHeight>0,"game visible");
        check(v.gameLeft>0&&v.gameRight()<w,"two action lanes");
        check(v.gameTop>=0&&v.gameBottom()<=h-v.bottomRail,
                "utility buttons remain below game");
        check(Math.abs(v.gameWidth/(float)v.gameHeight-256f/224f)
                <= 2f/v.gameHeight, "video ratio unchanged");
        check(v.leftRail>0&&v.rightRail>0,"thumb lanes");
        check(Math.abs(v.leftRail-v.rightRail)<=1,"balanced hand space");
        check(v.utilityY>=v.gameBottom(),"start/select below game");
        check(v.leftThumbX<v.gameLeft&&v.rightThumbX>v.gameRight(),
                "real snes action cluster never needs to cover viewport");
        float buttonRadius=Math.min(v.radius*1.5f,
                (Math.min(v.leftRail,v.rightRail)*.5f-5f*density)/1.82f);
        check(buttonRadius>0,"A/B/X/Y fit");
    }
    public static void main(String[] args) {
        verify(1600,610,2.7f); // A05s-class landscape
        verify(1280,560,2.25f);
        verify(2340,1080,3.5f);
        verify(960,500,1.8f);
        verify(1080,440,2f);
        verify(800,480,1.35f);
        System.out.println("RetroSnesGeometryTest: OK");
    }
}
