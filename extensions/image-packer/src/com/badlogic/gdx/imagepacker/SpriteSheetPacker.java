
package com.badlogic.gdx.imagepacker;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.utils.MathUtils;

public class SpriteSheetPacker {
	static Pattern numberedImagePattern = Pattern.compile(".*?(\\d+)");

	ArrayList<Image> images = new ArrayList();
	FileWriter writer;
	final File inputDir;
	int uncompressedSize, compressedSize;
	int xPadding, yPadding;
	final Filter filter;
	int minWidth, minHeight;
	int maxWidth, maxHeight;
	final Settings settings;

	public SpriteSheetPacker (Settings settings, File inputDir, Filter filter, File outputDir, File packFile) throws IOException {
		this.settings = settings;
		this.inputDir = inputDir;
		this.filter = filter;

		minWidth = filter.width != -1 ? filter.width : settings.minWidth;
		minHeight = filter.height != -1 ? filter.height : settings.minHeight;
		maxWidth = filter.width != -1 ? filter.width : settings.maxWidth;
		maxHeight = filter.height != -1 ? filter.height : settings.maxHeight;
		xPadding = images.size() > 1 && filter.direction != Direction.x && filter.direction != Direction.xy ? settings.padding : 0;
		yPadding = images.size() > 1 && filter.direction != Direction.y && filter.direction != Direction.xy ? settings.padding : 0;

		// Collect and squeeze images.
		File[] files = inputDir.listFiles(filter);
		if (files == null) return;
		for (File file : files) {
			if (file.isDirectory()) continue;
			Image image = squeeze(file);
			if (image != null) images.add(image);
		}
		if (images.isEmpty()) return;

		System.out.println(inputDir);
		if (filter.format != null)
			System.out.println("Format: " + filter.format);
		else
			System.out.println("Format: " + settings.defaultFormat + " (default)");
		if (filter.minFilter != null && filter.magFilter != null)
			System.out.println("Filter: " + filter.minFilter + ", " + filter.magFilter);
		else
			System.out.println("Filter: " + settings.defaultFilterMin + ", " + settings.defaultFilterMag + " (default)");
		if (filter.direction != Direction.none) System.out.println("Repeat: " + filter.direction);

		outputDir.mkdirs();
		String prefix = inputDir.getName();
		writer = new FileWriter(packFile, true);
		try {
			while (!images.isEmpty())
				writePage(prefix, outputDir);
			if (writer != null) {
				System.out.println("Pixels eliminated: " + (1 - compressedSize / (float)uncompressedSize) * 100 + "%");
				System.out.println();
			}
		} finally {
			writer.close();
		}
	}

