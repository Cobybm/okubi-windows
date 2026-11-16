package bo.okubi.desktop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** JNI acceptance using a synthetic PS1 disc core, not a copyrighted image. */
public final class DesktopPs1NativeCoreTest {
    private static void check(boolean success,String message) {
        if(!success)throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Expected mock PS1 core DLL");
        Path folder=Files.createTempDirectory("okubi-ps1-jni-");
        try {
            Path image=Files.write(folder.resolve("demo.chd"),new byte[1024*1024]);
            Path core=Path.of(args[0]).toAbsolutePath().normalize();
            WindowsRetroNative.open(core,image,DesktopLibrary.Console.PS1,
                    folder.resolve("save-ps1"));
            try {
                check(WindowsRetroNative.fps()==60,"Mock PS1 core FPS.");
                int[] picture=new int[WindowsRetroNative.PS1_PIXELS];
                short[] audio=new short[WindowsRetroNative.MAX_AUDIO_SAMPLES];
                int samples=WindowsRetroNative.step(0,picture,audio);
                check(samples>0,"PS1 audio samples returned.");
                check(picture[0]==0xff0000ff,"PS1 320x240 frame upscaled inside OKUBI.");
                check(picture[picture.length-1]==0xff0000ff,
                        "PS1 entire 640x480 surface rendered without overflow.");
                samples=WindowsRetroNative.step(1<<4,picture,audio);
                check(samples>0 && picture[0]==0xffff0000,
                        "Gamepad A mapped in PS1 native core.");
                byte[] state=WindowsRetroNative.saveState();
                check(state!=null && state.length>0,"PS1 state is available.");
                check(WindowsRetroNative.loadState(state),"PS1 state reloads.");
            }finally{
                WindowsRetroNative.close();
            }
            check(Files.size(image)==1024*1024,
                    "Disc remains unchanged after direct path-native test.");
            System.out.println("DesktopPs1NativeCoreTest PASS");
        }finally{
            try(var stream=Files.walk(folder)){
                stream.sorted(Comparator.reverseOrder()).forEach(path->{
                    try {Files.deleteIfExists(path);}catch(Exception ignored){}
                });
            }
        }
    }
}
