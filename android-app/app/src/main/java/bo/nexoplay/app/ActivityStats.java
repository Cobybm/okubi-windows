package bo.nexoplay.app;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Optional and approximate app-foreground activity, not guaranteed active gameplay.
 * Read once on demand, on a background thread; no running service and no data uploaded.
 * Counts only foreground sessions whose starting event is in Android's available history.
 */
public final class ActivityStats {
    private ActivityStats() {}

    public static final class Duration {
        public long todayMs;
        public long weekMs;
        public long monthMs;
    }

    public static boolean permitted(Context context) {
        try {
            AppOpsManager ops=(AppOpsManager)context.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) return false;
            return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
        } catch (Exception ex) { return false; }
    }

    public static Map<String, Duration> read(Context context, Set<String> gamePackages) {
        if (!permitted(context) || gamePackages.isEmpty()) return Collections.emptyMap();
        long now=System.currentTimeMillis();
        Calendar calendar=Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY,0);
        calendar.set(Calendar.MINUTE,0);
        calendar.set(Calendar.SECOND,0);
        calendar.set(Calendar.MILLISECOND,0);
        long today=calendar.getTimeInMillis();
        calendar.add(Calendar.DAY_OF_YEAR,-6);
        long week=calendar.getTimeInMillis();
        calendar.add(Calendar.DAY_OF_YEAR,-23);
        long month=calendar.getTimeInMillis();
        Map<String, Duration> data = new HashMap<>();
        for(String pkg:gamePackages) data.put(pkg,new Duration());
        UsageStatsManager manager=(UsageStatsManager)context.getSystemService(Context.USAGE_STATS_SERVICE);
        if(manager==null) return data;
        try {
            UsageEvents events=manager.queryEvents(month,now);
            if(events==null) return data;
            UsageEvents.Event event=new UsageEvents.Event();
            String current=null;
            long started=0L;
            while(events.hasNextEvent()) {
                events.getNextEvent(event);
                int type=event.getEventType();
                String pkg=event.getPackageName();
                long at=event.getTimeStamp();
                if(type==UsageEvents.Event.ACTIVITY_RESUMED || type==UsageEvents.Event.MOVE_TO_FOREGROUND){
                    if(pkg==null)continue;
                    if(current!=null && !current.equals(pkg)) add(data,current,started,at,today,week,month);
                    if(!pkg.equals(current)){current=pkg;started=at;}
                } else if(type==UsageEvents.Event.ACTIVITY_PAUSED || type==UsageEvents.Event.MOVE_TO_BACKGROUND){
                    if(pkg!=null && pkg.equals(current)) {
                        add(data,current,started,at,today,week,month);
                        current=null;
                    }
                } else if(type==UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                    if(current!=null) add(data,current,started,at,today,week,month);
                    current=null;
                }
            }
            if(current!=null) add(data,current,started,now,today,week,month);
        } catch (SecurityException | IllegalArgumentException ignored) {
            // Permission might be revoked while reading; UI explains missing data.
        }
        return data;
    }

    private static void add(Map<String,Duration> data,String pkg,long start,long end,
                            long today,long week,long month){
        Duration d=data.get(pkg);
        if(d==null||end<=start) return;
        d.todayMs+=Math.max(0,end-Math.max(start,today));
        d.weekMs+=Math.max(0,end-Math.max(start,week));
        d.monthMs+=Math.max(0,end-Math.max(start,month));
    }

    public static String format(long millis) {
        long minutes=Math.max(0,millis/60000L);
        if(minutes<60) return minutes+" min";
        long hours=minutes/60;
        long rest=minutes%60;
        return rest==0 ? hours+" h" : hours+" h "+rest+" min";
    }
}