	private void writePage (String prefix, File outputDir) throws IOException {
		// Remove existing image pages in output dir.
		int imageNumber = 1;
		File outputFile = new File(outputDir, prefix + imageNumber + ".png");
		while (outputFile.exists())
			outputFile = new File(outputDir, prefix + ++imageNumber + ".png");

		writer.write("\n" + prefix + imageNumber + ".png\n");
		Format format;
		if (filter.format != null) {
			writer.write("format: " + filter.format + "\n");
			format = filter.format;
		} else {
			writer.write("format: " + settings.defaultFormat + "\n");
			format = settings.defaultFormat;
		}
		if (filter.minFilter == null || filter.magFilter == null)
			writer.write("filter: " + settings.defaultFilterMin + "," + settings.defaultFilterMag + "\n");
		else
			writer.write("filter: " + filter.minFilter + "," + filter.magFilter + "\n");
		writer.write("repeat: " + filter.direction + "\n");

		// Try reasonably hard to pack images into the smallest POT size.
		Comparator bestComparator = null;
		Comparator secondBestComparator = imageComparators.get(0);
		int bestWidth = 99999, bestHeight = 99999;
		int secondBestWidth = 99999, secondBestHeight = 99999;
		int bestUsedPixels = 0;
		int width = minWidth, height = minHeight;
		int grownPixels = 0, grownPixels2 = 0;
		int i = 0, ii = 0;
		while (true) {
			if (width > maxWidth && height > maxHeight) break;
			for (Comparator comparator : imageComparators) {
				// Pack as many images as possible, sorting the images different ways.
				Collections.sort(images, comparator);
				int usedPixels = insert(null, new ArrayList(images), width, height);
				// Store the best pack, in case not all images fit on the max texture size.
				if (usedPixels > bestUsedPixels) {
					secondBestComparator = comparator;
					secondBestWidth = width;
					secondBestHeight = height;
				}
				// If all images fit and this sort is the best so far, take note.
				if (usedPixels == -1) {
					if (width * height < bestWidth * bestHeight) {
						bestComparator = comparator;
						bestWidth = width;
						bestHeight = height;
					}
				}
			}
			if (bestComparator != null) break;
			if (settings.pot) {
				// 64,64 then 64,128 then 128,64 then 128,128 then 128,256 etc.
				if (i % 3 == 0) {
					width *= 2;
					i++;
				} else if (i % 3 == 1) {
					width /= 2;
					height *= 2;
					i++;
				} else {
					width *= 2;
					i++;
				}
			} else {
				// 64-127,64 then 64,64-127 then 128-255,128 then 128,128-255 etc.
				if (i % 3 == 0) {
					width++;
					grownPixels++;
					if (width == MathUtils.nextPowerOfTwo(width)) {
						width -= grownPixels;
						grownPixels = 0;
						i++;
					}
				} else if (i % 3 == 1) {
					height++;
					grownPixels++;
					if (height == MathUtils.nextPowerOfTwo(height)) {
						height -= grownPixels;
						grownPixels = 0;
						i++;
					}
				} else {
					if (width == MathUtils.nextPowerOfTwo(width) && height == MathUtils.nextPowerOfTwo(height)) ii++;
					if (ii % 2 == 1)
						width++;
					else
						height++;
					i++;
				}
			}
		}
		if (bestComparator != null) {
			Collections.sort(images, bestComparator);
		} else {
			Collections.sort(images, secondBestComparator);
			bestWidth = secondBestWidth;
			bestHeight = secondBestHeight;
		}
		width = bestWidth;
		height = bestHeight;
		if (settings.pot) {
			width = MathUtils.nextPowerOfTwo(width);
			height = MathUtils.nextPowerOfTwo(height);
		}

		int type;
		switch (format) {
		case RGBA8888:
		case RGBA4444:
			type = BufferedImage.TYPE_INT_ARGB;
			break;
		case RGB565:
			type = BufferedImage.TYPE_INT_RGB;
			break;
		case Alpha:
			type = BufferedImage.TYPE_BYTE_GRAY;
			break;
		default:
			throw new RuntimeException();
		}
		BufferedImage canvas = new BufferedImage(width, height, type);
		insert(canvas, images, bestWidth, bestHeight);
		System.out.println("Writing " + canvas.getWidth() + "x" + canvas.getHeight() + ": " + outputFile);
		ImageIO.write(canvas, "png", outputFile);
		compressedSize += canvas.getWidth() * canvas.getHeight();
	}

	private int insert (BufferedImage canvas, ArrayList<Image> images, int width, int height) throws IOException {
		if (settings.debug && canvas != null) {
			Graphics g = canvas.getGraphics();
			g.setColor(Color.green);
			g.drawRect(0, 0, width - 1, height - 1);
		}
		// Pretend image is larger so padding on right and bottom edges is ignored.
		if (filter.direction != Direction.x && filter.direction != Direction.xy) width += xPadding;
		if (filter.direction != Direction.y && filter.direction != Direction.xy) height += yPadding;
		Node root = new Node(0, 0, width, height);
		int usedPixels = 0;
		for (int i = images.size() - 1; i >= 0; i--) {
			Image image = images.get(i);
			Node node = root.insert(image, canvas, false);
			if (node == null) {
				if (settings.rotate) node = root.insert(image, canvas, true);
				if (node == null) continue;
			}
			usedPixels += image.getWidth() * image.getHeight();
			images.remove(i);
			if (canvas != null) {
				System.out.println("Packing... " + image.file.getName());
				Graphics2D g = (Graphics2D)canvas.getGraphics();
				if (image.rotate) {
					g.translate(node.left, node.top);
					g.rotate(-90 * MathUtils.degreesToRadians);
					g.translate(-node.left, -node.top);
					g.translate(-image.getWidth(), 0);
				}
				g.drawImage(image, node.left, node.top, null);
				if (image.rotate) {
					g.translate(image.getWidth(), 0);
					g.translate(node.left, node.top);
					g.rotate(90 * MathUtils.degreesToRadians);
					g.translate(-node.left, -node.top);
				}
				if (settings.debug) {
					g.setColor(Color.magenta);
					int imageWidth = image.getWidth();
					int imageHeight = image.getHeight();
					if (image.rotate)
						g.drawRect(node.left, node.top, imageHeight - 1, imageWidth - 1);
					else
						g.drawRect(node.left, node.top, imageWidth - 1, imageHeight - 1);
				}
			}
		}
		return images.isEmpty() ? -1 : usedPixels;
	}

