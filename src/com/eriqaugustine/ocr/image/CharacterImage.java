package com.eriqaugustine.ocr.image;

import com.eriqaugustine.ocr.utils.CharacterUtils;
import com.eriqaugustine.ocr.utils.ImageUtils;
import com.eriqaugustine.ocr.utils.MathUtils;

import java.awt.Dimension;

import magick.MagickImage;

/**
 * Namespace for images that contain a single character.
 */
public class CharacterImage {
   public static final int DEFAULT_POINT_SIZE = 2;
   public static final double DEFAULT_POINT_DENSITY = 0.75;

   /**
    * Break up the character into strokes.
    */
   // TODO: Return strokes.
   public static void getStrokes(MagickImage image) throws Exception {
      Dimension dimensions = image.getDimension();

      byte[] pixels = Filters.averageChannels(Filters.bwPixels(image, 200), 3);
      System.out.println(ImageUtils.asciiImage(pixels, dimensions.width, 1) + "\n");

      boolean[] points = discretizeLines(pixels, dimensions.width);
      System.out.println(ImageUtils.asciiImage(points, dimensions.width / DEFAULT_POINT_SIZE) + "\n");

      /*
      boolean[] outline = getOutline(pixels, dimensions.width);
      System.out.println(ImageUtils.asciiImage(outline, dimensions.width) + "\n");
      */

      /*
      boolean[] points = boxizeLines(pixels, dimensions.width, 2, 1);
      System.out.println(ImageUtils.asciiImage(points, dimensions.width) + "\n");
      */

      // TODO(eriq).
   }

   /**
    * Does the same as discretizeLines(), except the resulting image is the same size.
    */
   public static boolean[] boxizeLines(byte[] pixels, int imageWidth,
                                       int pointSize, double pointDensity) {
      boolean[] points = new boolean[pixels.length];

      for (int row = 0; row < pixels.length / imageWidth; row++) {
         for (int col = 0; col < imageWidth; col++) {
            int pixelCount = 0;

            for (int pointRowOffset = 0; pointRowOffset < pointSize; pointRowOffset++) {
               for (int pointColOffset = 0; pointColOffset < pointSize; pointColOffset++) {
                  int index = MathUtils.rowColToIndex(row + pointRowOffset,
                                                      col + pointColOffset,
                                                      imageWidth);

                  if (index < pixels.length && pixels[index] == 0) {
                     pixelCount++;
                  }
               }
            }

            if (pixelCount / (double)(pointSize * pointSize) >= pointDensity) {
               points[MathUtils.rowColToIndex(row, col, imageWidth)] = true;
            }
         }
      }

      return points;
   }

   public static boolean[] boxizeLines(byte[] pixels, int imageWidth) {
      return boxizeLines(pixels, imageWidth, DEFAULT_POINT_SIZE, DEFAULT_POINT_DENSITY);
   }

   /**
    * Get the outlines for an image, only border pixels willbe shown.
    * |pixels| is assumed to be bw.
    */
   public static boolean[] getOutline(byte[] pixels, int imageWidth) {
      boolean[] outline = new boolean[pixels.length];
      int[][] neighborOffsets = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};

      for (int row = 0; row < pixels.length / imageWidth; row++) {
         for (int col = 0; col < imageWidth; col++) {
            int index = MathUtils.rowColToIndex(row, col, imageWidth);
            boolean border = false;

            // Only consider occupied pixels.
            if ((pixels[index] & 0xFF) == 0xFF) {
               continue;
            }

            // Any pixel touching an edge is automatically a border.
            if (row == 0 || row == pixels.length / imageWidth - 1 ||
                col == 0 || col == imageWidth - 1) {
               border = true;
            } else {
               for (int[] neighborOffset : neighborOffsets) {
                  int newRow = row + neighborOffset[0];
                  int newCol = col + neighborOffset[1];
                  int newIndex = MathUtils.rowColToIndex(newRow, newCol, imageWidth);

                  // Not enough to check index bounds because it could be on vertical edge.
                  // If the pixel touches any whitespace, it is a border.
                  if (newRow >= 0 && newRow < pixels.length / imageWidth &&
                      newCol >= 0 && newCol < imageWidth &&
                      (pixels[newIndex] & 0xFF) == 0xFF) {
                     border = true;
                     break;
                  }
               }
            }

            if (border) {
               outline[index] = true;
            }
         }
      }

