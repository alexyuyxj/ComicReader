package m.comicreader.reader;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.HashMap;

public class ImageBuffer {
	private File folder;
	private byte[] buffer;
	private HashMap<String, Image> buffers;
	
	public ImageBuffer(String folder) {
		this(new File(folder));
	}
	
	public ImageBuffer(File folder) {
		this.folder = folder;
		buffers = new HashMap<String, Image>();
	}
	
	public void put(String key, Bitmap bm) throws Throwable {
		int bc = bm.getByteCount();
		ByteBuffer bb;
		synchronized (this) {
			if (buffer == null || buffer.length < bc) {
				buffer = new byte[bc];
			}
			bb = ByteBuffer.wrap(buffer);
		}
		bm.copyPixelsToBuffer(bb);
		put(key, buffer, 0, bc, bm.getWidth(), bm.getHeight(), bm.getConfig());
	}
	
	public void put(String key, byte[] pixels, int offset, int len, int width, int height,
			Config config) throws Throwable {
		saveImage(key, pixels, offset, len, width, height, config);
		openImage(key);
	}
	
	private void openImage(String name) throws Throwable {
		File file = new File(folder, name);
		synchronized (this) {
			if (!file.exists()) {
				return;
			}
		}
		
		int width = 0;
		int height = 0;
		Config config = null;
		RandomAccessFile raf = new RandomAccessFile(file, "r");
		ByteBuffer bb = raf.getChannel().map(MapMode.READ_ONLY, 0, file.length());
		int id = bb.getInt();
		while (id != -1) {
			switch (id) {
				case 0: {
					width = bb.getInt();
				} break;
				case 1: {
					height = bb.getInt();
				} break;
				case 2: {
					switch(bb.getInt()) {
						case 1: config = Config.ALPHA_8; break;
						case 3: config = Config.RGB_565; break;
						case 4: config = Config.ARGB_4444; break;
						case 5: config = Config.ARGB_8888; break;
					}
				} break;
			}
			id = bb.getInt();
		}
		raf.seek(0);
		
		Image image = new Image();
		image.file = file;
		image.raf = raf;
		image.buffer = bb.duplicate();
		image.buffer.position(bb.position());
		image.width = width;
		image.height = height;
		image.config = config;
		
		synchronized (this) {
			buffers.put(name, image);
		}
	}
	
	private void saveImage(String key, byte[] pixels, int offset, int len, int width, int height, Config config)
			throws Throwable {
		Image image;
		synchronized (this) {
			image = buffers.remove(key);
		}
		if (image != null) {
			closeImage(image, true);
		}
		
		File file = new File(folder, key);
		synchronized (this) {
			if (!folder.exists()) {
				folder.mkdirs();
			}
		}
		FileOutputStream fos = new FileOutputStream(file);
		DataOutputStream dos = new DataOutputStream(fos);
		dos.writeInt(0);
		dos.writeInt(width);
		dos.writeInt(1);
		dos.writeInt(height);
		dos.writeInt(2);
		switch(config) {
			case ALPHA_8: dos.writeInt(1); break;
			case RGB_565: dos.writeInt(3); break;
			case ARGB_4444: dos.writeInt(4); break;
			case ARGB_8888: dos.writeInt(5); break;
		}
		dos.writeInt(-1);
		dos.write(pixels, offset, len);
		dos.flush();
		dos.close();
	}
	
	private void closeImage(Image image, boolean delete) throws Throwable {
		if (image != null) {
			image.raf.close();
			if (delete) {
				image.file.delete();
			}
		}
	}
	
	public Bitmap get(String key) throws Throwable {
		Image image;
		synchronized(this) {
			image = buffers.get(key);
		}
		if (image == null) {
			openImage(key);
			synchronized(this) {
				image = buffers.get(key);
			}
			if (image == null) {
				return null;
			}
		}
		
		int pos = image.buffer.position();
		Bitmap bm = Bitmap.createBitmap(image.width, image.height, image.config);
		bm.copyPixelsFromBuffer(image.buffer);
		image.buffer.position(pos);
		return bm;
	}
	
	public void remove(String key) throws Throwable {
		remove(key, false);
	}
	
	public void remove(String key, boolean delete) throws Throwable {
		Image image;
		synchronized (this) {
			image = buffers.remove(key);
		}
		closeImage(image, delete);
	}
	
	public void clear() throws Throwable {
		clear(false);
	}
	
	public void clear(boolean delete) throws Throwable {
		synchronized(this) {
			for (Image image : buffers.values()) {
				closeImage(image, delete);
			}
			buffers.clear();
		}
	}
	
	public int size() {
		synchronized(this) {
			return buffers.size();
		}
	}
	
	private class Image {
		private File file;
		private RandomAccessFile raf;
		private ByteBuffer buffer;      // id = -1
		private int width;              // id = 0
		private int height;             // id = 1
		private Config config;          // id = 2
	}
	
}
