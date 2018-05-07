package m.comicreader.reader;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;

import m.comicreader.R;

public class ReaderActivity extends Activity {
	private MPSFile mps;
	private ArrayList<SimpleEntry<String, String[]>> contents;
	private ViewPager vpPages;
	private TabView[] tabs;
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final String[] mpsPaths = getIntent().getStringArrayExtra("mps");
		final String name = mpsPaths[0];
		open(mpsPaths, new Runnable() {
			public void run() {
				SharedPreferences sp = getPreferences(MODE_PRIVATE);
				showComic(sp, name);
				readHistory(sp, name);
			}
		});
	}
	
	private void open(final String[] mpsPaths, final Runnable afterOpen) {
		new Thread() {
			public void run() {
				try {
					mps = new MPSFile(mpsPaths, 1024 * 16);
					mps.open();
					contents = new ArrayList<SimpleEntry<String, String[]>>();
					for (String folder : mps.list()) {
						String[] images = mps.list(folder);
						for (int i = 0; i < images.length; i++) {
							images[i] = folder + "/" + images[i];
						}
						contents.add(new SimpleEntry<String, String[]>(folder, images));
					}
				} catch (Throwable t) {
					t.printStackTrace();
				}
				runOnUiThread(afterOpen);
			}
		}.start();
	}
	
	private void showComic(final SharedPreferences sp, final String name) {
		RelativeLayout rl = new RelativeLayout(this);
		rl.setBackgroundColor(0xffffffff);
		setContentView(rl);
		
		vpPages = new ViewPager(this);
		rl.addView(vpPages, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		
		final TextView tv1 = new TextView(this);
		tv1.setId(1);
		LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
		lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
		rl.addView(tv1, lp);
		tv1.setTextColor(0xff7f7f7f);
		tv1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		tv1.setPadding(50, 0, 0, 50);
		
		final TextView tv2 = new TextView(this);
		lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
		lp.addRule(RelativeLayout.ABOVE, tv1.getId());
		rl.addView(tv2, lp);
		tv2.setTextColor(0xff7f7f7f);
		tv2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		tv2.setPadding(50, 0, 0, 0);
		
		ImageView iv = new ImageView(this);
		lp = new LayoutParams(120, 120);
		lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
		lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
		lp.rightMargin = lp.bottomMargin = 50;
		rl.addView(iv, lp);
		iv.setPadding(20, 20, 20, 20);
		iv.setScaleType(ScaleType.FIT_XY);
		iv.setBackgroundColor(0x3f000000);
		iv.setImageResource(R.drawable.rotate);
		iv.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				int ori = getResources().getConfiguration().orientation;
				if (ori == Configuration.ORIENTATION_PORTRAIT) {
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
				} else {
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
				}
			}
		});
		
		tabs = new TabView[] {
				new TabView(vpPages.getContext(), mps),
				new TabView(vpPages.getContext(), mps),
				new TabView(vpPages.getContext(), mps),
				new TabView(vpPages.getContext(), mps),
				new TabView(vpPages.getContext(), mps)
		};
		
		vpPages.setAdapter(new PagerAdapter() {
			public int getCount() {
				return contents.size();
			}
			
			public boolean isViewFromObject(View view, Object object) {
				return view == object;
			}
			
			public void destroyItem(ViewGroup container, int position, Object object) {
				container.removeView(tabs[position % tabs.length]);
			}
			
			public Object instantiateItem(ViewGroup container, int position) {
				TabView tab = tabs[position % tabs.length];
				container.addView(tab);
				SimpleEntry<String, String[]> e = contents.get(position);
				tab.setImages(e.getKey(), e.getValue());
				return tab;
			}
		});
		vpPages.addOnPageChangeListener(new OnPageChangeListener() {
			public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
			
			}
			
			public void onPageSelected(final int position) {
				sp.edit().putInt("page_" + name, position).apply();
				
				TabView tab = tabs[position % tabs.length];
				SimpleEntry<String, String[]> e = contents.get(position);
				tv1.setText(e.getKey() + " (" + (position + 1) + "/" + contents.size() + ")");
				final int size = e.getValue().length;
				tv2.setText(1 + "/" + size);
				tab.setOnScrollListener(new OnScrollListener() {
					public void onScrollStateChanged(AbsListView view, int scrollState) {
					
					}
					
					public void onScroll(AbsListView view, int first, int count, int total) {
						tv2.setText((first + 1) + "/" + size);
					}
				});
			}
			
			public void onPageScrollStateChanged(int state) {
			
			}
		});
	}
	
	private void readHistory(SharedPreferences sp, String name) {
		int page = sp.getInt("page_" + name, 0);
		vpPages.setCurrentItem(page);
	}
	
	protected void onDestroy() {
		super.onDestroy();
		new Thread() {
			public void run() {
				try {
					if (tabs != null) {
						for (TabView tab : tabs) {
							tab.close();
						}
					}
					mps.close();
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		}.start();
	}
	
}
