package com.example.reddinator;


import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.StrictMode;
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
	
	public ListRemoteViewsFactory(Context ctxt, Intent intent) {
		this.ctxt = ctxt;
		appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
				AppWidgetManager.INVALID_APPWIDGET_ID);
	}
	
	private RedditData rdata;
	private JSONArray data;
	
	@Override
	public void onCreate() {
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
		StrictMode.setThreadPolicy(policy); 
		rdata = new RedditData();
		try {
			data = rdata.getJSONFromUrl("technology", "hot").getJSONObject("data").getJSONArray("children");
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			data = new JSONArray();
		}
		/*try {
			System.out.print(data.get(0).toString());
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
	}

	@Override
	public void onDestroy() {
		// no-op
	}

	@Override
	public int getCount() {
		return (data.length());
	}

	@Override
	public RemoteViews getViewAt(int position) {
		String name = "";
		String url = "";
		try {
			JSONObject tempobj = data.getJSONObject(position).getJSONObject("data");
			name = tempobj.getString("title");
			url =  tempobj.getString("url");
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		RemoteViews row = new RemoteViews(ctxt.getPackageName(), R.layout.listrow);

		row.setTextViewText(android.R.id.text1, name);

		Intent i = new Intent();
		Bundle extras = new Bundle();
		extras.putString(WidgetProvider.ITEM_URL, url);
		i.putExtras(extras);
		row.setOnClickFillInIntent(android.R.id.text1, i);
		System.out.println("getViewAt() firing!");
		return (row);
	}

	@Override
	public RemoteViews getLoadingView() {
		return (null);
	}

	@Override
	public int getViewTypeCount() {
		return (1);
	}

	@Override
	public long getItemId(int position) {
		return (position);
	}

	@Override
	public boolean hasStableIds() {
		return (true);
	}

	@Override
	public void onDataSetChanged() {
		// refresh data
		try {
			data = rdata.getJSONFromUrl("technology", "hot").getJSONObject("data").getJSONArray("children");
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			data = new JSONArray();
		}
	}
}
