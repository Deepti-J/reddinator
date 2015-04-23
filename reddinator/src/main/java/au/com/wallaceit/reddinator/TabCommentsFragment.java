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

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class TabCommentsFragment extends Fragment {
    private Context mContext;
    public WebView mWebView;
    private boolean mFirstTime = true;
    private LinearLayout ll;
    private GlobalObjects global;
    private SharedPreferences mSharedPreferences;
    public String articleId;
    private String currentSort = "best";

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this.getActivity();
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        global = (GlobalObjects) mContext.getApplicationContext();

        // get shared preferences
        articleId = getActivity().getIntent().getStringExtra(WidgetProvider.ITEM_ID);

        ll = new LinearLayout(mContext);
        ll.setLayoutParams(new WebView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 0, 0));
        // fixes for webview not taking keyboard input on some devices
        mWebView = new WebView(mContext);
        mWebView.setLayoutParams(new WebView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 0, 0));
        ll.addView(mWebView);
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true); // enable ecmascript
        webSettings.setDomStorageEnabled(true); // some video sites require dom storage
        webSettings.setSupportZoom(false);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);
        int fontSize = Integer.parseInt(mSharedPreferences.getString("commentfontpref", "20"));
        webSettings.setDefaultFontSize(fontSize);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webSettings.setDisplayZoomControls(false);

        String[] themeColors = global.getThemeColorHex();
        mSharedPreferences.getString("titlefontpref", "16");


        final String themeStr = StringUtils.join(themeColors, ",");
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.indexOf("http://www.reddit.com/")==0){
                    Intent i = new Intent(mContext, WebViewActivity.class);
                    i.putExtra("url", url);
                    startActivity(i);
                } else {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(i);
                }
                return true; // always override url
            }

            public void onPageFinished(WebView view, String url) {
                mWebView.loadUrl("javascript:setTheme(\"" + StringEscapeUtils.escapeJavaScript(themeStr) + "\")");
                loadComments("best");
            }
        });

        mWebView.requestFocus(View.FOCUS_DOWN);
        WebInterface webInterface = new WebInterface(mContext);
        mWebView.addJavascriptInterface(webInterface, "Reddinator");

        mWebView.loadUrl("file:///android_asset/comments.html");
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        //ll = (LinearLayout) inflater.inflate(R.layout.commentstab, container, false);

        if (container == null) {
            return null;
        }
        if (mFirstTime) {
            //ll.addView(mWebView);
            mFirstTime = false;
        } else {
            ((ViewGroup) ll.getParent()).removeView(ll);
        }

        return ll;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //mWebView.saveState(outState);
    }

    @Override
    public void onPause() {
        super.onPause();
        //mWebView.saveState(WVState);
    }

    public class WebInterface {
        Context mContext;

        /**
         * Instantiate the interface and set the context
         */
        WebInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void reloadComments(String sort) {
            System.out.println("Reload command received");
            loadComments(sort);
        }

        @JavascriptInterface
        public void loadChildren(String moreId, String children) {
            System.out.println("Load more command received");
            CommentsLoader commentsLoader = new CommentsLoader(currentSort, moreId, children);
            commentsLoader.execute();
        }

        @JavascriptInterface
        public void vote(String thingId, int direction) {
            System.out.println("Vote command received: "+direction);
            ((ViewRedditActivity) getActivity()).setTitleText("Voting...");
            CommentsVoteTask voteTask = new CommentsVoteTask(thingId, direction);
            voteTask.execute();
        }
    }

    private void loadComments(String sort) {
        if (sort != null)
            currentSort = sort;
        CommentsLoader commentsLoader = new CommentsLoader(currentSort);
        commentsLoader.execute();
    }

    class CommentsLoader extends AsyncTask<Void, Integer, String> {

        private boolean loadMore = false;
        private String mSort = "best";
        private String mMoreId;
        private String mChildren;

        public CommentsLoader(String sort){
            mSort = sort;
        }

        public CommentsLoader(String sort, String moreId, String children) {
            mSort = sort;
            if (children != null && !children.equals("")) {
                loadMore = true;
                mMoreId = moreId;
                mChildren = children;
            }
        }

        @Override
        protected String doInBackground(Void... none) {
            //String sort = mSharedPreferences.getString("sort-app", "hot");
            JSONArray data;

            JSONArray tempArray;
            if (loadMore) {
                String articleId = getActivity().getIntent().getStringExtra(WidgetProvider.ITEM_ID);
                tempArray = global.mRedditData.getChildComments(mMoreId, articleId, mChildren, mSort);
            } else {
                String permalink = getActivity().getIntent().getStringExtra(WidgetProvider.ITEM_PERMALINK);
                // reloading
                //int limit = Integer.valueOf(mSharedPreferences.getString("numitemloadpref", "25"));
                tempArray = global.mRedditData.getCommentsFeed(permalink, mSort, 25);
            }
            if (!isError(tempArray)) {
                data = tempArray;
                if (data.length() == 0) {
                    return "";
                }
                // save feed
                //global.setFeed(mSharedPreferences, 0, data);
            } else {
                return "-1"; // Indicates error
            }

            return data.toString();
        }

        @Override
        protected void onPostExecute(String result) {
            if (result.equals("")) {
                mWebView.loadUrl("javascript:showLoadingView('No comments here')");
            } else if (result.equals("-1")) {
                // show error
                mWebView.loadUrl("javascript:showLoadingView('Error loading comments')");
                Toast.makeText(getActivity(), result, Toast.LENGTH_LONG).show();
            } else {
                if (loadMore){
                    mWebView.loadUrl("javascript:populateChildComments(\""+mMoreId+"\", \"" + StringEscapeUtils.escapeJavaScript(result) + "\")");
                } else {
                    mWebView.loadUrl("javascript:populateComments(\"" + StringEscapeUtils.escapeJavaScript(result) + "\")");
                }
            }

        }

        // check if the array is an error array
        private boolean isError(JSONArray tempArray) {
            boolean error;
            if (tempArray == null) {
                return true; // null error
            }
            if (tempArray.length() > 0) {
                try {
                    error = tempArray.getString(0).equals("-1");
                } catch (JSONException e) {
                    error = true;
                    e.printStackTrace();
                }
            } else {
                error = false; // empty array means no more feed items
            }
            return error;
        }
    }

    class CommentsVoteTask extends AsyncTask<String, Integer, String> {
        JSONObject item;
        private String redditId;
        private int direction;

        public CommentsVoteTask(String thingId, int dir) {
            direction = dir;
            redditId = thingId;
        }

        @Override
        protected String doInBackground(String... strings) {
            // Do the vote
            try {
                return global.mRedditData.vote(redditId, direction);
            } catch (RedditData.RedditApiException e) {
                e.printStackTrace();
                return e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            ((ViewRedditActivity) getActivity()).setTitleText("Reddinator"); // reset title
            switch (result) {
                case "OK":
                    mWebView.loadUrl("javascript:voteCallback(\"" + redditId + "\", \"" + direction + "\")");
                    break;
                case "LOGIN":
                    global.mRedditData.initiateLogin(getActivity());
                    break;
                default:
                    // show error
                    Toast.makeText(getActivity(), result, Toast.LENGTH_LONG).show();
                    break;
            }
            //listAdapter.hideAppLoader(false, false);
        }
    }
}
