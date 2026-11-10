package bo.nexoplay.app.retro;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/** Bounded streaming and ROM read regression checks without Android. */
public final class RetroRomReaderTest {
    private static void check(boolean actual,String name) {
        if(!actual)throw new AssertionError(name);
    }
    private static void rejects(byte[] data,int limit,int minimum,String name) {
        try {
            RetroRomReader.readBounded(new ByteArrayInputStream(data),limit,minimum);
            throw new AssertionError("Expected failure: "+name);
        } catch(IOException expected) { }
    }
    private static byte[] sample(int size){
        byte[] data=new byte[size];
        for(int i=0;i<size;i++) data[i]=(byte)((i * 37) ^ (i >>> 8));
        return data;
    }
    public static void main(String[] args) throws Exception {
        for(int size:new int[]{32768,65536,131072,524288,1048576}){
            byte[] original=sample(size);
            byte[] result=RetroRomReader.readBounded(
                    new ByteArrayInputStream(original),size,32768);
            check(Arrays.equals(original,result),"ROM exact size "+size);
            check(result.length==size,"ROM output length "+size);
        }
        byte[] unaligned=sample(100000);
        check(Arrays.equals(unaligned,RetroRomReader.readBounded(
                new ByteArrayInputStream(unaligned),100000,32768)),
                "unaligned exact ROM bytes");
        rejects(sample(65537),65536,32768,"over limit");
        rejects(sample(1024),65536,32768,"truncated ROM");
        rejects(sample(0),65536,32768,"empty ROM");
        InputStream zeroThenData=new ByteArrayInputStream(sample(65536)){
            boolean zero=true;
            @Override public synchronized int read(byte[] buffer,int off,int len){
                if(zero){zero=false;return 0;}
                return super.read(buffer,off,len);
            }
        };
        check(RetroRomReader.readBounded(zeroThenData,65536,32768).length==65536,
                "zero read recovers");
        try {
            RetroRomReader.readBounded(new ByteArrayInputStream(sample(32768)),1,32768);
            throw new AssertionError("Expected invalid limits");
        } catch(IllegalArgumentException expected) { }
        System.out.println("OKUBI ROM reader: bounded streaming and byte accuracy passed");
    }
}
