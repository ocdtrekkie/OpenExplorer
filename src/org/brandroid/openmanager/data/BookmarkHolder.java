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

package org.brandroid.openmanager.data;

import org.brandroid.openmanager.R;
import org.brandroid.openmanager.util.ThumbnailCreator;
import org.brandroid.openmanager.util.ThumbnailStruct;
import org.brandroid.openmanager.util.ThumbnailTask;
import org.brandroid.utils.Logger;
//import org.brandroid.utils.Logger;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class BookmarkHolder {
	private ImageView mIcon, mEject, mIndicate;
	private TextView mMainText, mInfo, mPath, mSizeText;
	private View mParentView;
	private String sTitle;
	private String sPath;
	private OpenPath mFile;
	private ThumbnailTask mTask;
	
	public BookmarkHolder(String path, View view) {
		this(new OpenFile(path), getTitleFromPath(path), view);
	}
	public BookmarkHolder(OpenPath path, String title, final View view)
	{
		mParentView = view;
		sPath = path.getPath();
		mFile = path;
		ensureViews();
		//mIndicate = (ImageView)view.findViewById(R.id.)
		setTitle(title);
	}
	
	private void ensureViews()
	{
		if(mIcon == null)
			mIcon = (ImageView)mParentView.findViewById(R.id.content_icon);
		if(mMainText == null)
			mMainText = (TextView)mParentView.findViewById(R.id.content_text);
		if(mIndicate == null)
			mIndicate = (ImageView)mParentView.findViewById(R.id.list_arrow);
		if(mEject == null)
			mEject = (ImageView)mParentView.findViewById(R.id.eject);
		if(mInfo == null)
			mInfo = (TextView)mParentView.findViewById(R.id.content_info);
		if(mPath == null)
			mPath = (TextView)mParentView.findViewById(R.id.content_fullpath);
		if(mSizeText == null)
			mSizeText = (TextView)mParentView.findViewById(R.id.size_text);
	}
	
	public void hideViews(View... views)
	{
		for(View v : views)
			if(v != null)
				v.setVisibility(View.GONE);
	}
	
	public ImageView getIconView() { ensureViews(); return mIcon; }
	public void setIconResource(int res) { ensureViews(); if(mIcon != null) mIcon.setImageResource(res); }
	public void setIconDrawable(Drawable d, ThumbnailStruct ts) {
		OpenPath file = ts.File;
		if(!file.getPath().equals(mFile.getPath())) {
			int w = ts.Width, h = ts.Height;
			Bitmap bmp = ThumbnailCreator.getThumbnailCache(mFile.getPath(), w, h);
			if(bmp != null)
			{
				BitmapDrawable bd = new BitmapDrawable(bmp);
				bd.setGravity(Gravity.CENTER);
				d = bd;
			} else {
				try {
					mParentView.notify();
				} catch(Exception e) { }
				Logger.LogWarning("Bad path " + mFile.getName() + " != " + file.getName());
				return;
			}
		}
		ensureViews();
		if(mIcon != null) mIcon.setImageDrawable(d);
	}
	public void setIconDrawable(Drawable d) { mIcon.setImageDrawable(d); }
	
	public void setEjectClickListener(View.OnClickListener listener)
	{
		if(mEject != null)
			mEject.setOnClickListener(listener);
	}
	public void setEjectable(Boolean eject) {
		if(mEject != null)
			mEject.setVisibility(eject ? View.VISIBLE : View.GONE);
	}
	public Boolean isEjectable() { return mFile.getPath().toLowerCase().indexOf("ext") > -1 || mFile.getPath().toLowerCase().startsWith("/removable/"); }
	
	public void setSelected(Boolean sel)
	{
		if(mIndicate != null)
			mIndicate.setVisibility(sel ? View.VISIBLE : View.GONE);
	}
	
	public String getPath() { return sPath; }
	public void setPath(String path)
	{
		sPath = path;
		if(mPath != null)
			mPath.setText(path.substring(0, path.lastIndexOf("/")));
	}
	public String getTitle() { return sTitle; }
	public void setTitle(String title) { setTitle(title, true); }
	public void setTitle(String title, boolean permanent) {
		if(mMainText != null)
			mMainText.setText(title);
		if(permanent)
			sTitle = title;
	}
	public void hideTitle() { if(mMainText != null) mMainText.setVisibility(View.GONE); }
	public void showTitle() { if(mMainText != null) mMainText.setVisibility(View.VISIBLE); }
	public View getView() { return mParentView; }
	public String getInfo() { return mInfo != null ? mInfo.getText().toString() : null; }
	public void setInfo(String info) { if(mInfo != null) mInfo.setText(info); }
	public void setSizeText(String txt) { if(mSizeText != null) mSizeText.setText(txt); }
	
	private static String getTitleFromPath(String path)
	{
		if(path != "/")
		{
			if(path.endsWith("/"))
				path = path.substring(0, path.length() - 1);
			path = path.substring(path.lastIndexOf("/") + 1);
		}
		return path;
	}
	public void showPath(Boolean visible) {
		if(mPath != null)
			mPath.setVisibility(visible ? View.VISIBLE : View.GONE);
	}
	public void setTask(ThumbnailTask task) {
		mTask = task;
	}
	public void cancelTask() { if(mTask!=null) mTask.cancel(true); }
}
