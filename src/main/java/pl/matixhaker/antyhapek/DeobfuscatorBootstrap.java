package pl.matixhaker.antyhapek;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class DeobfuscatorBootstrap {
  private static final DeobfuscationTransformer TRANSFORMER = new DeobfuscationTransformer();

  public static void main(String[] args) throws Throwable {
    if (args.length != 2) {
      System.out.println("uzycie: (input jar) (output jar)");
      return;
    } else {
      File input = new File(args[0]);
      File output = new File(args[1]);

      if (!input.exists()) System.out.println("nie znaleziono pliku " + args[0]);
      if (!output.exists()) output.createNewFile();

      ZipFile file = new ZipFile(input);

      Enumeration<? extends ZipEntry> enumeration = file.entries();

      try (ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(output))) {
        while (enumeration.hasMoreElements()) {
          ZipEntry entry = enumeration.nextElement();
          outputStream.putNextEntry(new ZipEntry(entry.getName()));

          byte[] data = readInputStream(file.getInputStream(entry));
          if (data.length > 8
              && data[0] == (byte) 0xCA
              && data[1] == (byte) 0xFE
              && data[2] == (byte) 0xBA & data[3] == (byte) 0xBE) {
            ClassReader reader = new ClassReader(data);

            ClassNode node = new ClassNode();

            try {
              reader.accept(node, ClassReader.EXPAND_FRAMES);
            } catch (Throwable throwable) {
              System.out.println("Znaleziono huja: " + node.name);
              continue;
            }

            TRANSFORMER.transform(node);

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);

            data = writer.toByteArray();
          }
          outputStream.write(data);

          outputStream.closeEntry();
        }
      }
    }

    System.out.println("Odkodowalo sie!");
  }

  private static byte[] readInputStream(InputStream stream) throws IOException {
    try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

      int nRead;
      byte[] data = new byte[16384];

      while ((nRead = stream.read(data, 0, data.length)) != -1) {
        buffer.write(data, 0, nRead);
      }

      return buffer.toByteArray();
    }
  }
}