	private Image squeeze (File file) throws IOException {
		BufferedImage source = ImageIO.read(file);
		if (source == null) return null;
		if (!filter.accept(source)) return null;
		uncompressedSize += source.getWidth() * source.getHeight();
		WritableRaster alphaRaster = source.getAlphaRaster();
		if (alphaRaster == null) return new Image(file, source, 0, 0, source.getWidth(), source.getHeight());
		final byte[] a = new byte[1];
		int top = 0;
		int bottom = source.getHeight();
		if (filter.direction != Direction.y && filter.direction != Direction.xy) {
			outer:
			for (int y = 0; y < source.getHeight(); y++) {
				for (int x = 0; x < source.getWidth(); x++) {
					alphaRaster.getDataElements(x, y, a);
					int alpha = a[0];
					if (alpha < 0) alpha += 256;
					if (alpha > settings.alphaThreshold) break outer;
				}
				top++;
			}
			outer:
			for (int y = source.getHeight(); --y >= top;) {
				for (int x = 0; x < source.getWidth(); x++) {
					alphaRaster.getDataElements(x, y, a);
					int alpha = a[0];
					if (alpha < 0) alpha += 256;
					if (alpha > settings.alphaThreshold) break outer;
				}
				bottom--;
			}
		}
		int left = 0;
		int right = source.getWidth();
		if (filter.direction != Direction.x && filter.direction != Direction.xy) {
			outer:
			for (int x = 0; x < source.getWidth(); x++) {
				for (int y = top; y <= bottom; y++) {
					alphaRaster.getDataElements(x, y, a);
					int alpha = a[0];
					if (alpha < 0) alpha += 256;
					if (alpha > settings.alphaThreshold) break outer;
				}
				left++;
			}
			outer:
			for (int x = source.getWidth(); --x >= left;) {
				for (int y = top; y <= bottom; y++) {
					alphaRaster.getDataElements(x, y, a);
					int alpha = a[0];
					if (alpha < 0) alpha += 256;
					if (alpha > settings.alphaThreshold) break outer;
				}
				right--;
			}
		}
		int newWidth = right - left;
		int newHeight = bottom - top;
		if (newWidth <= 0 || newHeight <= 0) {
			System.out.println("Ignoring blank input image: " + file.getAbsolutePath());
			return null;
		}
		return new Image(file, source, left, top, newWidth, newHeight);
	}

	private class Node {
		final int left, top, width, height;
		Node child1, child2;
		Image image;

		public Node (int left, int top, int width, int height) {
			this.left = left;
			this.top = top;
			this.width = width;
			this.height = height;
		}

		/**
		 * Returns true if the image was inserted. If canvas != null, an entry is written to the pack file.
		 */
		public Node insert (Image image, BufferedImage canvas, boolean rotate) throws IOException {
			if (this.image != null) return null;
			if (child1 != null) {
				Node newNode = child1.insert(image, canvas, rotate);
				if (newNode != null) return newNode;
				return child2.insert(image, canvas, rotate);
			}
			int imageWidth = image.getWidth();
			int imageHeight = image.getHeight();
			if (rotate) {
				int temp = imageWidth;
				imageWidth = imageHeight;
				imageHeight = temp;
			}
			int neededWidth = imageWidth + xPadding;
			int neededHeight = imageHeight + yPadding;
			if (neededWidth > width || neededHeight > height) return null;
			if (neededWidth == width && neededHeight == height) {
				this.image = image;
				image.rotate = rotate;
				write(canvas);
				return this;
			}
			int dw = width - neededWidth;
			int dh = height - neededHeight;
			if (dw > dh) {
				child1 = new Node(left, top, neededWidth, height);
				child2 = new Node(left + neededWidth, top, width - neededWidth, height);
			} else {
				child1 = new Node(left, top, width, neededHeight);
				child2 = new Node(left, top + neededHeight, width, height - neededHeight);
			}
			return child1.insert(image, canvas, rotate);
		}

