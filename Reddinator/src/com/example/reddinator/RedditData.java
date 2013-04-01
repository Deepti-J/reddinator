package com.example.reddinator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.Uri;
import android.util.Log;

public class RedditData {
	static InputStream is = null;
    static JSONObject jObj = null;
    static String json = "";
	RedditData(){
		
	}
	public JSONArray getSubreddits(){
		JSONArray sreddits = new JSONArray();
		String url = "http://www.reddit.com/subreddits/popular.json?limit=50";
		try {
			sreddits = getJSONFromUrl(url).getJSONObject("data").getJSONArray("children");
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return sreddits;
	}
	public JSONArray getSubredditSearch(String query){
		JSONArray sreddits = new JSONArray();
		String url = "http://www.reddit.com/subreddits/search.json?q="+Uri.encode(query);
		try {
			sreddits = getJSONFromUrl(url).getJSONObject("data").getJSONArray("children");
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return sreddits;
	}
	public JSONArray getRedditFeed(String subreddit, String sort){
		String url = "http://www.reddit.com/r/"+subreddit+"/"+sort+".json";
		JSONArray feed = new JSONArray();
		try {
			feed = getJSONFromUrl(url).getJSONObject("data").getJSONArray("children");
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return feed;
	}
	private JSONObject getJSONFromUrl(String url) {
		 
        // Making HTTP request
        try {
            // defaultHttpClient
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpGet httpget = new HttpGet(url);
 
            HttpResponse httpResponse = httpClient.execute(httpget);
            HttpEntity httpEntity = httpResponse.getEntity();
            is = httpEntity.getContent();          
 
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
 
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    is, "iso-8859-1"), 8);
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
            is.close();
            json = sb.toString();
        } catch (Exception e) {
            Log.e("Buffer Error", "Error converting result " + e.toString());
        }
 
        // try parse the string to a JSON object
        try {
            jObj = new JSONObject(json);
        } catch (JSONException e) {
            Log.e("JSON Parser", "Error parsing data " + e.toString());
        }
        System.out.println("Download complete");
        // return JSON String
        return jObj;
 
    }
}
