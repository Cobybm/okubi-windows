package bo.nexoplay.app;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Optional Android-aggregated foreground activity for selected games.
 * All data stays on-device. Reads once on demand from a worker thread.
 * Daily buckets may be incomplete, missing, rounded to calendar days or
 * truncated by Android; they are not proof of time spent actively playing.
 */
public final class UsageInsights {
    private UsageInsights() {}
    public static final int DAYS = 30;

    public static final class Day {
        public final long startMs;
        public final int weekday;
        public final int dayOfMonth;
        public final int month;
        Day(long millis) {
            startMs = millis;
            Calendar c = Calendar.getInstance();c.setTimeInMillis(millis);
            weekday = c.get(Calendar.DAY_OF_WEEK);
            dayOfMonth = c.get(Calendar.DAY_OF_MONTH);
            month = c.get(Calendar.MONTH)+1;
        }
    }
    public static final class Result {
        public final List<Day> days;
        public final Map<String,long[]> byPackage;
        public final boolean available;
        public final boolean hasRecords;
        Result(List<Day> d,Map<String,long[]> m,boolean a,boolean r) {
            days=d;byPackage=m;available=a;hasRecords=r;
        }
        public long gameTotal(String pkg,int periodDays) {
            long[] daily=byPackage.get(pkg);
            if(daily==null)return 0;
            int start=Math.max(0,DAYS-Math.max(1,Math.min(DAYS,periodDays)));
            long total=0;for(int i=start;i<DAYS;i++)total+=daily[i];return total;
        }
        public long total(Set<String> packages,int periodDays) {
            long total=0;for(String pkg:packages)total+=gameTotal(pkg,periodDays);return total;
        }
        public long[] chart(Set<String> packages) {
            long[] totals=new long[DAYS];
            for(String pkg:packages){long[] individual=byPackage.get(pkg);
                if(individual==null)continue;
                for(int i=0;i<DAYS;i++)totals[i]+=individual[i];
            }
            return totals;
        }
    }

    public static Result read(Context context,Set<String> selectedPackages) {
        List<Day> days=new ArrayList<>();Map<Long,Integer> indices=new HashMap<>();
        Calendar cal=Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);
        cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
        cal.add(Calendar.DAY_OF_YEAR,-(DAYS-1));
        for(int i=0;i<DAYS;i++){long start=cal.getTimeInMillis();
            days.add(new Day(start));indices.put(start,i);cal.add(Calendar.DAY_OF_YEAR,1);
        }
        Map<String,long[]> perPackage=new HashMap<>();
        for(String pkg:selectedPackages)perPackage.put(pkg,new long[DAYS]);
        if(!ActivityStats.permitted(context))return new Result(days,perPackage,false,false);
        UsageStatsManager manager=(UsageStatsManager)context.getSystemService(Context.USAGE_STATS_SERVICE);
        if(manager==null)return new Result(days,perPackage,false,false);
        boolean any=false;
        try {
            // A single daily-aggregation query; Android may expand time to bucket boundaries.
            List<UsageStats> stats=manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,
                days.get(0).startMs,System.currentTimeMillis());
            if(stats==null)return new Result(days,perPackage,false,false);
            for(UsageStats entry:stats) {
                long[] values=perPackage.get(entry.getPackageName());
                if(values==null)continue;
                Calendar bucket=Calendar.getInstance();bucket.setTimeInMillis(entry.getFirstTimeStamp());
                bucket.set(Calendar.HOUR_OF_DAY,0);bucket.set(Calendar.MINUTE,0);
                bucket.set(Calendar.SECOND,0);bucket.set(Calendar.MILLISECOND,0);
                Integer index=indices.get(bucket.getTimeInMillis());
                if(index==null)continue; // ignore expanded intervals outside requested window
                long observed=Math.max(0,entry.getTotalTimeInForeground());
                if(observed>0){values[index]+=observed;any=true;}
            }
        }catch(SecurityException | IllegalArgumentException ignored){
            return new Result(days,perPackage,false,false);
        }
        return new Result(days,perPackage,true,any);
    }
}
