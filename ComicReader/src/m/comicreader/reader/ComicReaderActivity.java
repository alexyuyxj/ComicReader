package m.comicreader.reader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils.TruncateAt;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class ComicReaderActivity extends Activity {
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final ListView lv = new ListView(this);
		lv.setBackgroundColor(0xffffffff);
		lv.setCacheColorHint(0);
		lv.setDividerHeight(1);
		lv.setSelector(new ColorDrawable());
		setContentView(lv);
		
		new Thread() {
			public void run() {
				final HashMap<String, ArrayList<File>> files = collectFiles("/sdcard/mps");
				runOnUiThread(new Runnable() {
					public void run() {
						initUI(files, lv);
					}
				});
			}
		}.start();
	}
	
	private HashMap<String, ArrayList<File>> collectFiles(String path) {
		HashMap<String, ArrayList<File>> result = new HashMap<String, ArrayList<File>>();
		File mpsFolder = new File(path);
		if (mpsFolder.exists()) {
			File[] files = mpsFolder.listFiles(new FileFilter() {
				public boolean accept(File file) {
					if (file.isFile()) {
						String name = file.getName();
						String[] parts = name.split("\\.");
						if ("mps".equals(parts[parts.length - 1])) {
							return true;
						} else if (parts.length > 1 && "mps".equals(parts[parts.length - 2])) {
							try {
								Integer.parseInt(parts[parts.length - 1]);
								return true;
							} catch (Throwable t) {}
						}
					}
					return false;
				}
			});
			if (files != null) {
				for (File file : files) {
					String name = file.getName();
					if (!name.endsWith(".mps")) {
						name = name.substring(0, name.lastIndexOf("."));
					}
					name = name.substring(0, name.length() - 4);
					ArrayList<File> list = result.get(name);
					if (list == null) {
						list = new ArrayList<File>();
						result.put(name, list);
					}
					list.add(file);
				}
			}
		}
		
		for (ArrayList<File> list : result.values()) {
			Collections.sort(list, new Comparator<File>() {
				public int compare(File lhs, File rhs) {
					int lIndex = 0;
					String lName = lhs.getName();
					if (!lName.endsWith(".mps")) {
						lName = lName.substring(lName.lastIndexOf(".") + 1);
						lIndex = Integer.parseInt(lName);
					}
					
					int rIndex = 0;
					String rName = rhs.getName();
					if (!rName.endsWith(".mps")) {
						rName = rName.substring(rName.lastIndexOf(".") + 1);
						rIndex = Integer.parseInt(rName);
					}
					
					return lIndex < rIndex ? -1 : (lIndex == rIndex ? 0 : 1);
				}
			});
		}
		return result;
	}
	
	private void initUI(final HashMap<String, ArrayList<File>> files, ListView lv) {
		final ArrayList<String> names = new ArrayList<String>(files.keySet());
		Collections.sort(names);
		lv.setAdapter(new BaseAdapter() {
			public int getCount() {
				return names.size();
			}
			
			public Object getItem(int position) {
				return position;
			}
			
			public long getItemId(int position) {
				return position;
			}
			
			public View getView(int position, View convertView, ViewGroup parent) {
				if (convertView == null) {
					TextView tv = new TextView(parent.getContext());
					tv.setPadding(50, 50, 50, 50);
					tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
					tv.setTextColor(0xff000000);
					tv.setMaxLines(1);
					tv.setEllipsize(TruncateAt.END);
					convertView = tv;
				}
				TextView tv = (TextView) convertView;
				tv.setText(names.get(position));
				return convertView;
			}
		});
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				ArrayList<File> list = files.get(names.get(position));
				String[] paths = new String[list.size()];
				for (int i = 0; i < paths.length; i++) {
					paths[i] = list.get(i).getAbsolutePath();
				}
				Intent i = new Intent(parent.getContext(), ReaderActivity.class);
				i.putExtra("mps", paths);
				startActivity(i);
			}
		});
	}
	
	protected void onDestroy() {
		super.onDestroy();
		new Thread() {
			public void run() {
				try {
					File folder = new File("/sdcard/mps/cache");
					if (folder.exists()) {
						String[] names = folder.list();
						if (names != null) {
							for (String name : names) {
								new File(folder, name).delete();
							}
						}
					}
				} catch (Throwable t) {
					t.printStackTrace();
				}
				System.exit(0);
			}
		}.start();
	}
}
