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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Base64;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

public class RedditData {
    private SharedPreferences sharedPrefs;
    private DefaultHttpClient httpclient;
    static JSONObject jObj = null;
    private static final String STANDARD_ENDPOINT = "http://www.reddit.com";
    private static final String OAUTH_ENDPOINT = "https://oauth.reddit.com";
    public static final String OAUTH_CLIENTID = "wY63YAHgSPSh5w";
    public static final String OAUTH_SCOPES = "mysubreddits,vote,read";
    public static final String OAUTH_REDIRECT = "oauth://reddinator.wallaceit.com.au";
    private String userAgent;
    private String modhash = "";
    private JSONObject oauthToken = null;
    private String oauthstate = null; // random string for secure oauth flow

    RedditData(Context context) {
        // set user agent
        userAgent = "android:au.com.wallaceit.reddinator:v";
        try {
            PackageInfo manager = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            userAgent += manager.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        userAgent += " (by /u/micwallace)";
        // load account
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        modhash = sharedPrefs.getString("modhash", "");
        String tokenStr = sharedPrefs.getString("oauthtoken", "");
        if (!tokenStr.equals("")) {
            try {
                oauthToken = new JSONObject(tokenStr);
            } catch (JSONException e) {
                e.printStackTrace();
                oauthToken = null;
            }
        }
    }

    // ACCOUNT CONTROL
    public void initiateLogin(Context context) {
        Intent loginintent = new Intent(context, OAuthView.class);
        oauthstate = UUID.randomUUID().toString();
        loginintent.putExtra("oauthstate", oauthstate);
        context.startActivity(loginintent);
    }

    public void purgeAccountData() {
        modhash = "";
        oauthToken = null;
    }

    // NON-AUTHED REQUESTS
    public JSONArray getSubreddits() {
        JSONArray subreddits = new JSONArray();
        String url = STANDARD_ENDPOINT + "/subreddits/popular.json?limit=50";
        try {
            subreddits = getJSONFromUrl(url, false).getJSONObject("data").getJSONArray("children");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return subreddits;
    }

    public JSONArray getSubredditSearch(String query) {
        JSONArray subreddits = new JSONArray();
        String url = STANDARD_ENDPOINT + "/subreddits/search.json?q=" + Uri.encode(query);
        try {
            subreddits = getJSONFromUrl(url, false).getJSONObject("data").getJSONArray("children");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return subreddits;
    }

    public JSONArray getRedditFeed(String subreddit, String sort, int limit, String afterid) {
        boolean loggedIn = isLoggedIn();
        String url = (loggedIn ? OAUTH_ENDPOINT : STANDARD_ENDPOINT) + (subreddit.equals("Front Page") ? "" : "/r/" + subreddit) + "/" + sort + ".json?limit=" + String.valueOf(limit) + (!afterid.equals("0") ? "&after=" + afterid : "");
        JSONObject result = new JSONObject();
        JSONArray feed = new JSONArray();
        try {
            result = getJSONFromUrl(url, true); // use oauth if logged in

            if (result != null)
                feed = result.getJSONObject("data").getJSONArray("children");
        } catch (JSONException e) {
            feed.put("-1"); // error indicator
            e.printStackTrace();
        }
        return feed;
    }

    // AUTHED CALLS
    public String vote(String id, int direction) {

        // if modhash is blank, try to login
        if (!isLoggedIn()) {
            return "LOGIN";
        }
        if (isTokenExpired()) {
            refreshToken();
        }
        if (modhash.equals("")) {
            // TODO: Get new modhash
        }

        String result = "";
        JSONObject resultjson;
        resultjson = new JSONObject();
        String url = OAUTH_ENDPOINT + "/api/vote?id=" + id + "&dir=" + String.valueOf(direction) + "&uh=" + modhash + "&api_type=json";
        try {
            resultjson = getJSONFromPost(url, null, false).getJSONObject("json");
            JSONArray errors = resultjson.getJSONArray("errors");
            if (resultjson.getJSONArray("errors").get(0) != null) {
                JSONArray firsterror = (JSONArray) errors.get(0);
                if (firsterror.get(0).equals("USER_REQUIRED")) {
                    oauthToken = null; // bearer token invalid, nullify
                    result = "LOGIN"; // no creds stored or login failed.
                }
                result = errors.toString();
            } else {
                result = "OK";
            }
        } catch (JSONException e) {
            if (resultjson.toString().equals("{}")) {
                result = "OK";
            } else {
                e.printStackTrace();
            }
        }

        return result;
    }

    public ArrayList<String> getMySubreddits() {
        ArrayList<String> mysrlist = new ArrayList<String>();
        if (!isLoggedIn()) {
            mysrlist.add("Something bad happened");
        }

        if (isTokenExpired()) {
            refreshToken();
        }
        String url = OAUTH_ENDPOINT + "/subreddits/mine.json";
        JSONArray resultjson = new JSONArray();
        try {
            resultjson = getJSONFromUrl(url, true).getJSONObject("data").getJSONArray("children");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        int i = 0;
        while (i < resultjson.length() - 1) {
            try {
                mysrlist.add(resultjson.getJSONObject(i).getJSONObject("data").getString("display_name"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            i++;
        }

        return mysrlist;
    }

    // COMM FUNCTIONS
    // Create Http/s client
    private boolean createHttpClient() {
        // Initialize client
        HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(params, HTTP.DEFAULT_CONTENT_CHARSET);
        HttpProtocolParams.setUseExpectContinue(params, true);
        HttpConnectionParams.setConnectionTimeout(params, 12000);
        HttpConnectionParams.setSoTimeout(params, 10000);
        SchemeRegistry schReg = new SchemeRegistry();
        schReg.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        schReg.register(new Scheme("https", org.apache.http.conn.ssl.SSLSocketFactory.getSocketFactory(), 443));
        ClientConnectionManager conMgr = new ThreadSafeClientConnManager(params, schReg);
        //cookieStore = new BasicCookieStore();
        httpclient = new DefaultHttpClient(conMgr, params);
        httpclient.getParams().setParameter(CoreProtocolPNames.USER_AGENT, userAgent);
        //httpclient.setCookieStore(cookieStore);
        return true;
    }

    // HTTP Get Request
    private JSONObject getJSONFromUrl(String url, boolean useAuth) {
        // create null object to return on errors
        JSONObject jObj = new JSONObject();
        String json;
        // create client if null
        if (httpclient == null) {
            createHttpClient();
        }
        InputStream is;
        // Making HTTP request
        try {
            HttpGet httpget = new HttpGet(url);
            if (isLoggedIn() && useAuth) {
                String tokenStr = getTokenValue("token_type") + " " + getTokenValue("access_token");
                System.out.println("Logged In, setting token header: " + tokenStr);
                httpget.addHeader("Authorization", tokenStr);
            }

            HttpResponse httpResponse = httpclient.execute(httpget);
            HttpEntity httpEntity = httpResponse.getEntity();
            if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                //System.out.println("Http Status: " + httpResponse.getStatusLine());
                //System.out.println("Http Headers: " + Arrays.toString(httpResponse.getAllHeaders()));
                return null;
            }
            is = httpEntity.getContent();

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return jObj;
        } catch (ClientProtocolException e) {
            e.printStackTrace();
            return jObj;
        } catch (IOException e) {
            e.printStackTrace();
            return jObj;
        }
        // read data
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "iso-8859-1"), 8);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            is.close();
            //System.out.println("Http Body: " + sb.toString());
            json = sb.toString();
        } catch (Exception e) {
            //Log.e("Buffer Error", "Error converting result " + e.toString());
            e.printStackTrace();
            return jObj;
        }
        // try parse the string to a JSON object
        try {
            jObj = new JSONObject(json);
        } catch (JSONException e) {
            //Log.e("JSON Parser", "Error parsing data " + e.toString());
            e.printStackTrace();
            return jObj;
        }
        // return JSON String
        return jObj;

    }

    // HTTPS POST Request
    private JSONObject getJSONFromPost(String url, ArrayList data, boolean addOauthHeaders) {
        JSONObject jObj = new JSONObject();
        String json = "";
        InputStream is = null;
        // create client if null
        if (httpclient == null) {
            createHttpClient();
        }
        try {
            HttpPost httppost = new HttpPost(url);
            if (addOauthHeaders) {
                // For oauth token retrieval and refresh
                httppost.addHeader("Authorization", "Basic " + Base64.encodeToString((OAUTH_CLIENTID + ":").getBytes(), Base64.URL_SAFE | Base64.NO_WRAP));
            }
            if (isLoggedIn()) {
                String tokenStr = getTokenValue("token_type") + " " + getTokenValue("access_token");
                System.out.println("Logged In, setting token header: " + tokenStr);
                httppost.addHeader("Authorization", tokenStr);
            }
            if (data != null) httppost.setEntity(new UrlEncodedFormEntity(data));

            HttpResponse httpResponse = httpclient.execute(httppost);
            HttpEntity httpEntity = httpResponse.getEntity();
            if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                //System.out.println("Http Status: " + httpResponse.getStatusLine());
                //System.out.println("Http Headers: " + Arrays.toString(httpResponse.getAllHeaders()));
                return null;
            }
            is = httpEntity.getContent();

        } catch (UnsupportedEncodingException | ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "iso-8859-1"), 8);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            is.close();
            //System.out.println("Http Body: " + sb.toString());
            json = sb.toString();
        } catch (Exception e) {
            System.out.println("Error converting result " + e.toString());
        }
        // try parse the string to a JSON object
        try {
            jObj = new JSONObject(json);
        } catch (JSONException e) {
            System.out.println("Error parsing data " + e.toString());
        }
        // return json response
        return jObj;
    }