		private void write (BufferedImage canvas) throws IOException {
			if (canvas == null) return;

			String imageName = image.file.getAbsolutePath().substring(inputDir.getAbsolutePath().length()) + "\n";
			if (imageName.startsWith("/") || imageName.startsWith("\\")) imageName = imageName.substring(1);
			int dotIndex = imageName.lastIndexOf('.');
			if (dotIndex != -1) imageName = imageName.substring(0, dotIndex);
			imageName = imageName.replace("_" + filter.format, "");
			imageName = imageName.replace("_" + filter.direction, "");
			imageName = imageName.replace("_" + filterToAbbrev.get(filter.minFilter) + "," + filterToAbbrev.get(filter.magFilter),
				"");

			writer.write(imageName.replace("\\", "/") + "\n");
			writer.write("  rotate: " + image.rotate + "\n");
			writer.write("  xy: " + left + ", " + top + "\n");
			writer.write("  size: " + image.getWidth() + ", " + image.getHeight() + "\n");
			writer.write("  orig: " + image.originalWidth + ", " + image.originalHeight + "\n");
			writer.write("  offset: " + image.offsetX + ", " + image.offsetY + "\n");

			Matcher matcher = numberedImagePattern.matcher(imageName);
			if (matcher.matches())
				writer.write("  index: " + Integer.parseInt(matcher.group(1)) + "\n");
			else
				writer.write("  index: 0\n");
		}
	}

	static private class Image extends BufferedImage {
		final File file;
		final int offsetX, offsetY;
		final int originalWidth, originalHeight;
		boolean rotate;

		public Image (File file, BufferedImage src, int left, int top, int newWidth, int newHeight) {
			super(src.getColorModel(), src.getRaster().createWritableChild(left, top, newWidth, newHeight, 0, 0, null), src
				.getColorModel().isAlphaPremultiplied(), null);
			this.file = file;
			offsetX = left;
			offsetY = top;
			originalWidth = src.getWidth();
			originalHeight = src.getHeight();
		}

		public String toString () {
			return file.toString();
		}
	}

	static private ArrayList<Comparator> imageComparators = new ArrayList();
	static {
		imageComparators.add(new Comparator<Image>() {
			public int compare (Image image1, Image image2) {
				int diff = image1.getHeight() - image2.getHeight();
				if (diff != 0) return diff;
				return image1.getWidth() - image2.getWidth();
			}
		});
		imageComparators.add(new Comparator<Image>() {
			public int compare (Image image1, Image image2) {
				int diff = image1.getWidth() - image2.getWidth();
				if (diff != 0) return diff;
				return image1.getHeight() - image2.getHeight();
			}
		});
		imageComparators.add(new Comparator<Image>() {
			public int compare (Image image1, Image image2) {
				return image1.getWidth() * image1.getHeight() - image2.getWidth() * image2.getHeight();
			}
		});
	}

	static private class Filter implements FilenameFilter {
		Direction direction;
		Format format;
		TextureFilter minFilter;
		TextureFilter magFilter;
		int width = -1;
		int height = -1;
		Settings settings;

		public Filter (Settings settings, Direction direction, Format format, int width, int height, TextureFilter minFilter,
			TextureFilter magFilter) {
			this.settings = settings;
			this.direction = direction;
			this.format = format;
			this.width = width;
			this.height = height;
			this.minFilter = minFilter;
			this.magFilter = magFilter;
		}

		public boolean accept (File dir, String name) {
			switch (direction) {
			case none:
				if (name.contains("_x") || name.contains("_y")) return false;
				break;
			case x:
				if (!name.contains("_x") || name.contains("_xy")) return false;
				break;
			case y:
				if (!name.contains("_y") || name.contains("_xy")) return false;
				break;
			case xy:
				if (!name.contains("_xy")) return false;
				break;
			}

			if (format != null) {
				if (!name.contains("_" + formatToAbbrev.get(format))) return false;
			} else {
				// Return if name has a format.
				for (String f : formatToAbbrev.values())
					if (name.contains("_" + f)) return false;
			}

			if (minFilter != null && magFilter != null) {
				if (!name.contains("_" + filterToAbbrev.get(minFilter) + "," + filterToAbbrev.get(magFilter) + ".")
					&& !name.contains("_" + filterToAbbrev.get(minFilter) + "," + filterToAbbrev.get(magFilter) + "_")) return false;
			} else {
				// Return if the name has a filter.
				for (String f : filterToAbbrev.values()) {
					String tag = "_" + f + ",";
					int tagIndex = name.indexOf(tag);
					if (tagIndex != -1) {
						String rest = name.substring(tagIndex + tag.length());
						for (String f2 : filterToAbbrev.values())
							if (rest.startsWith(f2 + ".") || rest.startsWith(f2 + "_")) return false;
					}
				}
			}

			return true;
		}

		public boolean accept (BufferedImage image) {
			if (width != -1 && image.getWidth() != width) return false;
			if (height != -1 && image.getHeight() != height) return false;
			return true;
		}
	}

	static private enum Direction {
		x, y, xy, none
	}

