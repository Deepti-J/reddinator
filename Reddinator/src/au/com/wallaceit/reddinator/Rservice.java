package au.com.wallaceit.reddinator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import au.com.wallaceit.reddinator.R;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

public class Rservice extends RemoteViewsService {
	@Override
	public RemoteViewsFactory onGetViewFactory(Intent intent) {
		return new ListRemoteViewsFactory(this.getApplicationContext(), intent);
	}
}

class ListRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
	private Context ctxt = null;
	private int appWidgetId;
	private JSONArray data;
	private GlobalObjects global;
	private SharedPreferences prefs;
	private Editor prefseditor;
	private String itemfontsize = "16";
	private boolean loadcached = false; // tells the ondatasetchanged function that it should not download any further items, cache is loaded
	
	public ListRemoteViewsFactory(Context ctxt, Intent intent) {
		this.ctxt = ctxt;
		appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
		global = ((GlobalObjects) ctxt.getApplicationContext());
		prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
		prefseditor = prefs.edit();
		//System.out.println("New view factory created for widget ID:"+appWidgetId);
		// if this is a user request (apart from 'loadmore') or an auto update, do not attempt to load cache. 
		// when a user clicks load more and a new view factory needs to be created we don't want to bypass cache, we want to load the cached items
		int loadtype = global.getLoadType();
		if (!global.getBypassCache() || loadtype ==  GlobalObjects.LOADTYPE_LOADMORE){
			//System.out.println("This is not a standard user request or auto update, checking for cache");
			try {
				data = new JSONArray(prefs.getString("feeddata-"+appWidgetId, "[]"));
			} catch (JSONException e) {
				data = new JSONArray();
				e.printStackTrace();
			}
			//System.out.println("cached Data length: "+data.length());
			if (data.length() != 0){
				itemfontsize = prefs.getString("widgetfontpref", "16");
				try {
					lastitemid = data.getJSONObject(data.length()-1).getJSONObject("data").getString("name");
				} catch (JSONException e) {
					lastitemid = "0"; // Could not get last item ID; perform a reload next time and show error view :(
					e.printStackTrace();
				}
				if (loadtype == GlobalObjects.LOADTYPE_LOAD){
					loadcached = true; // this isn't a loadmore request, the cache is loaded and we're done
					//System.out.println("Cache loaded, no user request received.");
				}
			}
		} else {
			data = new JSONArray(); // set empty data to prevent any NPE
		}
	}
	
	@Override
	public void onCreate() {
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
		StrictMode.setThreadPolicy(policy);
		endoffeed = false;
	}

	@Override
	public void onDestroy() {
		// no-op
		System.out.println("Service detroyed");
	}

	@Override
	public int getCount() {
		return (data.length() + 1); // plus 1 advertises the "load more" item to the listview without having to add it to the data source
	}
	
	@Override
	public RemoteViews getViewAt(int position) {
		RemoteViews row;
		if (position > data.length()){
			return null; //  prevent errornous views
		}
		// check if its the last view and return loading view instead of normal row
		if (position == data.length()) {
			//System.out.println("load more getViewAt("+position+") firing"); 
			RemoteViews loadmorerow = new RemoteViews(ctxt.getPackageName(), R.layout.listrowloadmore);
			if (endoffeed){ 
				loadmorerow.setTextViewText(R.id.loadmoretxt, "There's nothing more here");
			} else {
				loadmorerow.setTextViewText(R.id.loadmoretxt, "Load more...");
			}
			Intent i = new Intent();
			Bundle extras = new Bundle();
			extras.putString(WidgetProvider.ITEM_ID, "0"); // zero will be an indicator in the onreceive function of widget provider if its not present it forces a reload
			i.putExtras(extras);
			loadmorerow.setOnClickFillInIntent(R.id.listrowloadmore, i);
			return loadmorerow;
		} else {
			String name = "";
			String url = "";
			String permalink = "";
			String domain = "";
			String id = "";
			int score = 0;
			int numcomments = 0;
			try {
				JSONObject tempobj = data.getJSONObject(position).getJSONObject("data");
				name = tempobj.getString("title");
				domain = tempobj.getString("domain");
				id = tempobj.getString("id");
				url = tempobj.getString("url");
				permalink = tempobj.getString("permalink");
				score = tempobj.getInt("score");
				numcomments = tempobj.getInt("num_comments");
			} catch (JSONException e) {
				e.printStackTrace();
				// return null; // The view is invalid;
			}
			// build view
			row = new RemoteViews(ctxt.getPackageName(), R.layout.listrow);
			row.setTextViewText(R.id.listheading, Html.fromHtml(name).toString());
			// row.setTextViewTextSize(R.id.listheading, TypedValue.COMPLEX_UNIT_SP, Integer.valueOf(itemfontsize)); // This was only introduced in api 16, using the method below instead
			row.setFloat(R.id.listheading, "setTextSize", Integer.valueOf(itemfontsize)); // use for compatibility
			row.setTextViewText(R.id.sourcetxt, domain);
			row.setTextViewText(R.id.votestxt, String.valueOf(score));
			row.setTextViewText(R.id.commentstxt, String.valueOf(numcomments));
			// add extras and set click intent
			Intent i = new Intent();
			Bundle extras = new Bundle();
			extras.putString(WidgetProvider.ITEM_ID, id);
			extras.putString(WidgetProvider.ITEM_URL, url);
			extras.putString(WidgetProvider.ITEM_PERMALINK, permalink);
			extras.putString(WidgetProvider.ITEM_TXT, name);
			extras.putString(WidgetProvider.ITEM_DOMAIN, domain);
			extras.putInt(WidgetProvider.ITEM_VOTES, score);
			i.putExtras(extras);
			row.setOnClickFillInIntent(R.id.listrow, i);
		}
		//System.out.println("getViewAt("+position+");");
		return row;
	}

	@Override
	public RemoteViews getLoadingView() {
		RemoteViews rowload = new RemoteViews(ctxt.getPackageName(), R.layout.listrowload);
		return rowload;
	}

	@Override
	public int getViewTypeCount() {
		return (2);
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
		if (!loadcached){
			// refresh data
			if (global.getLoadType() == GlobalObjects.LOADTYPE_LOADMORE && !lastitemid.equals("0")){ // do not attempt a "loadmore" if we don't have a valid item ID; this would append items to the list, instead perform a full reload
				global.SetLoad();
				loadMoreReddits();
			} else {
				//System.out.println("loadReddits();");
				loadReddits(false);
			}
			global.setBypassCache(false); // don't bypass the cache check the next time the service starts
		} else {
			loadcached = false;
			// hide loader
			hideWidgetLoader(false, false); // don't go to top as the user is probably interacting with the list
		}
	}
	private String lastitemid = "0";
	private boolean endoffeed = false;
	private void loadMoreReddits() {
		//System.out.println("loadMoreReddits();");
		loadReddits(true);
	}
	private void loadReddits(boolean loadmore){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
		String curfeed = prefs.getString("currentfeed-"+appWidgetId, "technology");
		String sort = prefs.getString("sort-"+appWidgetId, "hot");
		itemfontsize = prefs.getString("widgetfontpref", "16");
		// Load more or initial load/reload?
		if (loadmore){
			// fetch 25 more after current last item and append to the list
			JSONArray tempdata = global.rdata.getRedditFeed(curfeed, sort, 25, lastitemid);
			if (!isError(tempdata)){
				if (tempdata.length() == 0){
					endoffeed = true;
				} else {
					endoffeed = false;
					int i = 0;
					while (i<tempdata.length()){
						try {
							data.put(tempdata.get(i));
						} catch (JSONException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						i++;
					}
					prefseditor.putString("feeddata-"+appWidgetId, data.toString());
					prefseditor.commit();
				}
			} else {
				hideWidgetLoader(false, true); // don't go to top of list and show error icon
				return;
			}
		} else {
			endoffeed = false;
			// reloading
			int limit = Integer.valueOf(prefs.getString("numitemloadpref", "25"));
			JSONArray temparray = global.rdata.getRedditFeed(curfeed, sort, limit, "0");
			// check if data is valid; if the getredditfeed function fails to create a connection it returns -1 in the first value of the array
			if (!isError(temparray)){
				data = temparray;
				if (data.length() == 0){
					endoffeed=true;
				}
				prefseditor.putString("feeddata-"+appWidgetId, data.toString());
				prefseditor.commit();
			} else {
				hideWidgetLoader(false, true); // don't go to top of list and show error icon
				return;
			}
		}
		// set last item id for "loadmore use"
		// Damn reddit doesn't allow you to specify a start index for the data, instead you have to reference the last item id from the prev page :(
		try {
			lastitemid = data.getJSONObject(data.length()-1).getJSONObject("data").getString("name"); // name is actually the unique id we want
		} catch (JSONException e) {
			lastitemid = "0"; // Could not get last item ID; perform a reload next time and show error view :(
			e.printStackTrace();
		};
		// hide loader
		if (loadmore){
			hideWidgetLoader(false, false); // don't go to top of list
		} else {
			hideWidgetLoader(true, false); // go to top
		}
	}
	// check if the array is an error array
	private boolean isError(JSONArray temparray){
		boolean error;
		try {
			error = temparray.getString(0).equals("-1");
		} catch (JSONException e) {
			error = true;
			e.printStackTrace();
		}
		return error;
	}
	// hide appwidget loader
	private void hideWidgetLoader(boolean gototopoflist, boolean showerror){
		AppWidgetManager mgr = AppWidgetManager.getInstance(ctxt);
		RemoteViews views = new RemoteViews(ctxt.getPackageName(), R.layout.widgetmain);
		views.setViewVisibility(R.id.srloader, View.INVISIBLE);
		// go to the top of the list view
		if (gototopoflist){
			views.setScrollPosition(R.id.listview, 0);
		}
		if (showerror){
			views.setViewVisibility(R.id.erroricon, View.VISIBLE);
		}
		mgr.partiallyUpdateAppWidget(appWidgetId, views);
	}
}