    // OAUTH FUNCTIONS
    public static String OAUTH_ERROR = "";

    private void setOauthError(JSONObject jsonObject) {
        try {
            OAUTH_ERROR = jsonObject.getString("error");
        } catch (JSONException e) {
            OAUTH_ERROR = "Unspecified error";
            e.printStackTrace();
        }
    }

    public boolean isLoggedIn() {
        return oauthToken != null;
    }

    private boolean isTokenExpired() {
        Long now = (new Date()).getTime();
        Long expiry = null;
        try {
            expiry = oauthToken.getLong("expires_at");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return expiry < now;
    }

    private String getTokenValue(String key) {
        String token = "";
        try {
            token = oauthToken.getString(key);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return token;
    }

    public boolean retrieveToken(String code, String state) {
        if (!state.equals(oauthstate)) {
            System.out.println("oauth request result: Invalid state");
            return false;
        }
        String url = "https://www.reddit.com/api/v1/access_token";
        JSONObject resultjson;
        ArrayList params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("grant_type", "authorization_code"));
        params.add(new BasicNameValuePair("code", code));
        params.add(new BasicNameValuePair("redirect_uri", OAUTH_REDIRECT));
        resultjson = getJSONFromPost(url, params, true);
        if (resultjson.has("access_token")) {
            // login successful, set new token and save
            oauthToken = resultjson;
            try {
                oauthToken.put("expires_at", (new Date()).getTime() + oauthToken.getString("expires_in"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            // save session data and add cookie to client
            saveUserData();
            System.out.println("oauth request result: OK");
            return true;
        }
        // set error result
        setOauthError(resultjson);
        System.out.println("oauth request result error: " + resultjson.toString());
        return false;
    }

    private boolean refreshToken() {
        String url = "https://www.reddit.com/api/v1/access_token";
        JSONObject resultjson;
        ArrayList<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("grant_type", "refresh_token"));
        params.add(new BasicNameValuePair("code", getTokenValue("refresh_token")));
        resultjson = getJSONFromPost(url, params, true);
        if (resultjson.has("access_token")) {
            // login successful, update token and save
            try {
                oauthToken.put("access_token", resultjson.get("access_token"));
                oauthToken.put("token_type", resultjson.get("token_type"));
                Long expires_in = resultjson.getLong("expires_in");
                oauthToken.put("expires_in", expires_in);
                oauthToken.put("expires_at", (new Date()).getTime() + expires_in);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            // save session data and add cookie to client
            saveUserData();
            System.out.println("oauth refresh result: OK");
            return true;
        } else {
            // set error result
            setOauthError(resultjson);
            System.out.println("oauth refresh result error: " + resultjson.toString());
        }
        return false;
    }

    public void saveUserData() {
        SharedPreferences.Editor edit = sharedPrefs.edit();
        edit.putString("oauthtoken", oauthToken.toString());
        edit.putString("modhash", modhash);
        edit.apply();
    }

}
