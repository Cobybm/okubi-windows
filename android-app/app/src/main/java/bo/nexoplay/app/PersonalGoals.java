package bo.nexoplay.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Locally recorded personal challenges. These are NOT official game achievements. */
public final class PersonalGoals {
    private static final String PREFIX = "personal_goals_v10_";
    private final SharedPreferences prefs;
    public static final int MAX_GOALS_PER_GAME = 25;
    public static final int MAX_TITLE_LENGTH = 60;

    public static final class Goal {
        public final String id, title;
        public final boolean complete;
        public final long createdAt, completedAt;
        Goal(String id, String title, boolean complete, long createdAt, long completedAt) {
            this.id=id; this.title=title; this.complete=complete;
            this.createdAt=createdAt; this.completedAt=completedAt;
        }
    }
    public PersonalGoals(Context context) {
        prefs=context.getApplicationContext().getSharedPreferences("nexo_play",Context.MODE_PRIVATE);
    }
    static String key(String gameId) { return PREFIX+gameId; }
    public synchronized List<Goal> forGame(String gameId) {
        List<Goal> out=new ArrayList<>();
        if(gameId==null || gameId.isEmpty())return out;
        try {
            JSONArray items=new JSONArray(prefs.getString(key(gameId),"[]"));
            for(int i=0;i<items.length();i++) {
                JSONObject o=items.optJSONObject(i);
                if(o==null)continue;
                String id=o.optString("id", ""), title=o.optString("title", "");
                if(id.isEmpty()||title.trim().isEmpty())continue;
                out.add(new Goal(id,title,o.optBoolean("complete",false),
                    o.optLong("createdAt",0),o.optLong("completedAt",0)));
            }
        } catch(Exception ignored) { /* Malformed data must not stop the launcher. */ }
        return out;
    }
    private synchronized void write(String gameId,List<Goal> goals) {
        JSONArray rows=new JSONArray();
        for(Goal g:goals) {
            JSONObject item=new JSONObject();
            try {
                item.put("id",g.id);item.put("title",g.title);
                item.put("complete",g.complete);item.put("createdAt",g.createdAt);
                item.put("completedAt",g.completedAt);rows.put(item);
            } catch(Exception ignored) {}
        }
        prefs.edit().putString(key(gameId),rows.toString()).apply();
    }
    public synchronized boolean add(String gameId,String title) {
        if(gameId==null||gameId.isEmpty()||title==null)return false;
        String clean=title.trim();
        if(clean.isEmpty()||clean.length()>MAX_TITLE_LENGTH)return false;
        List<Goal> goals=forGame(gameId);
        if(goals.size()>=MAX_GOALS_PER_GAME)return false;
        for(Goal g:goals)if(g.title.equalsIgnoreCase(clean))return false;
        goals.add(new Goal(UUID.randomUUID().toString(),clean,false,System.currentTimeMillis(),0));
        write(gameId,goals);return true;
    }
    public synchronized void toggle(String gameId,String goalId) {
        List<Goal> goals=forGame(gameId),next=new ArrayList<>();
        for(Goal g:goals) {
            if(g.id.equals(goalId)){
                boolean checked=!g.complete;
                next.add(new Goal(g.id,g.title,checked,g.createdAt,checked?System.currentTimeMillis():0));
            } else next.add(g);
        }
        write(gameId,next);
    }
    public synchronized void remove(String gameId,String goalId) {
        List<Goal> goals=forGame(gameId);
        goals.removeIf(g->g.id.equals(goalId));write(gameId,goals);
    }
    public synchronized void removeGame(String gameId) {
        prefs.edit().remove(key(gameId)).apply();
    }
    public synchronized int[] summary(Iterable<String> gameIds) {
        int total=0,completed=0;
        for(String id:gameIds)for(Goal g:forGame(id)){
            total++;if(g.complete)completed++;
        }
        return new int[]{total,completed};
    }
}
