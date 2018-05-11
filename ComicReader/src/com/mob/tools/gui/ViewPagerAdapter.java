package com.mob.tools.gui;

import android.view.View;
import android.view.ViewGroup;

public abstract class ViewPagerAdapter {
	private MobViewPager parent;
	
	final void setMobViewPager(MobViewPager mvp) {
		parent = mvp;
	}

	public abstract int getCount();

	public void onScreenChanging(float position) {
		
	}
	
	public void onScreenChange(int currentScreen, int lastScreen) {
		
	}

	public abstract View getView(int index, View convertView, ViewGroup parent);
	
	public void invalidate() {
		if (parent != null) {
			parent.setAdapter(this);
		}
	}
	
}