      return outline;
   }

   /**
    * Turns |pixels| into a more defined set of points.
    * A point can be a single pixel, or a box of pixels.
    * Assumes that |pixels| is bw.
    * |pointSize| is the length of one of the sides of the box.
    * The resulting image will be
    * (|imageWidth| / |pointSize|) x (|pixels|.length / |imageWidth| / |pointSize|)
    */
   public static boolean[] discretizeLines(byte[] pixels, int imageWidth,
                                           int pointSize, double pointDensity) {
      int newWidth = imageWidth / pointSize;
      int newHeight = pixels.length / imageWidth / pointSize;

      boolean[] points = new boolean[newWidth * newHeight];

      for (int row = 0; row < newHeight; row++) {
         for (int col = 0; col < newWidth; col++) {
            int pixelCount = 0;

            for (int pointRowOffset = 0; pointRowOffset < pointSize; pointRowOffset++) {
               for (int pointColOffset = 0; pointColOffset < pointSize; pointColOffset++) {
                  int index = MathUtils.rowColToIndex((row * pointSize) + pointRowOffset,
                                                      (col * pointSize) + pointColOffset,
                                                      imageWidth);

                  if (index < pixels.length && pixels[index] == 0) {
                     pixelCount++;
                  }
               }
            }

            if (pixelCount / (double)(pointSize * pointSize) >= pointDensity) {
               points[MathUtils.rowColToIndex(row, col, newWidth)] = true;
            }
         }
      }

      return points;
   }

   public static boolean[] discretizeLines(byte[] pixels, int imageWidth) {
      return discretizeLines(pixels, imageWidth, DEFAULT_POINT_SIZE, DEFAULT_POINT_DENSITY);
   }

   /**
    * Get the distance (DISsimilarity) bewteen two density maps.
    * Distance is currently measured using MSE.
    */
   public static double densityMapDistance(double[][] a, double[][] b) {
      assert(a.length == b.length);

      double sumSquareError = 0;
      int count = 0;

      for (int i = 0; i < a.length; i++) {
         assert(a[i].length == b[i].length);

         for (int j = 0; j < a[i].length; j++) {
            sumSquareError += Math.pow(a[i][j] - b[i][j], 2);
            count++;
         }
      }

      return sumSquareError / count;
   }

   /**
    * Get the density of the different regions of the character.
    * Note: Because pixels are atomic, some pixels on the right and bottom edges may be lost.
    *  The alternative to losing pixels would be to have uneven regions.
    */
   public static double[][] getDensityMap(MagickImage image,
                                          int rows, int cols,
                                          int whiteThreshold) throws Exception {
      assert(rows > 0 && cols > 0);

      double[][] densityMap = new double[rows][cols];

      Dimension dimensions = image.getDimension();
      byte[] pixels = Filters.averageChannels(Filters.bwPixels(image), 3);

      int rowDelta = dimensions.height / rows;
      int colDelta = dimensions.width / cols;

      if (rowDelta == 0 || colDelta == 0) {
         return null;
      }

      for (int row = 0; row < rows; row++) {
         for (int col = 0; col < cols; col++) {
            densityMap[row][col] =
               ImageUtils.density(pixels, dimensions.width,
                                  row * rowDelta, rowDelta,
                                  col * colDelta, colDelta,
                                  whiteThreshold);
         }
      }

      return densityMap;
   }

   public static double[][] getDensityMap(MagickImage image,
                                          int rows, int cols) throws Exception {
      return getDensityMap(image, rows, cols, ImageUtils.DEFAULT_WHITE_THRESHOLD);
   }

   /**
    * Generate an image for every character in the string.
    * The index of the entry represents the character associated with it.
    */
   public static MagickImage[] generateFontImages(String characters) throws Exception {
      MagickImage[] images = new MagickImage[characters.length()];

      for (int i = 0; i < characters.length(); i++) {
         images[i] = CharacterUtils.generateCharacter(characters.charAt(i),
                                                      true);
      }

      return images;
   }

   /**
    * Get the density maps for the output of generateFontImages().
    */
   public static double[][][] getFontDensityMaps(String characters,
                                                 int mapRows,
                                                 int mapCols) throws Exception {
      MagickImage[] characterImages = generateFontImages(characters);
      double[][][] maps = new double[characterImages.length][][];

      for (int i = 0; i < characterImages.length; i++) {
         maps[i] = getDensityMap(characterImages[i], mapRows, mapCols);
      }

      return maps;
   }
}
