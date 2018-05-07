package m.comicreader.recreator;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.text.SimpleDateFormat;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

public class MPSReCreator {
	
	public void recreate(File[] mpsFiles, int partSize) throws Throwable {
		File folder = createTmpFolder(mpsFiles[0]);
		ArrayList<String> paths = dumpToFile(mpsFiles, folder, partSize);
		File newMps = saveToBuffer(folder, paths, partSize);
		File tmpFile = new File(mpsFiles[0].getParentFile(), newMps.getName() + ".tmp");
		newMps.renameTo(tmpFile);
		newMps = tmpFile;
		deleteFolder(folder);
		for (File mps : mpsFiles) {
			mps.delete();
		}
		newMps.renameTo(mpsFiles[0]);
	}
	
	private File createTmpFolder(File mps) throws Throwable {
		String name = mps.getName();
		int index = name.lastIndexOf(".mps");
		name = name.substring(0, index);
		File folder = new File(mps.getParentFile(), name);
		if (folder.exists()) {
			if (folder.isFile()) {
				folder.delete();
			} else {
				deleteFolder(folder);
			}
		}
		folder.mkdirs();
		return folder;
	}
	
	private void deleteFolder(File folder) throws Throwable {
		String[] names = folder.list();
		if (names != null) {
			for (String name : names) {
				File file = new File(folder, name);
				if (file.isDirectory()) {
					deleteFolder(file);
				} else {
					file.delete();
				}
			}
		}
		folder.delete();
	}
	
	private ArrayList<String> dumpToFile(File[] mpsFiles, File folder, int partSize) throws Throwable {
		ArrayList<SimpleEntry<String, ByteBuffer[]>> pathToBuffers = new ArrayList<SimpleEntry<String, ByteBuffer[]>>();
		for (File mps : mpsFiles) {
			Object[] res = open(mps, partSize, pathToBuffers);
			FileInputStream fis = (FileInputStream) res[0];
			saveToFiles(folder, pathToBuffers, partSize);
			fis.close();
		}
		
		ArrayList<String> paths = new ArrayList<String>();
		for (SimpleEntry<String, ByteBuffer[]> ent : pathToBuffers) {
			paths.add(ent.getKey());
		}
		return paths;
	}
	