	static final HashMap<TextureFilter, String> filterToAbbrev = new HashMap();
	static {
		filterToAbbrev.put(TextureFilter.Linear, "l");
		filterToAbbrev.put(TextureFilter.Nearest, "n");
		filterToAbbrev.put(TextureFilter.MipMap, "m");
		filterToAbbrev.put(TextureFilter.MipMapLinearLinear, "mll");
		filterToAbbrev.put(TextureFilter.MipMapLinearNearest, "mln");
		filterToAbbrev.put(TextureFilter.MipMapNearestLinear, "mnl");
		filterToAbbrev.put(TextureFilter.MipMapNearestNearest, "mnn");
	}

	static final HashMap<Format, String> formatToAbbrev = new HashMap();
	static {
		formatToAbbrev.put(Format.RGBA8888, "8888");
		formatToAbbrev.put(Format.RGBA4444, "4444");
		formatToAbbrev.put(Format.RGB565, "565");
		formatToAbbrev.put(Format.Alpha, "a");
	}

	static class Settings {
		public Format defaultFormat = Format.RGBA8888;
		public TextureFilter defaultFilterMin = TextureFilter.Linear;
		public TextureFilter defaultFilterMag = TextureFilter.Linear;
		public int alphaThreshold = 9;
		public boolean pot = true;
		public int padding = 0;
		public boolean debug = false;
		public boolean rotate = true;
		public int minWidth = 64;
		public int minHeight = 64;
		public int maxWidth = 1024;
		public int maxHeight = 1024;
	}

	static private void process (Settings settings, File inputDir, File outputDir, File packFile) throws Exception {
		// Clean existing page images.
		if (outputDir.exists()) {
			String prefix = inputDir.getName();
			for (File file : outputDir.listFiles())
				if (file.getName().startsWith(prefix)) file.delete();
		}

		// Just check all combinations, because we are extremely lazy.
		ArrayList<TextureFilter> filters = new ArrayList();
		filters.add(null);
		filters.addAll(Arrays.asList(TextureFilter.values()));
		ArrayList<Format> formats = new ArrayList();
		formats.add(null);
		formats.addAll(Arrays.asList(Format.values()));
		for (int i = 0, n = formats.size(); i < n; i++) {
			Format format = formats.get(i);
			for (int ii = 0, nn = filters.size(); ii < nn; ii++) {
				TextureFilter min = filters.get(ii);
				for (int iii = ii; iii < nn; iii++) {
					TextureFilter mag = filters.get(iii);
					if ((min == null && mag != null) || (min != null && mag == null)) continue;

					Filter filter = new Filter(settings, Direction.none, format, -1, -1, min, mag);
					new SpriteSheetPacker(settings, inputDir, filter, outputDir, packFile);

					for (int width = settings.minWidth; width <= settings.maxWidth; width <<= 1) {
						filter = new Filter(settings, Direction.x, format, width, -1, min, mag);
						new SpriteSheetPacker(settings, inputDir, filter, outputDir, packFile);
					}

					for (int height = settings.minHeight; height <= settings.maxHeight; height <<= 1) {
						filter = new Filter(settings, Direction.y, format, -1, height, min, mag);
						new SpriteSheetPacker(settings, inputDir, filter, outputDir, packFile);
					}

					for (int width = settings.minWidth; width <= settings.maxWidth; width <<= 1) {
						for (int height = settings.minHeight; height <= settings.maxHeight; height <<= 1) {
							filter = new Filter(settings, Direction.xy, format, width, height, min, mag);
							new SpriteSheetPacker(settings, inputDir, filter, outputDir, packFile);
						}
					}
				}
			}
		}

		// Process subdirectories.
		File[] files = inputDir.listFiles();
		if (files == null) return;
		for (File file : files)
			if (file.isDirectory()) process(settings, file, new File(outputDir, file.getName()), packFile);
	}

	static public void process (Settings settings, String input, String output) throws Exception {
		File inputDir = new File(input);
		File outputDir = new File(output);

		if (!inputDir.isDirectory()) {
			System.out.println("Not a directory: " + inputDir);
			return;
		}

		// Clean pack file.
		File packFile = new File(outputDir, "pack");
		packFile.delete();

		process(settings, inputDir, outputDir, packFile);
	}

	public static void main (String[] args) throws Exception {
		String input, output;
		if (args.length != 2) {
			System.out.println("Usage: INPUTDIR OUTPUTDIR");
			return;
		}
		input = args[0];
		output = args[1];
		// input = "c:/temp/pack-in";
		// output = "c:/temp/pack-out";
		process(new Settings(), input, output);
	}
}
