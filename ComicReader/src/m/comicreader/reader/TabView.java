package m.comicreader.reader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler.Callback;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.ListView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import m.comicreader.R;

public class TabView extends ListView {
	private MPSFile mps;
	private ImageBuffer buffer;
	private String prefix;
	
	public TabView(Context context, MPSFile mps) {
		super(context);
		this.mps = mps;
		buffer = new ImageBuffer("/sdcard/mps/cache");
		
		setBackgroundColor(0xffffffff);
		setCacheColorHint(0);
		setDividerHeight(0);
		setSelector(new ColorDrawable());
		setVerticalScrollBarEnabled(false);
		
		setAdapter(newAdapter());
	}
	
	private BaseAdapter newAdapter() {
		return new BaseAdapter() {
			public int getCount() {
				return buffer.size();
			}
			
			public Object getItem(int position) {
				return position;
			}
			
			public long getItemId(int position) {
				return position;
			}
			
			public View getView(int position, View convertView, ViewGroup parent) {
				if (convertView == null) {
					ImageView iv = new ImageView(parent.getContext());
					iv.setScaleType(ScaleType.FIT_CENTER);
					iv.setAdjustViewBounds(true);
					convertView = iv;
				}
				
				ImageView iv = (ImageView) convertView;
				try {
					Bitmap bm = buffer.get(prefix + position);
					if (bm == null) {
						iv.setImageResource(R.drawable.ic_launcher);
					} else {
						iv.setImageBitmap(bm);
					}
				} catch (Throwable t) {
					t.printStackTrace();
					iv.setImageResource(R.drawable.ic_launcher);
				}
				return convertView;
			}
		};
	}
	
	public void setImages(String name, final String[] images) {
		try {
			this.prefix = name;
			buffer.clear(true);
		} catch (Throwable t) {
			t.printStackTrace();
		}
		((BaseAdapter) getAdapter()).notifyDataSetChanged();
		
		new Thread() {
			public void run() {
				try {
					final ImageBuffer ib = buffer;
					decode(images, new Callback() {
						public boolean handleMessage(Message msg) {
							if (ib.equals(buffer)) {
								try {
									ib.put(prefix + msg.arg1, (Bitmap) msg.obj);
									((BaseAdapter) TabView.this.getAdapter()).notifyDataSetChanged();
								} catch (Throwable t) {
									t.printStackTrace();
								}
							}
							return false;
						}
					});
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		}.start();
	}
	
	private void decode(String[] images, final Callback cb) throws Throwable {
		final int[] len = new int[1];
		final byte[][] baosBuf = new byte[1][];
		ByteArrayOutputStream baos = new ByteArrayOutputStream() {
			public void flush() throws IOException {
				len[0] = count;
				baosBuf[0] = buf;
			}
		};
		
		for (int i = 0; i < images.length; i++) {
			baos.reset();
			mps.read(images[i], baos);
			Options opt = new Options();
			opt.inPreferredConfig = Config.RGB_565;
			final Bitmap bm = BitmapFactory.decodeByteArray(baosBuf[0], 0, len[0], opt);
			final int index = i;
			post(new Runnable() {
				public void run() {
					Message msg = new Message();
					msg.arg1 = index;
					msg.obj = bm;
					cb.handleMessage(msg);
				}
			});
		}
	}
	
	public void close() {
		try {
			buffer.clear(true);
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
	
}
