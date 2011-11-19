/*
    Open Explorer, an open source file explorer & text editor
    Copyright (C) 2011 Brandon Bowles <brandroid64@gmail.com>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.brandroid.openmanager;

import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentManager.BackStackEntry;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.FragmentManager.OnBackStackChangedListener;
import android.support.v4.content.CursorLoader;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewStub;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.RejectedExecutionException;

import org.brandroid.openmanager.data.OpenCursor;
import org.brandroid.openmanager.data.OpenFTP;
import org.brandroid.openmanager.data.OpenFile;
import org.brandroid.openmanager.data.OpenMediaStore;
import org.brandroid.openmanager.data.OpenPath;
import org.brandroid.openmanager.fragments.BookmarkFragment;
import org.brandroid.openmanager.fragments.CarouselFragment;
import org.brandroid.openmanager.fragments.DialogHandler;
import org.brandroid.openmanager.fragments.ContentFragment;
import org.brandroid.openmanager.fragments.PreferenceFragmentV11;
import org.brandroid.openmanager.fragments.TextEditorFragment;
import org.brandroid.openmanager.ftp.FTPManager;
import org.brandroid.openmanager.util.EventHandler;
import org.brandroid.openmanager.util.RootManager;
import org.brandroid.openmanager.util.FileManager;
import org.brandroid.openmanager.util.MultiSelectHandler;
import org.brandroid.openmanager.util.ThumbnailCreator;
import org.brandroid.utils.Logger;
import org.brandroid.utils.Preferences;

public class OpenExplorer
		extends FragmentActivity
		implements OnBackStackChangedListener, OnClickListener, OnLongClickListener
	{	

	private static final int PREF_CODE =		0x6;
	public static final int VIEW_LIST = 0;
	public static final int VIEW_GRID = 1;
	public static final int VIEW_CAROUSEL = Build.VERSION.SDK_INT > 11 ? 2 : 1;
	
	public static final boolean BEFORE_HONEYCOMB = Build.VERSION.SDK_INT < 11;
	public static final int REQUEST_CANCEL = 101;
	
	private static OnSettingsChangeListener mSettingsListener = null;
	private Preferences mPreferences = null;
	private SearchView mSearchView;
	private ActionMode mActionMode;
	private ArrayList<OpenPath> mHeldFiles;
	private int mLastBackIndex = -1;
	private OpenPath mLastPath = null;
	private BroadcastReceiver storageReceiver = null;
	private int mViewMode = VIEW_LIST;
	
	private Fragment mFavoritesFragment;
	
	private EventHandler mEvHandler;
	private FileManager mFileManager;
	
	private FragmentManager fragmentManager;
	
	private OpenCursor mPhotoParent, mVideoParent, mMusicParent, mApkParent;
	
	private Boolean mSinglePane = false;
	
	public int getViewMode() { return mViewMode; }
	public void setViewMode(int mode) { mViewMode = mode; }
    
    public void onCreate(Bundle savedInstanceState) {
    	
    	if(BEFORE_HONEYCOMB)
    		requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        setContentView(R.layout.main_fragments);
        
        ThumbnailCreator.setContext(this);

        if(isGTV() && !getPreferences().getBoolean("global", "welcome", false))
		{
			showToast("Welcome, GoogleTV user!");
			getPreferences().setSetting("global", "welcome", true);
			//getActionBar().hide();
		} // else
        
        if(getPreferences().getSetting("global", "root", false) ||
        		Preferences.getPreferences(getApplicationContext(), "global").getBoolean("root", false))
        	RootManager.Default.requestRoot();
        
        if(!BEFORE_HONEYCOMB)
        {
        	setTheme(android.R.style.Theme_Holo);
            if(findViewById(R.id.title_bar) != null)
            	findViewById(R.id.title_bar).setVisibility(View.GONE);
        } else {
        	ViewStub mTitleStub = (ViewStub)findViewById(R.id.title_stub);
        	if(mTitleStub != null)
        		mTitleStub.inflate();
        	setOnClicks(R.id.title_icon, R.id.title_search);
        }
        
        if(findViewById(R.id.list_frag) == null)
        	mSinglePane = true;
        else if(findViewById(R.id.list_frag).getVisibility() == View.GONE)
        	mSinglePane = true;
        
        if(fragmentManager == null)
        {
	        fragmentManager = getSupportFragmentManager();
	        fragmentManager.addOnBackStackChangedListener(this);
        }
        
        //mTreeFragment = new DirListFragment();
        if(mFavoritesFragment == null)
        	mFavoritesFragment = new BookmarkFragment();
        
        //BookmarkFragment.setOnChangeLocationListener(this);

		refreshCursors();
        
        OpenPath path = null;
        if(savedInstanceState != null && savedInstanceState.containsKey("last") && !savedInstanceState.getString("last").equals(""))
        {
        	String last = savedInstanceState.getString("last");
        	if(last.startsWith("/"))
        		path = new OpenFile(last);
        	else if(last.indexOf(":/") > -1)
        		path = new OpenFTP(last, null, new FTPManager());
        	else if(last.equals("Videos"))
        		path = mVideoParent;
        	else if(last.equals("Photos"))
        		path = mPhotoParent;
        	else if(last.equals("Music"))
        		path = mMusicParent;
        	else
        		path = new OpenFile(last);
        	updateTitle(path.getPath());
        } else
        	path = new OpenFile(Environment.getExternalStorageDirectory());
        mLastPath = path;
        
        super.onCreate(savedInstanceState);
        
        boolean bAddToStack = fragmentManager.getBackStackEntryCount() > 0;

        FragmentTransaction ft = fragmentManager.beginTransaction();
        Fragment home = new ContentFragment(mLastPath);
        
        Logger.LogDebug("Creating with " + path.getPath());

        if(!mSinglePane)
        {
        	fragmentManager.beginTransaction()
        		.replace(R.id.list_frag, mFavoritesFragment)
        		.commit();
        	//ft.replace(R.id.list_frag, mFavoritesFragment);
        }

        if(Build.VERSION.SDK_INT > 11 && savedInstanceState == null)
        {
        	if(mVideoParent.length() > 1)
        	{
        		mViewMode = VIEW_CAROUSEL;
        		path = mLastPath = mVideoParent;
        	} else if (mPhotoParent.length() > 1) {
        		mViewMode = VIEW_CAROUSEL;
        		path = mLastPath = mPhotoParent;
        	} else mViewMode = VIEW_LIST;
        	
        	if(mViewMode == VIEW_CAROUSEL)
        		home = new CarouselFragment(mLastPath);
        } else if(savedInstanceState != null)
        	if(savedInstanceState.containsKey("edit_path"))
        	{
        		Logger.LogDebug("textEditor restore @ " + savedInstanceState.getString("edit_path"));
        		home = new TextEditorFragment(new OpenFile(savedInstanceState.getString("edit_path")));
        		bAddToStack = false;
        	}

        if(bAddToStack)
        {
        	if(fragmentManager.getBackStackEntryCount() == 0 || !mLastPath.getPath().equals(fragmentManager.getBackStackEntryAt(fragmentManager.getBackStackEntryCount() - 1).getBreadCrumbTitle()))
	        	fragmentManager.beginTransaction()
	        		.addToBackStack("path")
	        		.setBreadCrumbTitle(mLastPath.getPath())
	        		.commit();
        }
        ft.replace(R.id.content_frag, home);
        ft.commit();
        
        updateTitle(mLastPath.getPath());
        
        if(mFileManager == null)
        	mFileManager = new FileManager();
        if(mEvHandler == null)
        	mEvHandler = new EventHandler(mFileManager);
        
        //mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        handleMediaReceiver();
        
        /* read and display the users preferences */
        //mSettingsListener.onSortingChanged(mPreferences.getString(SettingsActivity.PREF_SORT_KEY, "type"));

    }
    
    public Preferences getPreferences() {
    	if(mPreferences == null)
    		mPreferences = new Preferences(getApplicationContext());
    	return mPreferences;
    }
    
    public void handleRefreshMedia(final String path, boolean keepChecking, final int retries)
    {
    	if(!keepChecking || retries <= 0)
    	{
    		refreshBookmarks();
    		return;
    	}
    	final Handler handler = new Handler();
		handler.postDelayed(new Runnable() {
			public void run() {
				Logger.LogDebug("Check " + retries + " for " + path);
				try {
					StatFs sf = new StatFs(path);
					if(sf.getBlockCount() == 0)
						throw new Exception("No blocks");
					//showToast("New USB! " + getVolumeName(path) + " @ " + DialogHandler.formatSize((long)sf.getBlockSize() * (long)sf.getAvailableBlocks()));
					refreshBookmarks();
				} catch(Throwable e)
				{
					Logger.LogWarning("Couldn't read " + path);
					handleRefreshMedia(path, true, retries - 1); // retry again in 1/2 second
				}
			}
		}, 1000);
    }
    
    public void handleMediaReceiver()
    {
    	storageReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				String data = intent.getDataString();
				final String path = data.replace("file://", "");
				//Logger.LogInfo("Received " + intent.toString());
				//Bundle extras = intent.getExtras();
				//showToast(data.replace("file://", "").replace("/mnt/", "") + " " +
				//		action.replace("android.intent.action.", "").replace("MEDIA_", ""));
				if(action.equals(Intent.ACTION_MEDIA_MOUNTED))
					handleRefreshMedia(path, true, 10);
				else
					refreshBookmarks();
			}
		};
		
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
		filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
		filter.addDataScheme("file");
		registerReceiver(storageReceiver, filter);
    }
    
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
    	super.onPostCreate(savedInstanceState);
    	ensureCursorCache();
    	/*
    	if(mSettingsListener != null)
        {
	        mSettingsListener.onHiddenFilesChanged(mPreferences.getBoolean(SettingsActivity.PREF_HIDDEN_KEY, false));
			mSettingsListener.onThumbnailChanged(mPreferences.getBoolean(SettingsActivity.PREF_THUMB_KEY, true));
			mSettingsListener.onViewChanged(mPreferences.getString(SettingsActivity.PREF_VIEW_KEY, "list"));
        } */
    }
    
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	if(storageReceiver != null)
    		unregisterReceiver(storageReceiver);
    }
    
    public OpenCursor getPhotoParent() { if(mPhotoParent == null) refreshCursors(); return mPhotoParent; }
    public OpenCursor getVideoParent() { if(mVideoParent == null) refreshCursors(); return mVideoParent; }
    public OpenCursor getMusicParent() { if(mMusicParent == null) refreshCursors(); return mMusicParent; }
    
    private void refreshCursors()
    {
    	if(mPhotoParent == null)
    	{
    		try {
    			CursorLoader loader = new CursorLoader(getApplicationContext(),
						MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
						new String[]{"_id", "_display_name", "_data", "_size", "date_modified"},
						MediaStore.Images.Media.SIZE + " > 10000", null,
						MediaStore.Images.Media.DATE_ADDED + " DESC");
    			Cursor c = loader.loadInBackground();
    			if(c != null)
    				mPhotoParent = new OpenCursor(c, "Photos");
    		} catch(IllegalStateException e) { Logger.LogError("Couldn't query photos.", e); }
		}
		if(mVideoParent == null)
    	{
			try {
				CursorLoader loader = new CursorLoader(getApplicationContext(),
						MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
						new String[]{"_id", "_display_name", "_data", "_size", "date_modified"},
						MediaStore.Video.Media.SIZE + " > 100000", null,
						MediaStore.Video.Media.BUCKET_DISPLAY_NAME + " ASC, " +
						MediaStore.Video.Media.DATE_MODIFIED + " DESC");
				Cursor c = loader.loadInBackground();
    			if(c != null)
    				mVideoParent = new OpenCursor(c, "Videos");
    		} catch(IllegalStateException e) { Logger.LogError("Couldn't query videos.", e); }
    	}
		if(mMusicParent == null)
		{
			try {
				CursorLoader loader = new CursorLoader(getApplicationContext(),
						MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
						new String[]{"_id", "_display_name", "_data", "_size", "date_modified"},
						MediaStore.Audio.Media.SIZE + " > 10000", null,
						MediaStore.Audio.Media.DATE_ADDED + " DESC");
				Cursor c = loader.loadInBackground();
    			if(c != null)
    				mMusicParent = new OpenCursor(c, "Music");
    		} catch(IllegalStateException e) { Logger.LogError("Couldn't query music.", e); }
		}
		if(mApkParent == null && Build.VERSION.SDK_INT > 10)
		{
			try {
				CursorLoader loader = new CursorLoader(getApplicationContext(),
						MediaStore.Files.getContentUri(Environment.getExternalStorageDirectory().getPath()),
						new String[]{"_id", "_display_name", "_data", "_size", "date_modified"},
						"_size > 10000", null,
						"date modified DESC");
				Cursor c = loader.loadInBackground();
				if(c != null)
					mApkParent = new OpenCursor(c, "Apps");
			} catch(IllegalStateException e) { Logger.LogError("Couldn't get Apks.", e); }
		}
		//Cursor mAudioCursor = managedQuery(MediaStore.Audio, projection, selection, selectionArgs, sortOrder)
		ensureCursorCache();
    }
    public void ensureCursorCache()
    {
    	// group into blocks
    	int enSize = 20;
    	ArrayList<OpenPath> buffer = new ArrayList<OpenPath>(enSize);
    	for(OpenCursor curs : new OpenCursor[]{mPhotoParent, mVideoParent, mApkParent})
    	{
    		if(curs == null) continue;
	    	for(OpenMediaStore ms : curs.list())
	    	{
	    		buffer.add(ms);
	    		if(buffer.size() == enSize)
	    		{
	    			OpenMediaStore[] buff = new OpenMediaStore[buffer.size()];
	    			buffer.toArray(buff);
	    			buffer.clear();
	    			try {
	    				new EnsureCursorCacheTask().execute(buff);
	    			} catch(RejectedExecutionException e) {
	    				Logger.LogWarning("Couldn't ensure cache.", e);
	    				return;
	    			}
	    		}
	    	}
    	}
    	if(buffer.size() > 0)
    	{
    		OpenMediaStore[] buff = new OpenMediaStore[buffer.size()];
			buffer.toArray(buff);
			buffer.clear();
			try {
				new EnsureCursorCacheTask().execute(buff);
			} catch(RejectedExecutionException e) {
				Logger.LogWarning("Couldn't ensure cache.", e);
				return;
			}
    	}
    }
    public void toggleView()
    {
    	int newMode = (mViewMode + 1) % 3;
    	if(BEFORE_HONEYCOMB && newMode == VIEW_CAROUSEL)
    		newMode = 0;
    	changeViewMode(newMode);
    }
    public void toggleBookmarks()
    {
    	if(mFavoritesFragment == null)
    		mFavoritesFragment = new BookmarkFragment();
    		
    	FragmentTransaction ft = fragmentManager.beginTransaction();
    	if(mFavoritesFragment.isVisible())
    		ft.replace(R.id.content_frag, new ContentFragment(mLastPath));
    	else
    		ft.replace(R.id.content_frag, mFavoritesFragment);

    	ft.addToBackStack("favs");
		ft.commit();
    }
    public void refreshBookmarks()
    {
    	refreshCursors();
    	if(mFavoritesFragment == null)
    	{
    		mFavoritesFragment = new BookmarkFragment();
    		FragmentTransaction ft = fragmentManager.beginTransaction();
    		ft.replace(R.id.list_frag, mFavoritesFragment);
    		ft.commit();
    	} else {
    		((BookmarkFragment)mFavoritesFragment).scanBookmarks();
    	}
    }
    public ContentFragment getDirContentFragment(Boolean activate)
    {
    	Logger.LogDebug("getDirContentFragment");
    	Fragment ret = fragmentManager.findFragmentById(R.id.content_frag);
    	if(ret == null || !ret.getClass().equals(ContentFragment.class))
   			ret = new ContentFragment(mLastPath);
    	if(activate)
    	{
    		Logger.LogDebug("Activating content fragment");
    		fragmentManager.beginTransaction()
    			.replace(R.id.content_frag, ret)
    			.commit();
    	}
    	
   		return (ContentFragment)ret;
    }
    
    public void updateTitle(String s)
    {
    	String t = getResources().getString(R.string.app_name) + (s.equals("") ? "" : " - " + s);
    	if(BEFORE_HONEYCOMB)
    	{
    		if(findViewById(R.id.title_path) != null)
    			((TextView)findViewById(R.id.title_path)).setText(s);
    		else
    			setTitle(t);
    	} else {
    		getActionBar().setTitle(t);
    	}
    }
    
    public void editFile(OpenPath path)
    {
    	TextEditorFragment editor = new TextEditorFragment(path);
    	fragmentManager.beginTransaction()
    		.replace(R.id.content_frag, editor)
    		.addToBackStack(null)
    		.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
    		.commit();
    	//addTab(editor, path.getName(), true);
    }
    
    public boolean onCreateOptionsMenu(Menu menu) {
    	getMenuInflater().inflate(R.menu.main_menu, menu);
    	if(!BEFORE_HONEYCOMB)
    	{
    		mSearchView = (SearchView)menu.findItem(R.id.menu_search).getActionView();
	        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
	        	public boolean onQueryTextSubmit(String query) {
					mSearchView.clearFocus();
					mEvHandler.searchFile(mLastPath, query, getApplicationContext());
					return true;
				}
				public boolean onQueryTextChange(String newText) {
					return false;
				}
			});
    	}
    	return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	if(BEFORE_HONEYCOMB)
    		menu.findItem(R.id.menu_view_carousel).setVisible(false);
    	if(!mSinglePane)
    		menu.findItem(R.id.menu_favorites).setVisible(false);
    	if(mViewMode == VIEW_GRID)
    		menu.findItem(R.id.menu_view_grid).setChecked(true);
    	else if(mViewMode == VIEW_LIST)
    		menu.findItem(R.id.menu_view_list).setChecked(true);
    	else if(mViewMode == VIEW_CAROUSEL)
    		menu.findItem(R.id.menu_view_carousel).setChecked(true);
    	if(RootManager.Default.isRoot())
    		menu.findItem(R.id.menu_root).setChecked(true);
    	return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	switch(keyCode)
    	{
	    	case KeyEvent.KEYCODE_DPAD_RIGHT:
	    		//fragmentManager.findFragmentById(R.id.content_frag).getView().requestFocus();
	    		break;
	    	case KeyEvent.KEYCODE_DPAD_LEFT:
	        	//fragmentManager.findFragmentById(R.id.list_frag).getView().requestFocus();
	        	break;
    	}
    	return super.onKeyDown(keyCode, event);
    }
    
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	if(item.isCheckable())
    		item.setChecked(item.getGroupId() > 0 ? true : !item.isChecked());
    	
    	return onClick(item.getItemId(), item);
    }
    public boolean onClick(int id, MenuItem item)
    {
    	switch(id)
    	{
    		case R.id.title_icon:
	    	case android.R.id.home:
	    		if (mHeldFiles != null) {
	    			//DialogFragment df = 
	    			DialogHandler dialog = DialogHandler.newDialog(DialogHandler.DialogType.HOLDINGFILE_DIALOG, this);
	    			dialog.setHoldingFileList(mHeldFiles);
	    			
	    			FragmentTransaction trans = fragmentManager.beginTransaction();
	    			trans.replace(R.id.content_frag, dialog, "dialog");
	    			//dialog.show(getFragmentManager(), "dialog");
	    			trans.addToBackStack("dialog");
	    			trans.commit();
	    		} else {
	    			if(mSinglePane)
	    				toggleBookmarks();
	    			else
	    				toggleView();
	    		}
	    		return true;
	    	
	    	case R.id.menu_new_folder:
	    		mEvHandler.createNewFolder(mFileManager.peekStack().getPath(), this);
	    		return true;
	    		
	    	case R.id.menu_multi:
	    		if(mActionMode != null)
	    			return false;
	    		
	    		if(BEFORE_HONEYCOMB)
	    		{
	    			getDirContentFragment(true).changeMultiSelectState(true, MultiSelectHandler.getInstance(OpenExplorer.this));
	    		} else {
	    			
		    		mActionMode = startActionMode(new ActionMode.Callback() {
		    			
		    			MultiSelectHandler handler;
		    			
		    			
		    			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		    				return false;
		    			}
		    			public void onDestroyActionMode(ActionMode mode) {
		    				getDirContentFragment(false).changeMultiSelectState(false, handler);
		    				mActionMode = null;
		    				handler = null;
		    			}
		    			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		    				handler = MultiSelectHandler.getInstance(OpenExplorer.this);
		    				mode.setTitle("Multi-select Options");
		    				mode.getMenuInflater().inflate(R.menu.context_multi, menu);
		    				getDirContentFragment(true).changeMultiSelectState(true, handler);
		    				return true;
		    			}
		    			public boolean onActionItemClicked(ActionMode mode, MenuItem item)
		    			{
		    				ArrayList<String>files = handler.getSelectedFiles();
		    				
		    				//nothing was selected
		    				if(files.size() < 1) {
		    					mode.finish();
		    					return true;
		    				}
		    				
		    				if(mHeldFiles == null)
		    					mHeldFiles = new ArrayList<OpenPath>();
		    				
		    				mHeldFiles.clear();
		    				
		    				for(String s : files)
		    					mHeldFiles.add(FileManager.getOpenCache(s));
		    			
		    				return getDirContentFragment(false).executeMenu(item.getItemId(), mode, mHeldFiles);
		    			}
		    		});
	    		}
	    		return true;
	    		
	    	case R.id.menu_sort_name_asc:	if(mSettingsListener!=null) mSettingsListener.onSortingChanged(FileManager.SortType.ALPHA); return true; 
	    	case R.id.menu_sort_name_desc:	if(mSettingsListener!=null) mSettingsListener.onSortingChanged(FileManager.SortType.ALPHA_DESC); return true; 
	    	case R.id.menu_sort_date_asc: 	if(mSettingsListener!=null) mSettingsListener.onSortingChanged(FileManager.SortType.DATE); return true;
	    	case R.id.menu_sort_date_desc: 	if(mSettingsListener!=null) mSettingsListener.onSortingChanged(FileManager.SortType.DATE_DESC); return true; 
	    	case R.id.menu_sort_size_asc: 	if(mSettingsListener!=null) mSettingsListener.onSortingChanged(FileManager.SortType.SIZE); return true; 
	    	case R.id.menu_sort_size_desc: 	if(mSettingsListener!=null) mSettingsListener.onSortingChanged(FileManager.SortType.SIZE_DESC); return true; 
	    	case R.id.menu_sort_type: 		if(mSettingsListener!=null) mSettingsListener.onSortingChanged(FileManager.SortType.TYPE); return true;
	    	
	    	case R.id.menu_view_grid:
	    		changeViewMode(VIEW_GRID);
	    		return true;
	    	case R.id.menu_view_list:
	    		changeViewMode(VIEW_LIST);
	    		return true;
	    	case R.id.menu_view_carousel:
	    		changeViewMode(VIEW_CAROUSEL);
	    		return true;
	    	case R.id.menu_view_hidden: if(mSettingsListener!=null) mSettingsListener.onHiddenFilesChanged(item.isChecked()); return true;
	    	case R.id.menu_view_thumbs: if(mSettingsListener!=null) mSettingsListener.onThumbnailChanged(item.isChecked()); return true;
	    	    	
	    	case R.id.menu_root:
	    		if(!item.isCheckable() || item.isChecked())
	    		{
	    			if(RootManager.Default.isRoot() || RootManager.Default.requestRoot())
	    			{
	    				getPreferences().setSetting("global", "root", true);
	    				showToast(getString(R.string.s_menu_root) + "!");
	    				item.setTitle(getString(R.string.s_menu_root) + "!");
	    			} else {
	    				item.setChecked(false);
	    				showToast("Unable to achieve root.");
	    			}
	    		}
	    		return true;
	    	case R.id.menu_flush:
	    		ThumbnailCreator.flushCache();
    			Intent intent = new Intent(this, OpenExplorer.class); 
    			startActivity(intent);
    			finish();
	    		return true;
	    		
	    	case R.id.menu_settings:
	    		showPreferences(null);
	    		return true;
	    	
	    	case R.id.menu_settings_folder:
	    		showPreferences(mLastPath);
	    		return true;
	    		
	    	case R.id.title_search:
	    	case R.id.menu_search:
	    		return onSearchRequested();

	    	case R.id.menu_favorites:
	    		toggleBookmarks();
	    		return true;
    	}
    	
    	return super.onOptionsItemSelected(item);
    }
    
    @Override
    public boolean onSearchRequested() {
    	mEvHandler.startSearch(mLastPath, this);
		//showToast("Sorry, not working yet.");
    	return super.onSearchRequested();
    }
    
    public void changeViewMode(int newView) {
    	if(mViewMode == newView) {
    		Logger.LogWarning("changeViewMode called unnecessarily! " + newView + " = " + mViewMode);
    		return;
    	}
    	if(newView == VIEW_CAROUSEL && BEFORE_HONEYCOMB)
    		newView = mViewMode == VIEW_LIST ? VIEW_GRID : VIEW_LIST;
    	int oldView = mViewMode;
		setViewMode(newView);
		if(BEFORE_HONEYCOMB)
		{
			if(mSettingsListener != null)
				mSettingsListener.onViewChanged(newView);
			else Logger.LogWarning("Listener not available.");
		} else if(newView == VIEW_CAROUSEL)
		{
			fragmentManager.beginTransaction()
				.replace(R.id.content_frag, new CarouselFragment(mLastPath))
				.commit();
			if(mSettingsListener!=null) mSettingsListener.onViewChanged(VIEW_GRID);
		} else if (oldView == VIEW_CAROUSEL) { // if we need to transition from carousel
			fragmentManager.beginTransaction()
				.replace(R.id.content_frag, new ContentFragment(mLastPath))
				//.addToBackStack(null)
				.commit();
		} else if (mSettingsListener != null)
			mSettingsListener.onViewChanged(newView);
	}
	public void showPreferences(OpenPath path)
    {
    	if(Build.VERSION.SDK_INT > 100)
    	{
    		FragmentTransaction ft = fragmentManager.beginTransaction();
    		ft.hide(fragmentManager.findFragmentById(R.id.content_frag));
	    	//ft.replace(R.id.content_frag, new PreferenceFragment(this, path));
	    	ft.setBreadCrumbTitle("prefs://" + (path != null ? path.getPath() : ""));
			ft.addToBackStack("prefs");
			ft.commit();
			final PreferenceFragmentV11 pf2 = new PreferenceFragmentV11(path);
			getFragmentManager().addOnBackStackChangedListener(new android.app.FragmentManager.OnBackStackChangedListener() {
				
				public void onBackStackChanged() {
					//android.app.FragmentTransaction ft3 = getFragmentManager().beginTransaction();
					Logger.LogDebug("hide me!");
					//getFragmentManager().removeOnBackStackChangedListener(this);
					if(pf2 != null && pf2.getView() != null && getFragmentManager().getBackStackEntryCount() == 0)
						pf2.getView().setVisibility(View.GONE);
				}
			});
			android.app.FragmentTransaction ft2 = getFragmentManager().beginTransaction();
			ft2.replace(R.id.content_frag, pf2);
			ft2.addToBackStack("prefs");
			ft2.commit();
    	} else {
    		Intent intent = new Intent(this, SettingsActivity.class);
    		if(path != null)
    			intent.putExtra("path", path.getPath());
    		startActivityForResult(intent, PREF_CODE);
    	}
    }
    
    public boolean onKeyUp(int keyCode, KeyEvent event) {
    	/*
    	if (keyCode == KeyEvent.KEYCODE_BACK) {
    		if (mBackQuit) {
    			return super.onKeyUp(keyCode, event);
    		} else {
    			Toast.makeText(this, "Press back again to quit", Toast.LENGTH_SHORT).show();
    			mBackQuit = true;
    			return true;
    		}    	
    	}*/
    	return super.onKeyUp(keyCode, event);
    }
    
	
	
	public static void setOnSettingsChangeListener(OnSettingsChangeListener e) {
    	mSettingsListener = e;
    }
    
    
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if(requestCode == PREF_CODE) {
    		//this could be done better.
    		try {
	    		mSettingsListener.onHiddenFilesChanged(mPreferences.getBoolean(mLastPath.getPath(), SettingsActivity.PREF_HIDDEN_KEY, false));
	    		mSettingsListener.onThumbnailChanged(mPreferences.getBoolean(mLastPath.getPath(), SettingsActivity.PREF_THUMB_KEY, false));
	    		mSettingsListener.onViewChanged(mPreferences.getInt(mLastPath.getPath(), SettingsActivity.PREF_VIEW_KEY, VIEW_LIST));
    		} catch(Exception e) {
    			Logger.LogError("onActivityResult FAIL", e);
    		}
    		//mSettingsListener.onSortingChanged(mPreferences.getString(SettingsActivity.PREF_SORT_KEY, "alpha"));
    	}
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
    	super.onSaveInstanceState(outState);
    	if(mLastPath == null || mLastPath.equals("")) return;
    	Logger.LogDebug("Saving instance last path = " + mLastPath.getPath());
    	outState.putString("last", mLastPath.getPath());
    }
    
    public void pushFragment(Object o)
    {
    	if(Build.VERSION.SDK_INT > 10 && o.getClass().equals(android.app.Fragment.class))
    		getFragmentManager().beginTransaction()
    			.replace(R.id.content_frag, ((android.app.Fragment)o))
    			.addToBackStack(null)
    			.commit();
    	else
    		getSupportFragmentManager().beginTransaction()
    			.replace(R.id.content_frag, (Fragment)o)
    			.addToBackStack(null)
    			.commit();
    }


	public void goBack()
	{
		//new FileIOTask().execute(new FileIOCommand(FileIOCommandType.PREV, mFileManager.getLastPath()));
		OpenPath last = mFileManager.popStack();
		if(last == null) return;
		Logger.LogDebug("Going back to " + last.getPath());
		changePath(last, false);
		//updateData(last.list());
	}
	
	public void onBackStackChanged() {
		int i = fragmentManager.getBackStackEntryCount();
		final Boolean isBack = i < mLastBackIndex;
		BackStackEntry entry = null;
		if(i > 0) entry = fragmentManager.getBackStackEntryAt(i - 1);
		Logger.LogDebug("Back Stack " + i + (entry != null ? ": " + entry.getBreadCrumbTitle() : ""));
		if(isBack)
		{
			if(i > 0)
			{
				if(entry != null && entry.getBreadCrumbTitle() != null)
					updateTitle(entry.getBreadCrumbTitle().toString());
				else
					updateTitle("");
			} else if (mSinglePane) updateTitle("");
			else {
				updateTitle("");
				//showToast("Press back again to exit.");
			}
		}
		if(isBack && mFileManager != null && mFileManager.getStack() != null && mFileManager.getStack().size() > 0)
		{
			goBack();
		} else if(mSinglePane && isBack)
		{
			if(findViewById(R.id.list_frag) != null && findViewById(R.id.list_frag).getVisibility() == View.GONE)
			{
				findViewById(R.id.list_frag).setVisibility(View.VISIBLE);
			} else {
				FragmentTransaction ft = fragmentManager.beginTransaction();
				ft.show(mFavoritesFragment);
				ft.commit();
			}
		}
		if(Build.VERSION.SDK_INT > 10)
		{
			android.app.Fragment frag = getFragmentManager().findFragmentById(R.id.content_frag);
			if(frag != null)
				getFragmentManager().beginTransaction().hide(frag).commit();
		}
		mLastBackIndex = i;
	}
	
	public boolean isGTV() { return getPackageManager().hasSystemFeature("com.google.android.tv"); }
	public void showToast(final String message)  {
		Logger.LogInfo("Made toast: " + message);
        showToast(message, Toast.LENGTH_SHORT);
    }
	public void showToast(final int iStringResource) { showToast(getResources().getString(iStringResource)); }
	public void showToast(final String message, final int toastLength)  {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getBaseContext(), message, toastLength).show();
            }
        });
    }


	public void hideBookmarkTitles() {
		BookmarkFragment bf = ((BookmarkFragment)fragmentManager.findFragmentById(R.id.list_frag));
		bf.hideTitles();
	}

	public void showBookmarkTitles() {
		BookmarkFragment bf = ((BookmarkFragment)fragmentManager.findFragmentById(R.id.list_frag));
		bf.showTitles();
	}
	
	public void toggleBookmarks(Boolean visible)
	{
		if(!mSinglePane) return;
		Fragment frag = fragmentManager.findFragmentById(R.id.list_frag);
		View v = frag.getView();
		v.setVisibility(visible ? View.VISIBLE : View.GONE);
	}
	
	public void killBadFragments()
	{
		
	}

	public void changePath(OpenPath path, Boolean addToStack)
	{
		if(path == null) path = mLastPath;
		if(!addToStack && path.getPath().equals("/")) return;
		//if(mLastPath.equalsIgnoreCase(path.getPath())) return;
		Fragment content;
		if(mViewMode == VIEW_CAROUSEL && !BEFORE_HONEYCOMB && path.getClass().equals(OpenCursor.class))
			content = new CarouselFragment(path);
		else {
			if(mViewMode == VIEW_CAROUSEL)
				mViewMode = VIEW_LIST;
			content = new ContentFragment(path);
		}
		FragmentTransaction ft = fragmentManager.beginTransaction();
		ft.replace(R.id.content_frag, content);
		ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
		if(addToStack)
		{
			int bsCount = fragmentManager.getBackStackEntryCount();
			if(bsCount > 0)
			{
				BackStackEntry entry = fragmentManager.getBackStackEntryAt(bsCount - 1);
				String last = entry.getBreadCrumbTitle() != null ? entry.getBreadCrumbTitle().toString() : "";
				Logger.LogDebug("Changing " + last + " to " + path.getPath() + "? " + (last.equalsIgnoreCase(path.getPath()) ? "No" : "Yes"));
				if(last.equalsIgnoreCase(path.getPath()))
					return;
			}
			mFileManager.pushStack(path);
			ft.addToBackStack(null);
		} else Logger.LogDebug("Covertly changing to " + path.getPath());
		Logger.LogDebug("Setting path to " + path.getPath());
		ft.setBreadCrumbTitle(path.getPath());
		updateTitle(path.getPath());
		ft.commit();
		if(!BEFORE_HONEYCOMB)
			setLights(!path.getName().equals("Videos"));
		mLastPath = path;
	}
	public void setLights(Boolean on)
	{
		try {
			View root = getCurrentFocus().getRootView();
			int vis = on ? View.STATUS_BAR_VISIBLE : View.STATUS_BAR_HIDDEN;
			if(root.getSystemUiVisibility() != vis)
				root.setSystemUiVisibility(vis);
		} catch(Exception e) { }
	}
	public void onChangeLocation(OpenPath path) {
		changePath(path, true);
	}

	public FileManager getFileManager() {
		return mFileManager;
	}

	public EventHandler getEventHandler() {
		return mEvHandler;
	}

	public void setFileManager(FileManager man) {
		mFileManager = man;
	}
	public void setEventHandler(EventHandler handler)
	{
		mEvHandler = handler;
	}
	
	public class EnsureCursorCacheTask extends AsyncTask<OpenPath, Void, Void>
	{
		@Override
		protected Void doInBackground(OpenPath... params) {
			int done = 0;
			for(OpenPath path : params)
			{
				if(path.isDirectory())
				{
					for(OpenPath kid : path.list())
					{
						ThumbnailCreator.generateThumb(kid, 36, 36);
						ThumbnailCreator.generateThumb(kid, 96, 96);
						done++;
					}
				} else {
					if(!ThumbnailCreator.hasContext())
						ThumbnailCreator.setContext(getApplicationContext());
					ThumbnailCreator.generateThumb(path, 36, 36);
					ThumbnailCreator.generateThumb(path, 96, 96);
					done++;
				}
			}
			//Logger.LogDebug("cursor cache of " + done + " generated.");
			return null;
		}
		
	}


	public void showFileInfo(OpenPath path) {
		DialogHandler dialogInfo = DialogHandler.newDialog(DialogHandler.DialogType.FILEINFO_DIALOG, this);
		dialogInfo.setFilePath(path.getPath());
		dialogInfo.show(fragmentManager, "info");
	}

	public interface OnSettingsChangeListener {
		public void onHiddenFilesChanged(boolean state);
		public void onThumbnailChanged(boolean state);
		public void onViewChanged(int viewGrid);
		public void onSortingChanged(FileManager.SortType type);
	}


    public void setOnClicks(int... ids)
    {
    	for(int id : ids)
    		if(findViewById(id) != null)
    		{
    			View v = findViewById(id);
    			if(v.isLongClickable())
    				v.setOnLongClickListener(this);
    			v.setOnClickListener(this);
    		}
    }
    
	public void onClick(View v) {
		onClick(v.getId(), null);
	}
	public static String getVolumeName(String sPath2) {
		Process mLog = null;
		BufferedReader reader = null;
		try {
			mLog = Runtime.getRuntime().exec(new String[] {"logcat", "-d", "MediaVolume:D *:S"});
			reader = new BufferedReader(new InputStreamReader(mLog.getInputStream()));
			String check = sPath2.substring(sPath2.lastIndexOf("/") + 1);
			if(check.indexOf(".") > -1)
				check = check.substring(check.indexOf(".") + 1);
			String s = null;
			String last = null;
			do {
				s = reader.readLine();
				if(s == null) break;
				if(s.indexOf("New volume - Label:[") > -1 || s.indexOf(check) > -1)
					last = s.substring(s.indexOf("[") + 1, s.indexOf("]"));
			} while(s != null);
			if(last == null) throw new IOException("Couldn't find volume label.");
			sPath2 = last;
		} catch (IOException e) {
			Logger.LogError("Couldn't read LogCat :(", e);
			sPath2 = sPath2.substring(sPath2.lastIndexOf("/") + 1);
			if(sPath2.indexOf("_") > -1 && sPath2.indexOf("usb") < sPath2.indexOf("_"))
				sPath2 = sPath2.substring(0, sPath2.indexOf("_"));
			else if (sPath2.indexOf("_") > -1 && sPath2.indexOf("USB") > sPath2.indexOf("_"))
				sPath2 = sPath2.substring(sPath2.indexOf("_") + 1);
			sPath2 = sPath2.toUpperCase();
		} finally {
			if(reader != null)
				try {
					reader.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
		return sPath2;
	}
	public boolean onLongClick(View v) {
		switch(v.getId())
		{
			case R.id.title_icon:
				toggleView();
				return true;
		}
		return false;
	}
}