	private Object[] open(File mps, int partSize, ArrayList<SimpleEntry<String, ByteBuffer[]>> pathToBuffers)
			throws Throwable {
		FileInputStream fis = new FileInputStream(mps);
		DataInputStream dis = new DataInputStream(fis);
		ArrayList<SimpleEntry<String, int[][]>> pathToIndexes = new ArrayList<SimpleEntry<String, int[][]>>();
		for (int i = 0, len = dis.readInt(); i < len; i++) {
			String path = dis.readUTF();
			int[][] indexes = new int[2][dis.readInt()];
			for (int j = 0; j < indexes[0].length; j++) {
				indexes[0][j] = dis.readInt();
				indexes[1][j] = dis.readInt();
			}
			pathToIndexes.add(new SimpleEntry<String, int[][]>(path, indexes));
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
		
		for (SimpleEntry<String, int[][]> ent : pathToIndexes) {
			int[][] indexes = ent.getValue();
			ByteBuffer[] bbs = new ByteBuffer[indexes[0].length];
			for (int i = 0; i < bbs.length; i++) {
				bbs[i] = buffers[indexes[0][i]];
				bbs[i].limit(bbs[i].position() + indexes[1][i]);
			}
			pathToBuffers.add(new SimpleEntry<String, ByteBuffer[]>(ent.getKey(), bbs));
		}
		
		return new Object[] {fis, pathToBuffers};
	}
	
	private void saveToFiles(File folder, ArrayList<SimpleEntry<String, ByteBuffer[]>> pathToBuffers, int partSize)
			throws Throwable {
		byte[] buf = new byte[partSize];
		for (SimpleEntry<String, ByteBuffer[]> ent : pathToBuffers) {
			File image = new File(folder, ent.getKey().replace("/", File.separator));
			if (!image.getParentFile().exists()) {
				image.getParentFile().mkdirs();
			}
			
			FileOutputStream fos = new FileOutputStream(image);
			ByteBuffer[] buffers = ent.getValue();
			for (ByteBuffer bb : buffers) {
				int pos = bb.position();
				int len = bb.remaining();
				bb.get(buf, 0, len);
				fos.write(buf, 0, len);
				bb.position(pos);
			}
			fos.flush();
			fos.close();
		}
	}
	
	private File saveToBuffer(File folder, ArrayList<String> paths, int partSize) throws Throwable {
		ArrayList<SimpleEntry<String, ArrayList<File>>> images = listImage(folder, paths);
		ArrayList<ByteBuffer> buffers = new ArrayList<ByteBuffer>();
		HashMap<File, int[]> fileToBuffers = new HashMap<File, int[]>();
		for (SimpleEntry<String, ArrayList<File>> ent : images) {
			for (File image : ent.getValue()) {
				List<ByteBuffer> bbs = Arrays.asList(imageToBuffers(image, partSize));
				int begin = buffers.size();
				buffers.addAll(bbs);
				int end = buffers.size();
				fileToBuffers.put(image, new int[] {begin, end});
			}
		}
		
		int[] rnds = random(buffers);
		ArrayList<SimpleEntry<String, int[][]>> pathToBuffers = new ArrayList<SimpleEntry<String,int[][]>>();
		for (SimpleEntry<String, ArrayList<File>> ent : images) {
			for (File image : ent.getValue()) {
				String path = ent.getKey() + "/" + image.getName();
				int[] arr = fileToBuffers.get(image);
				int[] rndIndexes = findIndex(arr[0], arr[1], rnds);
				int[][] indexes = new int[2][rndIndexes.length];
				indexes[0] = rndIndexes;
				for (int i = 0; i < rndIndexes.length; i++) {
					indexes[1][i] = buffers.get(rndIndexes[i]).remaining();
				}
				pathToBuffers.add(new SimpleEntry<String, int[][]>(path, indexes));
			}
		}
		
		return saveToFile(folder, buffers, pathToBuffers, partSize);
	}
	
	private ArrayList<SimpleEntry<String, ArrayList<File>>> listImage(File folder, ArrayList<String> paths) {
		ArrayList<SimpleEntry<String, ArrayList<File>>> images = new ArrayList<SimpleEntry<String, ArrayList<File>>>();
		for (String path : paths) {
			File image = new File(folder, path.replace("/", File.separator));
			String chapter = image.getParentFile().getName();
			ArrayList<File> files = null;
			for (SimpleEntry<String, ArrayList<File>> ent : images) {
				if (ent.getKey().equals(chapter)) {
					files = ent.getValue();
					break;
				}
			}
			if (files == null) {
				files = new ArrayList<File>();
				images.add(new SimpleEntry<String, ArrayList<File>>(chapter, files));
			}
			files.add(image);
		}
		return images;
	}
	
	private ByteBuffer[] imageToBuffers(File imageFile, int partSize) throws Throwable {
		FileInputStream fis = new FileInputStream(imageFile);
		ByteBuffer bb = fis.getChannel().map(MapMode.READ_ONLY, 0, imageFile.length());
		fis.close();
		
		long parts = imageFile.length() / partSize;
		if (parts * partSize < imageFile.length()) {
			parts++;
		}
		ByteBuffer[] buf = new ByteBuffer[(int) parts];
		
		for (int i = 0; i < buf.length; i++) {
			buf[i] = bb.duplicate();
			buf[i].position(i * partSize);
			buf[i].limit(Math.min(buf[i].position() + partSize, bb.limit()));
		}
		return buf;
	}
	
	private int[] random(ArrayList<ByteBuffer> buffers) throws Throwable {
		LinkedList<Integer> list = new LinkedList<Integer>();
		for (int i = 0, size = buffers.size(); i < size; i++) {
			list.add(i);
		}
		
		int len = 0;
		int[] rnds = new int[buffers.size()];
		ArrayList<ByteBuffer> tmp = new ArrayList<ByteBuffer>();
		Random rnd = new Random();
		while (list.size() > 0) {
			int r = rnd.nextInt(list.size());
			int index = list.remove(r);
			rnds[len] = index;
			tmp.add(buffers.get(index));
			len++;
		}
		
		buffers.clear();
		buffers.addAll(tmp);
		return rnds;
	}
	
	private int[] findIndex(int begin, int end, int[] rnds) throws Throwable {
		int[] res = new int[end - begin];
		for (int i = 0; i < res.length; i++) {
			for (int j = 0; j < rnds.length; j++) {
				if (rnds[j] == i + begin) {
					res[i] = j;
				}
			}
		}
		return res;
	}
	
	private File saveToFile(File folder, ArrayList<ByteBuffer> buffers,
			ArrayList<SimpleEntry<String, int[][]>> pathToBuffers, int partSize) throws Throwable {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(baos);
		dos.writeInt(pathToBuffers.size());
		for (SimpleEntry<String, int[][]> ent : pathToBuffers) {
			dos.writeUTF(ent.getKey());
			int[][] indexes = ent.getValue();
			dos.writeInt(indexes[0].length);
			for (int i = 0; i < indexes[0].length; i++) {
				dos.writeInt(indexes[0][i]);
				dos.writeInt(indexes[1][i]);
			}
		}
		dos.flush();
		dos.close();
		
		byte[] buf = baos.toByteArray();
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(System.currentTimeMillis());
		String date = new SimpleDateFormat("yyyyMMdd").format(cal.getTime());
		File bufferFile = new File(folder, folder.getName() + ".mps." + date.substring(2));
		FileOutputStream fos = new FileOutputStream(bufferFile);
		dos = new DataOutputStream(fos);
		dos.write(buf);
		dos.writeInt(buf.length);
		buf = new byte[partSize];
		for (ByteBuffer bb : buffers) {
			int len = Math.min(bb.remaining(), buf.length);
			bb.get(buf, 0, len);
			dos.write(buf);
		}
		dos.flush();
		dos.close();
		
		return bufferFile;
	}
}
