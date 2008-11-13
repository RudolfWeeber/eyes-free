package com.google.marvin.androidsays;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.ZipFile;



public class Unzipper {

  public static String download(String fileUrl) {
    URLConnection cn;
    try {
      fileUrl = (new URL(new URL(fileUrl), fileUrl)).toString();
      URL url = new URL(fileUrl);
      cn = url.openConnection();
      cn.connect();
      InputStream stream = cn.getInputStream();

      File outputDir = new File("/sdcard/mem/memes/");
      outputDir.mkdirs();
      String filename = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
      filename = filename.substring(0, filename.indexOf("meme.zip") + 8);

      File outputFile = new File("/sdcard/mem/memes/", filename);
      outputFile.createNewFile();
      FileOutputStream out = new FileOutputStream(outputFile);

      byte buf[] = new byte[16384];
      do {
        int numread = stream.read(buf);
        if (numread <= 0) {
          break;
        } else {
          out.write(buf, 0, numread);
        }
      } while (true);

      stream.close();
      out.close();
      return "/sdcard/mem/memes/" + filename;

    } catch (MalformedURLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return "";
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return "";
    }
  }

  public static void unzip(String fileUrl) {
    try {
      String filename = download(fileUrl);
      ZipFile zip = new ZipFile(filename);
      Enumeration<? extends ZipEntry> zippedFiles = zip.entries();
      while (zippedFiles.hasMoreElements()) {
        ZipEntry entry = zippedFiles.nextElement();
        InputStream is = zip.getInputStream(entry);
        String name = entry.getName();
        File outputFile = new File("/sdcard/mem/themes/" + name);
        String outputPath = outputFile.getCanonicalPath();
        name = outputPath.substring(outputPath.lastIndexOf("/") + 1);
        outputPath = outputPath.substring(0, outputPath.lastIndexOf("/"));
        File outputDir = new File(outputPath);
        outputDir.mkdirs();
        outputFile = new File(outputPath, name);
        outputFile.createNewFile();
        FileOutputStream out = new FileOutputStream(outputFile);

        byte buf[] = new byte[16384];
        do {
          int numread = is.read(buf);
          if (numread <= 0) {
            break;
          } else {
            out.write(buf, 0, numread);
          }
        } while (true);

        is.close();
        out.close();
      }
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
}
