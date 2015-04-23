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

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.text.Html;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import android.widget.TextView;

import com.joanzapata.android.iconify.Iconify;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class Rservice extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new ListRemoteViewsFactory(this.getApplicationContext(), intent);
    }
}

class ListRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private Context mContext = null;
    private int appWidgetId;
    private JSONArray data;
    private GlobalObjects global;
    private SharedPreferences mSharedPreferences;
    private String titleFontSize = "16";
    private int[] themeColors;
    private boolean loadCached = false; // tells the ondatasetchanged function that it should not download any further items, cache is loaded
    private boolean loadThumbnails = false;
    private boolean bigThumbs = false;
    private boolean hideInf = false;
    private Bitmap[] images;

    public ListRemoteViewsFactory(Context context, Intent intent) {
        this.mContext = context;
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        global = ((GlobalObjects) context.getApplicationContext());
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        //System.out.println("New view factory created for widget ID:"+appWidgetId);
        // Set thread network policy to prevent network on main thread exceptions.
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        // if this is a user request (apart from 'loadmore') or an auto update, do not attempt to load cache.
        // when a user clicks load more and a new view factory needs to be created we don't want to bypass cache, we want to load the cached items
        int loadType = global.getLoadType();
        if (!global.getBypassCache() || loadType == GlobalObjects.LOADTYPE_LOADMORE) {
            // load cached data
            data = global.getFeed(mSharedPreferences, appWidgetId);
            if (data.length() != 0) {
                titleFontSize = mSharedPreferences.getString(context.getString(R.string.widget_theme_pref), "16");
                try {
                    lastItemId = data.getJSONObject(data.length() - 1).getJSONObject("data").getString("name");
                } catch (JSONException e) {
                    lastItemId = "0"; // Could not get last item ID; perform a reload next time and show error view :(
                    e.printStackTrace();
                }
                if (loadType == GlobalObjects.LOADTYPE_LOAD) {
                    loadCached = true; // this isn't a loadmore request, the cache is loaded and we're done
                    //System.out.println("Cache loaded, no user request received.");
                }
            } else {
                loadReddits(false); // No feed items; do a reload.
            }
        } else {
            data = new JSONArray(); // set empty data to prevent any NPE
        }
    }

    @Override
    public void onCreate() {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        endOfFeed = false;
        // get thumbnail load preference for the widget
        loadThumbnails = mSharedPreferences.getBoolean("thumbnails-" + appWidgetId, true);
        bigThumbs = mSharedPreferences.getBoolean("bigthumbs-" + appWidgetId, false);
        hideInf = mSharedPreferences.getBoolean("hideinf-" + appWidgetId, false);
        images = new Bitmap[]{
                GlobalObjects.getFontBitmap(mContext, String.valueOf(Iconify.IconValue.fa_star.character()), Color.parseColor("#FFFF00"), 36),
                GlobalObjects.getFontBitmap(mContext, String.valueOf(Iconify.IconValue.fa_comment.character()), Color.parseColor("#C3DBF6"), 36)
        };
    }

    @Override
    public void onDestroy() {
        // no-op
        //System.out.println("Service detroyed");
    }

    @Override
    public int getCount() {
        return (data.length() + 1); // plus 1 advertises the "load more" item to the listview without having to add it to the data source
    }

    @Override
    public RemoteViews getViewAt(int position) {
        RemoteViews row;
        if (position > data.length()) {
            return null; //  prevent errornous views
        }
        // check if its the last view and return loading view instead of normal row
        if (position == data.length()) {
            // build load more item
            //System.out.println("load more getViewAt("+position+") firing");
            RemoteViews loadmorerow = new RemoteViews(mContext.getPackageName(), R.layout.listrowloadmore);
            if (endOfFeed) {
                loadmorerow.setTextViewText(R.id.loadmoretxt, "There's nothing more here");
            } else {
                loadmorerow.setTextViewText(R.id.loadmoretxt, "Load more...");
            }
            loadmorerow.setTextColor(R.id.loadmoretxt, themeColors[0]);
            Intent i = new Intent();
            Bundle extras = new Bundle();
            extras.putString(WidgetProvider.ITEM_ID, "0"); // zero will be an indicator in the onreceive function of widget provider if its not present it forces a reload
            i.putExtras(extras);
            loadmorerow.setOnClickFillInIntent(R.id.listrowloadmore, i);
            return loadmorerow;
        } else {
            // build normal item
            String title = "";
            String url = "";
            String permalink = "";
            String thumbnail = "";
            String domain = "";
            String id = "";
            int score = 0;
            int numcomments = 0;
            boolean nsfw = false;
            try {
                JSONObject tempobj = data.getJSONObject(position).getJSONObject("data");
                title = tempobj.getString("title");
                //userlikes = tempobj.getString("likes");
                domain = tempobj.getString("domain");
                id = tempobj.getString("name");
                url = tempobj.getString("url");
                permalink = tempobj.getString("permalink");
                thumbnail = (String) tempobj.get("thumbnail"); // we have to call get and cast cause its not in quotes
                score = tempobj.getInt("score");
                numcomments = tempobj.getInt("num_comments");
                nsfw = tempobj.getBoolean("over_18");
            } catch (JSONException e) {
                e.printStackTrace();
                // return null; // The view is invalid;
            }
            // create remote view from specified layout
            if (bigThumbs) {
                row = new RemoteViews(mContext.getPackageName(), R.layout.listrowbigthumb);
            } else {
                row = new RemoteViews(mContext.getPackageName(), R.layout.listrow);
            }
            // build view
            row.setImageViewBitmap(R.id.votesicon, images[0]);
            row.setImageViewBitmap(R.id.commentsicon, images[1]);
            row.setTextViewText(R.id.listheading, Html.fromHtml(title).toString());
            row.setFloat(R.id.listheading, "setTextSize", Integer.valueOf(titleFontSize)); // use for compatibility setTextViewTextSize only introduced in API 16
            row.setTextColor(R.id.listheading, themeColors[0]);
            row.setTextViewText(R.id.sourcetxt, domain);
            row.setTextColor(R.id.sourcetxt, themeColors[3]);
            row.setTextColor(R.id.votestxt, themeColors[4]);
            row.setTextColor(R.id.commentstxt, themeColors[4]);
            row.setTextViewText(R.id.votestxt, String.valueOf(score));
            row.setTextViewText(R.id.commentstxt, String.valueOf(numcomments));
            row.setInt(R.id.listdivider, "setBackgroundColor", themeColors[2]);
            row.setViewVisibility(R.id.nsfwflag, nsfw ? TextView.VISIBLE : TextView.GONE);
            // add extras and set click intent
            Intent i = new Intent();
            Bundle extras = new Bundle();
            extras.putString(WidgetProvider.ITEM_ID, id);
            extras.putInt("itemposition", position);
            extras.putString(WidgetProvider.ITEM_URL, url);
            extras.putString(WidgetProvider.ITEM_PERMALINK, permalink);
            i.putExtras(extras);
            row.setOnClickFillInIntent(R.id.listrow, i);
            // load thumbnail if they are enabled for this widget
            if (loadThumbnails) {
                // load big image if preference is set
                if (!thumbnail.equals("")) { // check for thumbnail; self is used to display the thinking logo on the reddit site, we'll just show nothing for now
                    if (thumbnail.equals("nsfw") || thumbnail.equals("self") || thumbnail.equals("default")) {
                        int resource = 0;
                        switch (thumbnail) {
                            case "nsfw":
                                resource = R.drawable.nsfw;
                                break;
                            case "default":
                            case "self":
                                resource = R.drawable.self_default;
                                break;
                        }
                        row.setImageViewResource(R.id.thumbnail, resource);
                        row.setViewVisibility(R.id.thumbnail, View.VISIBLE);
                        //System.out.println("Loading default image: "+thumbnail);
                    } else {
                        Bitmap bitmap;
                        String fileurl = mContext.getCacheDir() + "/thumbcache-" + appWidgetId + "/" + id + ".png";
                        // check if the image is in cache
                        if (new File(fileurl).exists()) {
                            bitmap = BitmapFactory.decodeFile(fileurl);
                            saveImageToStorage(bitmap, id);
                        } else {
                            // download the image
                            bitmap = loadImage(thumbnail);
                        }
                        if (bitmap != null) {
                            row.setImageViewBitmap(R.id.thumbnail, bitmap);
                            row.setViewVisibility(R.id.thumbnail, View.VISIBLE);
                        } else {
                            // row.setImageViewResource(R.id.thumbnail, android.R.drawable.stat_notify_error); for later
                            row.setViewVisibility(R.id.thumbnail, View.GONE);
                        }
                    }
                } else {
                    row.setViewVisibility(R.id.thumbnail, View.GONE);
                }
            } else {
                row.setViewVisibility(R.id.thumbnail, View.GONE);
            }
            // hide info bar if options set
            if (hideInf) {
                row.setViewVisibility(R.id.infbox, View.GONE);
            } else {
                row.setViewVisibility(R.id.infbox, View.VISIBLE);
            }
        }
        //System.out.println("getViewAt("+position+");");
        return row;
    }

    private Bitmap loadImage(String urlstr) {
        URL url;
        Bitmap bmp;
        try {
            url = new URL(urlstr);
            URLConnection con = url.openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            bmp = BitmapFactory.decodeStream(con.getInputStream());
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return bmp;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private boolean saveImageToStorage(Bitmap image, String redditid) {
        try {
            File file = new File(mContext.getCacheDir().getPath() + "/thumbcache-" + appWidgetId + "/", redditid + ".png");
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            FileOutputStream fos = new FileOutputStream(file);
            image.compress(Bitmap.CompressFormat.PNG, 100, fos);
            // 100 means no compression, the lower you go, the stronger the compression
            fos.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void clearImageCache() {
        // delete all images in the cache folder.
        DeleteRecursive(new File(mContext.getCacheDir() + "/thumbcache-app"));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void DeleteRecursive(File fileOrDirectory) {

        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                DeleteRecursive(child);

        fileOrDirectory.delete();

    }

    @Override
    public RemoteViews getLoadingView() {
        RemoteViews rowload = new RemoteViews(mContext.getPackageName(), R.layout.listrowload);
        if (themeColors == null) {
            themeColors = global.getThemeColors(); // prevents null pointer
        }
        rowload.setTextColor(R.id.listloadtxt, themeColors[0]);
        return rowload;
    }

    @Override
    public int getViewTypeCount() {
        return (3);
    }

    @Override
    public long getItemId(int position) {
        return (position);
    }

    @Override
    public boolean hasStableIds() {
        return (false);
    }

    @Override
    public void onDataSetChanged() {
        // get thumbnail load preference for the widget
        loadThumbnails = mSharedPreferences.getBoolean("thumbnails-" + appWidgetId, true);
        bigThumbs = mSharedPreferences.getBoolean("bigthumbs-" + appWidgetId, false);
        hideInf = mSharedPreferences.getBoolean("hideinf-" + appWidgetId, false);
        titleFontSize = mSharedPreferences.getString("titlefontpref", "16");
        themeColors = global.getThemeColors(); // reset theme colors
        int loadType = global.getLoadType();
        if (!loadCached) {
            loadCached = (loadType == GlobalObjects.LOADTYPE_REFRESH_VIEW); // see if its just a call to refresh view and set var accordingly but only check it if load cached is not already set true in the above constructor
        }
        //System.out.println("Loading type "+loadtype);
        if (!loadCached) {
            // refresh data
            if (loadType == GlobalObjects.LOADTYPE_LOADMORE && !lastItemId.equals("0")) { // do not attempt a "loadmore" if we don't have a valid item ID; this would append items to the list, instead perform a full reload
                global.setLoad();
                loadMoreReddits();
            } else {
                //System.out.println("loadReddits();");
                loadReddits(false);
            }
            global.setBypassCache(false); // don't bypass the cache check the next time the service starts
        } else {
            loadCached = false;
            global.setLoad();
            // hide loader
            hideWidgetLoader(false, false); // don't go to top as the user is probably interacting with the list
        }

    }

    private String lastItemId = "0";
    private boolean endOfFeed = false;

    private void loadMoreReddits() {
        //System.out.println("loadMoreReddits();");
        loadReddits(true);
    }

    private void loadReddits(boolean loadMore) {
        String curFeed = mSharedPreferences.getString("currentfeed-" + appWidgetId, "technology");
        String sort = mSharedPreferences.getString("sort-" + appWidgetId, "hot");
        // Load more or initial load/reload?
        if (loadMore) {
            // fetch 25 more after current last item and append to the list
            JSONArray tempData = global.mRedditData.getRedditFeed(curFeed, sort, 25, lastItemId);
            if (!isError(tempData)) {
                if (tempData.length() == 0) {
                    endOfFeed = true;
                } else {
                    endOfFeed = false;
                    int i = 0;
                    while (i < tempData.length()) {
                        try {
                            data.put(tempData.get(i));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        i++;
                    }
                    // Save feed data
                    global.setFeed(mSharedPreferences, appWidgetId, data);
                }
            } else {
                hideWidgetLoader(false, true); // don't go to top of list and show error icon
                return;
            }
        } else {
            endOfFeed = false;
            // clear image cache
            clearImageCache();
            // reload feed
            int limit = Integer.valueOf(mSharedPreferences.getString("numitemloadpref", "25"));
            JSONArray tempArray;
            tempArray = global.mRedditData.getRedditFeed(curFeed, sort, limit, "0");
            // check if data is valid; if the getredditfeed function fails to create a connection it returns -1 in the first value of the array
            if (!isError(tempArray)) {
                data = tempArray;
                if (data.length() == 0) {
                    endOfFeed = true;
                }
                // Save feed data
                global.setFeed(mSharedPreferences, appWidgetId, data);
            } else {
                hideWidgetLoader(false, true); // don't go to top of list and show error icon
                return;
            }
        }
        // set last item id for "loadmore use"
        // Damn reddit doesn't allow you to specify a start index for the data, instead you have to reference the last item id from the prev page :(
        try {
            lastItemId = data.getJSONObject(data.length() - 1).getJSONObject("data").getString("name"); // name is actually the unique id we want
        } catch (JSONException e) {
            lastItemId = "0"; // Could not get last item ID; perform a reload next time and show error view :(
            e.printStackTrace();
        }

        // hide loader
        if (loadMore) {
            hideWidgetLoader(false, false); // don't go to top of list
        } else {
            hideWidgetLoader(true, false); // go to top
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

    // hide appwidget loader
    private void hideWidgetLoader(boolean goToTopOfList, boolean showError) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(mContext);
        // get theme layout id
        int layout = 1;
        switch (Integer.valueOf(mSharedPreferences.getString("widgetthemepref", "1"))) {
            case 1:
                layout = R.layout.widgetmain;
                break;
            case 2:
                layout = R.layout.widgetdark;
                break;
            case 3:
                layout = R.layout.widgetholo;
                break;
            case 4:
                layout = R.layout.widgetdarkholo;
                break;
            case 5:
                layout = R.layout.widgettrans;
                break;
        }
        RemoteViews views = new RemoteViews(mContext.getPackageName(), layout);
        views.setViewVisibility(R.id.srloader, View.INVISIBLE);
        // go to the top of the list view
        if (goToTopOfList) {
            views.setScrollPosition(R.id.listview, 0);
        }
        if (showError) {
            views.setViewVisibility(R.id.erroricon, View.VISIBLE);
        }
        mgr.partiallyUpdateAppWidget(appWidgetId, views);
    }
}
