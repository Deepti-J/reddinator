package com.example.reddinator;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.RemoteViews;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class WidgetProvider extends AppWidgetProvider {
	public static String ITEM_URL = "ITEM_URL";
	public static String ITEM_PERMALINK = "ITEM_PERMALINK";
	public static String ITEM_TXT = "ITEM_TXT";
	public static String ITEM_ID = "ITEM_ID";
	public static String ITEM_VOTES = "ITEM_VOTES";
	public static String ITEM_DOMAIN = "ITEM_DOMAIN";
	public static String ITEM_CLICK = "ITEM_CLICK";
	public static String ACTION_WIDGET_CLICK_PREFS = "Action_prefs";
	public static String APPWIDGET_UPDATE = "APPWIDGET_UPDATE_FEED";
	public WidgetProvider() {
	}

	@SuppressWarnings("deprecation")
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager,
			int[] appWidgetIds) {
		final int N = appWidgetIds.length;
		
        // Perform this loop procedure for each App Widget that belongs to this provider
        for (int i=0; i<N; i++) {
            int appWidgetId = appWidgetIds[i];
            // CONFIG BUTTON
            Intent intent = new Intent(context, PrefsActivity.class);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);  // Identifies the particular widget...
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
            PendingIntent pendIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            // PICK Subreddit BUTTON
            Intent srintent = new Intent(context, SubredditSelect.class);
            srintent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);  // Identifies the particular widget...
            srintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            srintent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
            PendingIntent srpendIntent = PendingIntent.getActivity(context, 0, srintent, PendingIntent.FLAG_UPDATE_CURRENT);
            // REMOTE DATA
            Intent servintent = new Intent(context, Rservice.class);
            servintent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i]); // Add the app widget ID to the intent extras.
            servintent.setData(Uri.parse(servintent.toUri(Intent.URI_INTENT_SCHEME)));
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widgetmain);
            // REFRESH BUTTON
            Intent irefresh = new Intent(context, WidgetProvider.class);
            irefresh.setAction(APPWIDGET_UPDATE);
            irefresh.setPackage(context.getPackageName());
            irefresh.putExtra("id", appWidgetId);
            PendingIntent rpIntent = PendingIntent.getBroadcast(context, 0, irefresh, PendingIntent.FLAG_UPDATE_CURRENT);
            // ITEM CLICK
            Intent clickintent = new Intent(context, WidgetProvider.class);
            clickintent.setAction(ITEM_CLICK);
            clickintent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i]);
            clickintent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
            PendingIntent clickPI = PendingIntent.getBroadcast(context, 0, clickintent, PendingIntent.FLAG_UPDATE_CURRENT);
            // ADD ALL TO REMOTE VIEWS
            views.setPendingIntentTemplate(R.id.listview, clickPI);
            views.setOnClickPendingIntent(R.id.subreddittxt, srpendIntent);
            views.setOnClickPendingIntent(R.id.refreshbutton, rpIntent);
            views.setOnClickPendingIntent(R.id.prefsbutton, pendIntent);
            views.setEmptyView(R.id.listview, R.id.empty_list_view);
            // set current feed title
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    		String curfeed = prefs.getString("currentfeed", "technology");
    		views.setTextViewText(R.id.subreddittxt, curfeed);
            // This is how you populate the data.
            views.setRemoteAdapter(appWidgetIds[i], R.id.listview, servintent);
            // Tell the AppWidgetManager to perform an update on the current app widget
            appWidgetManager.updateAppWidget(appWidgetId , views);
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.listview);
            System.out.println("onUpdate() fires!");
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds);
	}
	// config activity not firing update, this is a workaround
	/*public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int mAppWidgetId){
		RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widgetmain);
		appWidgetManager.updateAppWidget(mAppWidgetId, views);
	}*/
	@Override
	public void onAppWidgetOptionsChanged(Context context,
			AppWidgetManager appWidgetManager, int appWidgetId,
			Bundle newOptions) {
		System.out.println("onAppWidgetOptionsChanged fired");
		this.onUpdate(context, appWidgetManager, new int[]{appWidgetId}); // fix for the widget not loading the second time round (adding to the homescreen)
		super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId,
				newOptions);
	}

	@Override
	public void onDeleted(Context context, int[] appWidgetIds) {
		System.out.println("onDeleted fired");
		super.onDeleted(context, appWidgetIds);
	}

	@Override
	public void onDisabled(Context context) {
		System.out.println("onDisabled fired");
		super.onDisabled(context);
	}

	@Override
	public void onEnabled(Context context) {
		System.out.println("onEnabled fired");
		/*Intent intent = new Intent();
		intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
		context.sendBroadcast(intent);*/
        super.onEnabled(context);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		int widgetid = intent.getExtras().getInt("id");
		if (action.equals(APPWIDGET_UPDATE)) {
			AppWidgetManager mgr = AppWidgetManager.getInstance(context);
			// show loader
			RemoteViews views = new RemoteViews(intent.getPackage(), R.layout.widgetmain);
			views.setViewVisibility(R.id.srloader, View.VISIBLE);
			//views.setViewVisibility(R.id.refreshbutton, View.GONE);
			mgr.partiallyUpdateAppWidget(widgetid, views);
			mgr.notifyAppWidgetViewDataChanged(widgetid, R.id.listview);
			System.out.println("updating feed");
		}
		if (action.equals(ITEM_CLICK)) {
			// check if its the load more button being clicked
			String redditid = intent.getExtras().getString(WidgetProvider.ITEM_ID);
			if (redditid.equals("0")){
				// TEST CODE
				/*GlobalObjects global = ((GlobalObjects) context.getApplicationContext());
				global.setLoadMore();
				AppWidgetManager mgr = AppWidgetManager.getInstance(context);
				// show loader
				RemoteViews views = new RemoteViews(intent.getPackage(), R.layout.widgetmain);
				views.setViewVisibility(R.id.srloader, View.VISIBLE);
				//views.setViewVisibility(R.id.refreshbutton, View.GONE);
				mgr.partiallyUpdateAppWidget(widgetid, views);
				mgr.notifyAppWidgetViewDataChanged(widgetid, R.id.listview);*/
				
				// open google link for now
				System.out.println("loadmore intent captured");
				Intent clickintent2 = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"));
				clickintent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				context.startActivity(clickintent2);
			} else {
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
			String clickprefst = prefs.getString("onclickpref", "1");
			int clickpref = Integer.valueOf(clickprefst);
			switch (clickpref){
				case 1:
				// open in the reddinator view
				Intent clickintent1 = new Intent(context, ViewReddit.class);
				clickintent1.putExtras(intent.getExtras());
				clickintent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				context.startActivity(clickintent1);
				break;
				case 2:
				// open link in browser
				String url = intent.getStringExtra(ITEM_URL);
				Intent clickintent2 = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
				clickintent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				context.startActivity(clickintent2);
				break;
				case 3:
				// open reddit comments page in browser
				String plink = intent.getStringExtra(ITEM_PERMALINK);
				Intent clickintent3 = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.reddit.com"+plink));
				clickintent3.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				context.startActivity(clickintent3);
				break;
			}
			}
		}
		if (action.equals("android.appwidget.action.APPWIDGET_UPDATE_OPTIONS")) {
			//int id = intent.getExtras().getInt("id");
			/*AppWidgetManager mgr = AppWidgetManager.getInstance(context);
			mgr.notifyAppWidgetViewDataChanged(id, R.id.listview);*/
			/*Intent initintent = new Intent();
			initintent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
			initintent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
			context.sendBroadcast(initintent);*/
			System.out.println("execute firsttime startup?");
		} 
		
		/*else if (AppWidgetManager.ACTION_APPWIDGET_ENABLED.equals(action)) {
	        this.onEnabled(context);
	    } else if (AppWidgetManager.ACTION_APPWIDGET_DISABLED.equals(action)) {
	    	this.onDisabled(context);
	    } else if (AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(action)) {
	        this.onDeleted(context, new int[]{intent.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID)});
	    }*/
		System.out.println("broadcast received: "+intent.getAction().toString());
        super.onReceive(context, intent);
	}

}
