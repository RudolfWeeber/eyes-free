package com.google.marvin.magnify;

public class ImageHelper {
  public static final int ARGB_8888 = 0;
  public static final int A_8 = 1;
  
  /**
   * Converts YUV420 (camera preview format) to ARGB8888 (bitmap format)
   * 
   * @param yuv
   * @param out
   * @param width
   * @param height
   */
  public static void ConvertYUV420(byte[] yuv, int[] out, int format, int width, int height) {
    int pixels = width * height;
    int scanline = width;
    
    int iOut = 0;
    int iY = 0;
    int iC = pixels;
    int Y, Cr, Cb, A, R, G, B;
    int j = 0;

    // Alpha is always 255 << 24
    A = 0xFF << 24;

    while (iY < pixels) {
      /* not necessary if width % 2 == 0
      if (iY == scanline) {
        iC = pixels + ((iY >> 1) / width) * width;
        scanline += width;
      }
      */

      Cr = (yuv[iC++] & 0xFF) - 128;
      Cb = (yuv[iC++] & 0xFF) - 128;

      for (j = 2; j > 0; j--) {
        Y = (((yuv[iY++] & 0xFF) - 16) * 298) >> 8;
        R = Y + ((409 * Cr) >> 8);
        if (R > 0xFF)   R = 0xFF;
        else if (R < 0) R = 0;
        G = Y - ((208 * Cr + 100 * Cb) >> 8);
        if (G > 0xFF)   G = 0xFF;
        else if (G < 0) G = 0;
        B = Y + ((516 * Cb) >> 8);
        if (B > 0xFF)   B = 0xFF;
        else if (B < 0) B = 0;
        switch (format) {
          case ARGB_8888:
            out[iOut++] = A | R << 16 | G << 8 | B;
            break;
        }
      }
    }
  }
}
