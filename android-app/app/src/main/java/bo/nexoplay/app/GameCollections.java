package bo.nexoplay.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Local, named user collections. Never changes which apps are installed. */
public final class GameCollections {
    private static final String KEY="collections_v05";
    private final SharedPreferences prefs;
    public static final class Collection {
        public final String id, name;
        public final Set<String> gameIds;
        Collection(String id,String name,Set<String> gameIds){this.id=id;this.name=name;this.gameIds=gameIds;}
    }
    public GameCollections(Context context){prefs=context.getApplicationContext().getSharedPreferences("nexo_play",Context.MODE_PRIVATE);}
    public synchronized List<Collection> all(){
        List<Collection> out=new ArrayList<>();
        try {
            JSONArray a=new JSONArray(prefs.getString(KEY,"[]"));
            for(int i=0;i<a.length();i++){
                JSONObject o=a.optJSONObject(i);if(o==null)continue;
                String id=o.optString("id"),name=o.optString("name");
                if(id.isEmpty()||name.isEmpty())continue;
                HashSet<String> games=new HashSet<>();
                JSONArray ids=o.optJSONArray("games");
                if(ids!=null)for(int j=0;j<ids.length();j++){String g=ids.optString(j);if(!g.isEmpty())games.add(g);}
                out.add(new Collection(id,name,games));
            }
        }catch(Exception ignored){} // Corrupt collections never prevent opening NEXO.
        return out;
    }
    private synchronized void save(List<Collection> all){
        JSONArray a=new JSONArray();
        for(Collection c:all){
            JSONObject o=new JSONObject();
            try {o.put("id",c.id);o.put("name",c.name);
                JSONArray g=new JSONArray();for(String id:c.gameIds)g.put(id);
                o.put("games",g);a.put(o);
            }catch(Exception ignored){}
        }
        prefs.edit().putString(KEY,a.toString()).apply();
    }
    public synchronized boolean create(String name){
        String clean=name==null?"":name.trim();
        if(clean.isEmpty()||clean.length()>32)return false;
        List<Collection> list=all();if(list.size()>=20)return false;
        for(Collection c:list)if(c.name.toLowerCase(Locale.ROOT).equals(clean.toLowerCase(Locale.ROOT)))return false;
        list.add(new Collection(UUID.randomUUID().toString(),clean,new HashSet<>()));save(list);return true;
    }
    public synchronized boolean rename(String id,String name){
        String clean=name==null?"":name.trim();if(clean.isEmpty()||clean.length()>32)return false;
        List<Collection> list=all();
        for(Collection c:list)if(!c.id.equals(id)&&c.name.equalsIgnoreCase(clean))return false;
        List<Collection> revised=new ArrayList<>();boolean found=false;
        for(Collection c:list){
            if(c.id.equals(id)){revised.add(new Collection(id,clean,c.gameIds));found=true;}
            else revised.add(c);
        }
        if(found)save(revised);return found;
    }
    public synchronized void delete(String id){List<Collection> list=all();list.removeIf(c->c.id.equals(id));save(list);}
    public synchronized void setMembership(String collectionId,String gameId,boolean selected){
        List<Collection> list=all();
        for(Collection c:list)if(c.id.equals(collectionId)){
            if(selected)c.gameIds.add(gameId);else c.gameIds.remove(gameId);
        }
        save(list);
    }
    /** Saves all selections of one collection in a single transaction. */
    public synchronized void replaceMembership(String collectionId,Set<String> gameIds){
        List<Collection> list=all();
        for(Collection c:list)if(c.id.equals(collectionId)){
            c.gameIds.clear();
            c.gameIds.addAll(gameIds);
            save(list);
            return;
        }
    }
    public synchronized void removeGame(String gameId){
        List<Collection> list=all();for(Collection c:list)c.gameIds.remove(gameId);save(list);
    }
}
