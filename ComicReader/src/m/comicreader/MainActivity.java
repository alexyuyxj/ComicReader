package m.comicreader;

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

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;

import m.comicreader.creator.MPSCreatorActivity;
import m.comicreader.reader.ComicReaderActivity;
import m.comicreader.recreator.MPSReCreatorActivity;

public class MainActivity extends Activity {
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final ListView lv = new ListView(this);
		lv.setBackgroundColor(0xffffffff);
		lv.setCacheColorHint(0);
		lv.setDividerHeight(1);
		lv.setSelector(new ColorDrawable());
		setContentView(lv);
		
		final ArrayList<SimpleEntry<String, Class<? extends Activity>>> pages
				= new ArrayList<SimpleEntry<String, Class<? extends Activity>>>();
		pages.add(new SimpleEntry<String, Class<? extends Activity>>("ComicReader", ComicReaderActivity.class));
		pages.add(new SimpleEntry<String, Class<? extends Activity>>("MPSCreator", MPSCreatorActivity.class));
		pages.add(new SimpleEntry<String, Class<? extends Activity>>("MPSReCreator", MPSReCreatorActivity.class));
		
		lv.setAdapter(new BaseAdapter() {
			public int getCount() {
				return pages.size();
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
				tv.setText(pages.get(position).getKey());
				return convertView;
			}
		});
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Intent i = new Intent(parent.getContext(), pages.get(position).getValue());
				startActivity(i);
				finish();
			}
		});
	}
	
}
