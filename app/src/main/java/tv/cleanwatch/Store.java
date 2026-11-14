package tv.cleanwatch;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** My List and Continue Watching, kept on the TV. */
final class Store {
    private static final String MY_LIST = "my_list";
    private static final String RECENT = "recent";
    private static final int RECENT_MAX = 20;

    private Store() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("library", Context.MODE_PRIVATE);
    }

    static List<Title> myList(Context c) {
        return read(c, MY_LIST);
    }

    static List<Title> recent(Context c) {
        return read(c, RECENT);
    }

    static boolean inMyList(Context c, Title t) {
        for (Title x : myList(c)) {
            if (x.key().equals(t.key())) {
                return true;
            }
        }
        return false;
    }

    /** Adds or removes; returns whether it is in the list afterwards. */
    static boolean toggleMyList(Context c, Title t) {
        List<Title> list = myList(c);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).key().equals(t.key())) {
                list.remove(i);
                write(c, MY_LIST, list);
                return false;
            }
        }
        list.add(0, t);
        write(c, MY_LIST, list);
        return true;
    }

    /** The show's last episode played, or null. */
    static Title lastPlayed(Context c, Title t) {
        for (Title x : recent(c)) {
            if (x.key().equals(t.key())) {
                return x;
            }
        }
        return null;
    }

    static void played(Context c, Title t) {
        List<Title> list = recent(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).key().equals(t.key())) {
                list.remove(i);
            }
        }
        list.add(0, t);
        while (list.size() > RECENT_MAX) {
            list.remove(list.size() - 1);
        }
        write(c, RECENT, list);
    }

    private static List<Title> read(Context c, String key) {
        List<Title> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) {
                out.add(Title.fromJson(a.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void write(Context c, String key, List<Title> list) {
        JSONArray a = new JSONArray();
        for (Title t : list) {
            a.put(t.toJson());
        }
        prefs(c).edit().putString(key, a.toString()).apply();
    }
}
