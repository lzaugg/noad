package ch.mypi.noad.installer;

import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Manages NodeJS assets install/uninstall.
 */
public class AssetManager {

  private static final String TAG = "AssetManager";
  private static final String ASSET_PATH = "assets/%s/%s.zip"; // 1st: version, 2nd: asset-name

  private final String version;
  private final File installDirectory;
  private final File sourceDirectory;

  public AssetManager(String version, File installDirectory, File sourceDirectory) {
    this.version = version;
    this.installDirectory = installDirectory;
    this.sourceDirectory = sourceDirectory;
  }

  private static void unzip(InputStream zipEntryStream, File destDir) {
    byte[] buffer = new byte[1024];
    try (ZipInputStream zis = new ZipInputStream(zipEntryStream)) {
      ZipEntry zipEntry = zis.getNextEntry();
      while (zipEntry != null) {

        File newFile = new File(destDir, zipEntry.getName());
        Log.i(TAG, "UNZIP, file: " + newFile);
        if (zipEntry.isDirectory()) {
          if (!newFile.isDirectory() && !newFile.mkdirs()) {
            throw new IOException("Failed to create directory " + newFile);
          }
        } else {
          // fix for Windows-created archives
          File parent = newFile.getParentFile();
          if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Failed to create directory " + parent);
          }

          // write file content with auto close
          try (FileOutputStream fos = new FileOutputStream(newFile)) {
            int len;
            while ((len = zis.read(buffer)) > 0) {
              fos.write(buffer, 0, len);
            }
          }
        }
        zipEntry = zis.getNextEntry();
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private InputStream getZipStream(String assetPath) throws IOException {
    ZipFile zip = new ZipFile(sourceDirectory);
    ZipEntry zipEntry = zip.getEntry(assetPath);
    if (zipEntry == null) {
      throw new FileNotFoundException("Asset [" + assetPath + "] not found in zip ['" + sourceDirectory + "']");
    }
    return zip.getInputStream(zipEntry);
  }

  public void install(String assetName) {
    Log.d(TAG, "INSTALLING");
    long startMs = System.currentTimeMillis();
    var assetPath = String.format(ASSET_PATH, version, assetName);
    var assetInstallDirectory = Paths.get(installDirectory.getAbsolutePath(), assetName);
    try {
      try (var stream = getZipStream(assetPath)) {
        unzip(stream, assetInstallDirectory.toFile());
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot install asset " + assetName, e);
    }
    Log.i(TAG, "INSTALLED, took " + (System.currentTimeMillis() - startMs) + "ms");
  }

  public void uninstall(String assetName) {
    Log.d(TAG, "UNINSTALLING");
    var assetInstallDirectory = Paths.get(installDirectory.getAbsolutePath(), assetName);
    var assetInstallDirectoryAsFile = assetInstallDirectory.toFile();
    long startMs = System.currentTimeMillis();
    if (assetInstallDirectoryAsFile.exists()) {
      try {
        Files.walk(assetInstallDirectory)
          .sorted(Comparator.reverseOrder())
          .map(Path::toFile)
          .forEach(File::delete);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
    long timeElapsedMs = System.currentTimeMillis() - startMs;
    Log.i(TAG, "UNINSTALLED, took " + timeElapsedMs + "ms");
  }
}
