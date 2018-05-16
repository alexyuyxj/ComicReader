package m.comicreader.creator;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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
import android.widget.Toast;

import com.mob.tools.RxMob;
import com.mob.tools.RxMob.QuickSubscribe;
import com.mob.tools.RxMob.Subscriber;

import org.json.JSONObject;

import java.io.File;
import java.io.FilenameFilter;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class MPSCreatorActivity extends Activity {
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final ListView lv = new ListView(this);
		lv.setBackgroundColor(0xffffffff);
		lv.setCacheColorHint(0);
		lv.setDividerHeight(1);
		lv.setSelector(new ColorDrawable());
		setContentView(lv);
		
		RxMob.create(new QuickSubscribe<HashMap<String, ArrayList<Chapter>>>() {
			protected void doNext(Subscriber<HashMap<String, ArrayList<Chapter>>> subscriber) throws Throwable {
				SQLiteDatabase db = copyDatabase();
				HashMap<String, ArrayList<Chapter>> chapters = collectFiles(db);
				db.close();
				subscriber.onNext(chapters);
			}
		}).subscribeOnNewThreadAndObserveOnUIThread(new Subscriber<HashMap<String, ArrayList<Chapter>>>() {
			public void onNext(HashMap<String, ArrayList<Chapter>> chapters) {
				initUI(chapters, lv);
			}
		});
	}
	
	private SQLiteDatabase copyDatabase() {
		String dbPath = "/sdcard/mps/.db";
		try {
			Process p = Runtime.getRuntime().exec("su");
			OutputStream os = p.getOutputStream();
			os.write(("cp /data/data/com.dmzj.manhua/databases/cartoon " + dbPath + "\n").getBytes("utf-8"));
			os.flush();
			os.write("exit\n".getBytes("utf-8"));
			os.flush();
			p.waitFor();
			p.destroy();
		} catch (Throwable t) {
			t.printStackTrace();
		}
		
		SQLiteDatabase db = null;
		try {
			File dbFile = new File(dbPath);
			if (dbFile.exists()) {
				db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return db;
	}
	
	private HashMap<String, ArrayList<Chapter>> collectFiles(SQLiteDatabase db) {
		HashMap<String, ArrayList<Chapter>> chapters = new HashMap<String, ArrayList<Chapter>>();
		File folder = new File("/sdcard/Android/data/com.dmzj.manhua/files/dmzj/DownLoad");
		if (folder.exists()) {
			String[] names = folder.list(new FilenameFilter() {
				public boolean accept(File file, String name) {
					return name.endsWith(".zip");
				}
			});
			if (names != null) {
				for (String name : names) {
					Chapter chapter = new Chapter();
					chapter.zipFile = new File(folder, name);
					name = name.substring(0, name.length() - 4);
					String[] parts = name.split("_");
					String comicName = null;
					if (db != null) {
						Cursor c = db.rawQuery("SELECT commic_info FROM commic_cache where commic_id = " + parts[0], null);
						if (c != null) {
							if (c.moveToFirst()) {
								String info = c.getString(c.getColumnIndex("commic_info"));
								try {
									JSONObject json = new JSONObject(info);
									comicName = json.optString("title");
								} catch (Throwable t) {}
							}
							c.close();
						}
					}
					chapter.comicName = comicName == null ? parts[0] : comicName;
					String title = null;
					int order = -1;
					if (db != null) {
						Cursor c = db.rawQuery("" +
								"SELECT chapter_title, chapter_order " +
								"FROM downloadwrapper " +
								"WHERE commic_id = " + parts[0] + " and chapterid = " + parts[1], null);
						if (c != null) {
							if (c.moveToFirst()) {
								title = c.getString(c.getColumnIndex("chapter_title"));
								order = c.getInt(c.getColumnIndex("chapter_order"));
							}
							c.close();
						}
					}
					chapter.title = title == null ? parts[1] : title;
					chapter.order = order;
					
					ArrayList<Chapter> list = chapters.get(chapter.comicName);
					if (list == null) {
						list = new ArrayList<Chapter>();
						chapters.put(chapter.comicName, list);
					}
					list.add(chapter);
				}
			}
		}
		return chapters;
	}

	private void initUI(final HashMap<String, ArrayList<Chapter>> chapters, final ListView lv) {
		final ArrayList<String> names = new ArrayList<String>(chapters.keySet());
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
			public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
				Toast.makeText(MPSCreatorActivity.this, "Started", Toast.LENGTH_SHORT).show();
				lv.setOnItemClickListener(null);
				String name = names.get(position);
				zipToBuffer(chapters, name, lv);
			}
		});
	}
	
	private void zipToBuffer(final HashMap<String, ArrayList<Chapter>> chapters, final String name, final ListView lv) {
		RxMob.create(new QuickSubscribe<String>() {
			protected void doNext(Subscriber<String> subscriber) throws Throwable {
				File result = null;
				Throwable err = null;
				try {
					File mps = new MPSCreator().zipToBuffer(chapters.get(name), 1024 * 16);
					File tmp = new File("/sdcard/mps", mps.getName());
					mps.renameTo(tmp);
					result = tmp;
				} catch (final Throwable t) {
					err = t;
				}
				String msg;
				if (result != null) {
					chapters.remove(name);
					msg = "Finished";
				} else if (err != null) {
					msg = err.getMessage();
				} else {
					msg = "Failed";
				}
				subscriber.onNext(msg);
			}
		}).subscribeOnNewThreadAndObserveOnUIThread(new Subscriber<String>() {
			public void onNext(String msg) {
				initUI(chapters, lv);
				Toast.makeText(MPSCreatorActivity.this, msg, Toast.LENGTH_SHORT).show();
			}
		});
	}
	
	protected void onDestroy() {
		super.onDestroy();
		System.exit(0);
	}
}
