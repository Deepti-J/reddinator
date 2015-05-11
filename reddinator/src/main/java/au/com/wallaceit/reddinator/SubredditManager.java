/*
 * Copyright 2013 Michael Boyde Wallace (http://wallaceit.com.au)
 * This file is part of Reddinator.
 *
 * Reddinator is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Reddinator is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Reddinator (COPYING). If not, see <http://www.gnu.org/licenses/>.
 */
package au.com.wallaceit.reddinator;

import android.content.SharedPreferences;
import android.widget.ArrayAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by michael on 10/05/15.
 *
 * Subreddit Manager Provides a global interface for modifying the user subreddit & multi lists.
 */
public class SubredditManager {
    private SharedPreferences prefs;
    private ArrayList<String> subreddits;
    private JSONObject multis;

    public SubredditManager(SharedPreferences prefs){
        // load subreddits & multis
        this.prefs = prefs;
        Set<String> feeds = prefs.getStringSet("personalsr", new HashSet<String>());
        if (feeds==null || feeds.isEmpty()) {
            // first time setup
            subreddits = new ArrayList<>(Arrays.asList("Front Page", "all", "arduino", "AskReddit", "pics", "technology", "science", "videos", "worldnews"));
            saveSubs();
        } else {
            subreddits = new ArrayList<>(feeds);
        }
        try {
            multis = new JSONObject(prefs.getString("userMultis", "{}"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String getCurrentFeedName(int feedId){
        try {
            return getCurrentFeed(feedId).getString("name");
        } catch (JSONException e) {
            e.printStackTrace();
            return "";
        }
    }

    public String getCurrentFeedPath(int feedId){
        try {
            return getCurrentFeed(feedId).getString("path");
        } catch (JSONException e) {
            e.printStackTrace();
            return "";
        }
    }

    public boolean isFeedMulti(int feedId){
        try {
            return getCurrentFeed(feedId).getBoolean("is_multi");
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void setFeed(int feedId, String name, String path, boolean isMulti){
        JSONObject data = new JSONObject();
        try {
            data.put("name", name);
            data.put("path", path);
            data.put("is_multi", isMulti);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("currentfeed-"+String.valueOf(feedId), data.toString());
        editor.apply();
    }

    public void setFeedSubreddit(int feedId, String subreddit){
        boolean isMulti = (subreddit.equals("Front Page") || subreddit.equals("all"));
        setFeed(feedId, subreddit, subreddit.equals("Front Page")?"":"/r/"+subreddit, isMulti);
    }

    private final static String defaultFeed = "{\"name\":\"Front Page\",\"path\":\"\",\"is_multi\":\"true\"}"; // default subs are also "multi"

    private JSONObject getCurrentFeed(int feedId) throws JSONException {
        return new JSONObject(prefs.getString("currentfeed-"+String.valueOf(feedId), defaultFeed));
    }

    private void saveSubs(){
        SharedPreferences.Editor editor = prefs.edit();
        Set<String> set = new HashSet<>();
        set.addAll(subreddits);
        editor.putStringSet("personalsr", set);
        editor.apply();
    }

    public ArrayList<String> getSubreddits() {
        return subreddits;
    }

    public void addSubreddit(String subredditName){
        subreddits.add(subredditName);
    }

    public void addSubreddits(ArrayList<String> subNames, boolean clearCurrent){
        if (clearCurrent)
            subreddits.clear();

        subreddits.addAll(subNames);
    }

    public void removeSubreddit(String subredditName){
        subreddits.remove(subredditName);
    }

    public JSONObject getMultis(){
        return multis;
    }

    public ArrayList<String> getMultiNames(){
        Iterator iterator = multis.keys();
        ArrayList<String> multiList = new ArrayList<>();
        while (iterator.hasNext()){
            multiList.add(iterator.next().toString());
        }
        return multiList;
    }

    public JSONObject getMultiData(String multiName){
        try {
            return multis.getJSONObject(multiName);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void addMulti(JSONObject multiObj){
        addMultiData(multiObj);
        saveMultis();
    }

    public void addMultis(JSONArray subsArray, boolean clearCurrent){
        if (clearCurrent)
            multis = new JSONObject();

        for (int i = 0; i<subsArray.length(); i++){
            try {
                addMultiData(subsArray.getJSONObject(i));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        saveMultis();
    }

    public void removeMulti(String key){
        multis.remove(key);
        saveMultis();
    }

    private void addMultiData(JSONObject multiObj){
        try {
            JSONObject data =  multiObj.getJSONObject("data");
            String name = data.getString("name");
            multis.put(name, data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void saveMultis(){
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("userMultis", multis.toString());
        editor.apply();
    }
}
