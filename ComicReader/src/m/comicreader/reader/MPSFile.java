package m.comicreader.reader;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class MPSFile {
	private String[] mpsPaths;
	private int partSize;
	private FileInputStream[] fises;
	private String[] paths;
	private HashMap<String, ByteBuffer[]> pathToBuffers;
	
	public MPSFile(String[] mpsPaths, int partSize) {
		this.mpsPaths = mpsPaths;
		this.partSize = partSize;
	}
	
	public void open() throws Throwable {
		fises = new FileInputStream[mpsPaths.length];
		ArrayList<String> pathList = new ArrayList<String>();
		pathToBuffers = new HashMap<String, ByteBuffer[]>();
		for (int i = 0; i < mpsPaths.length; i++) {
			Object[] res = open(mpsPaths[i]);
			fises[i] = (FileInputStream) res[0];
			pathList.addAll(Arrays.asList((String[]) res[1]));
			pathToBuffers.putAll((HashMap<String, ByteBuffer[]>) res[2]);
		}
		paths = pathList.toArray(new String[pathList.size()]);
	}
	
	private Object[] open(String path) throws Throwable {
		File mps = new File(path);
		FileInputStream fis = new FileInputStream(mps);
		DataInputStream dis = new DataInputStream(fis);
		String[] paths = new String[dis.readInt()];
		ArrayList<SimpleEntry<String, int[][]>> pathToIndexes = new ArrayList<SimpleEntry<String, int[][]>>();
		for (int i = 0; i < paths.length; i++) {
			paths[i] = dis.readUTF();
			int[][] indexes = new int[2][dis.readInt()];
			for (int j = 0; j < indexes[0].length; j++) {
				indexes[0][j] = dis.readInt();
				indexes[1][j] = dis.readInt();
			}
			pathToIndexes.add(new SimpleEntry<String, int[][]>(paths[i], indexes));
		}
		int offset = dis.readInt() + 4;
		dis.close();
		
		fis = new FileInputStream(mps);
		ByteBuffer bb = fis.getChannel().map(MapMode.READ_ONLY, offset, mps.length() - offset);
		ByteBuffer[] buffers = new ByteBuffer[bb.remaining() / partSize];
		for (int i = 0; i < buffers.length; i++) {
			buffers[i] = bb.duplicate();
			buffers[i].position(bb.position() + i * partSize);
			buffers[i].limit(buffers[i].position() + partSize);
		}
		
		HashMap<String, ByteBuffer[]> pathToBuffers = new HashMap<String, ByteBuffer[]>();
		for (SimpleEntry<String, int[][]> ent : pathToIndexes) {
			int[][] indexes = ent.getValue();
			ByteBuffer[] bbs = new ByteBuffer[indexes[0].length];
			for (int i = 0; i < bbs.length; i++) {
				bbs[i] = buffers[indexes[0][i]];
				bbs[i].limit(bbs[i].position() + indexes[1][i]);
			}
			pathToBuffers.put(ent.getKey(), bbs);
		}
		
		return new Object[] {fis, paths, pathToBuffers};
	}
	
	public String[] list() {
		return list("");
	}
	
	public String[] list(String path) {
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		if (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		ArrayList<String> children = new ArrayList<String>();
		if (paths != null) {
			for (String key : paths) {
				if (key.startsWith("/")) {
					key = key.substring(1);
				}
				if (key.endsWith("/")) {
					key = key.substring(0, path.length() - 1);
				}
				if (key.startsWith(path)) {
					String name = key.substring(path.length());
					if (name.startsWith("/")) {
						name = name.substring(1);
					}
					name = name.split("/")[0];
					if (!children.contains(name)) {
						children.add(name);
					}
				}
			}
		}
		return children.toArray(new String[children.size()]);
	}
	
	public int read(String path, OutputStream output) throws Throwable {
		if (pathToBuffers != null) {
			ByteBuffer[] buffers = pathToBuffers.get(path);
			if (buffers != null) {
				byte[] buf = new byte[partSize];
				int count = 0;
				for (ByteBuffer bb : buffers) {
					int pos = bb.position();
					int len = bb.remaining();
					bb.get(buf, 0, len);
					output.write(buf, 0, len);
					output.flush();
					bb.position(pos);
					count += len;
				}
				return count;
			}
		}
		return -1;
	}
	
	public void close() throws Throwable {
		paths = null;
		pathToBuffers = null;
		if (fises != null) {
			for (FileInputStream fis : fises) {
				if (fis != null) {
					fis.close();
				}
			}
		}
	}
	
}
