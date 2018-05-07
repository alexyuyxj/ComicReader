package m.comicreader.creator;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.text.SimpleDateFormat;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MPSCreator {
	
	public File zipToBuffer(ArrayList<Chapter> chapters, int partSize) throws Throwable {
		Chapter sample = chapters.get(0);
		File comicFolder = new File(sample.zipFile.getParentFile(), sample.comicName);
		for (Chapter chapter : chapters) {
			decompress(chapter, comicFolder);
		}
		File mps = saveToBuffer(chapters, comicFolder, partSize);
		
		File tmp = new File(comicFolder.getParentFile(), mps.getName());
		mps.renameTo(tmp);
		mps = tmp;
		
		for (Chapter chapter : chapters) {
			for (String name : chapter.folder.list()) {
				new File(chapter.folder, name).delete();
			}
			chapter.folder.delete();
		}
		comicFolder.delete();
		
		return mps;
	}

	private void decompress(Chapter chapter, File comicFolder) throws Throwable {
		chapter.folder = new File(comicFolder, chapter.title);
		if (!chapter.folder.exists()) {
			chapter.folder.mkdirs();
		}
		
		byte[] buf = new byte[1024 * 256];
		ZipFile zipFile = new ZipFile(chapter.zipFile);
		Enumeration<? extends ZipEntry> entries = zipFile.entries();
		while (entries.hasMoreElements()) {
			ZipEntry e = entries.nextElement();
			InputStream is = zipFile.getInputStream(e);
			FileOutputStream fos = new FileOutputStream(new File(chapter.folder, e.getName()));
			int len = is.read(buf);
			while (len != -1) {
				fos.write(buf, 0, len);
				len = is.read(buf);
			}
			is.close();
			fos.flush();
			fos.close();
		}
		zipFile.close();
	}

	private File saveToBuffer(ArrayList<Chapter> chapters, File comicFolder, int partSize) throws Throwable {
		ArrayList<SimpleEntry<String, List<File>>> images = listImage(chapters);
		ArrayList<ByteBuffer> buffers = new ArrayList<ByteBuffer>();
		HashMap<File, int[]> fileToBuffers = new HashMap<File, int[]>();
		for (SimpleEntry<String, List<File>> ent : images) {
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
		for (SimpleEntry<String, List<File>> ent : images) {
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

		return saveToFile(comicFolder, buffers, pathToBuffers, partSize);
	}

	private ArrayList<SimpleEntry<String, List<File>>> listImage(ArrayList<Chapter> chapters) throws Throwable {
		ArrayList<SimpleEntry<String, List<File>>> list = new ArrayList<SimpleEntry<String,List<File>>>();
		Collections.sort(chapters, new Comparator<Chapter>() {
			public int compare(Chapter cl, Chapter cr) {
				if (cl.order == cr.order) {
					return cl.title.compareTo(cr.title);
				} else {
					return cl.order < cr.order ? -1 : 1;
				}
			}
		});
		for (Chapter chapter : chapters) {
			List<File> files = Arrays.asList(chapter.folder.listFiles());
			Collections.sort(files, new Comparator<File>() {
				public int compare(File left, File right) {
					try {
						String ln = left.getName();
						ln = ln.substring(0, ln.lastIndexOf("."));
						int l = Integer.parseInt(ln);
						try {
							String rn = right.getName();
							rn = rn.substring(0, rn.lastIndexOf("."));
							int r = Integer.parseInt(rn);
							return l == r ? 0 : (l < r ? -1 : 1);
						} catch (Throwable t) {}
					} catch (Throwable t) {}
					return left.getName().compareTo(right.getName());
				}
			});
			list.add(new SimpleEntry<String, List<File>>(chapter.title, files));
		}
		return list;
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
