package dev.patra.identity.infra.account.avatar;

import com.luciad.imageio.webp.WebPImageWriterSpi;
import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/// 头像测试用的造图与读图工具：图片内容是三条竖带（左 1/4 红、中间绿、右 1/4 蓝），裁方后只剩绿色，
/// 便于断言居中裁方；另有往 JPEG / PNG 里塞元数据、只有文件头的「解压炸弹」、列出 WebP 块的方法。
public final class AvatarImageFixture {

  private AvatarImageFixture() {}

  /// 竖带图编码成 JPEG。
  ///
  /// @param width 宽
  /// @param height 高
  /// @return 字节
  public static byte[] jpeg(int width, int height) {
    return encode(stripes(width, height, BufferedImage.TYPE_INT_RGB), "jpg");
  }

  /// 编码成 PNG：不透明时是竖带图；透明时是全透明背景加中间一块不透明的绿色方块。
  ///
  /// @param width 宽
  /// @param height 高
  /// @param alpha 是否带透明
  /// @return 字节
  public static byte[] png(int width, int height, boolean alpha) {
    if (!alpha) {
      return encode(stripes(width, height, BufferedImage.TYPE_INT_RGB), "png");
    }
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    g.setColor(Color.GREEN);
    g.fillRect(width / 4, height / 4, width / 2, height / 2);
    g.dispose();
    return encode(image, "png");
  }

  /// 纯灰图：灰度 PNG（可带 alpha，全不透明）或灰度 JPEG，每个像素都是同一个灰度值。
  ///
  /// @param width 宽
  /// @param height 高
  /// @param value 灰度值 0–255
  /// @param format `png`、`png-alpha` 或 `jpg`
  /// @return 字节
  public static byte[] gray(int width, int height, int value, String format) {
    if (format.equals("png-alpha")) {
      ComponentColorModel model =
          new ComponentColorModel(
              ColorSpace.getInstance(ColorSpace.CS_GRAY),
              true,
              false,
              Transparency.TRANSLUCENT,
              DataBuffer.TYPE_BYTE);
      WritableRaster raster = model.createCompatibleWritableRaster(width, height);
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          raster.setSample(x, y, 0, value);
          raster.setSample(x, y, 1, 255);
        }
      }
      return encode(new BufferedImage(model, raster, false, null), "png");
    }
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
    WritableRaster raster = image.getRaster();
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        raster.setSample(x, y, 0, value);
      }
    }
    return encode(image, format.equals("jpg") ? "jpg" : "png");
  }

  /// 半透明的单色灰度 PNG（灰度 + alpha 两个通道）。
  ///
  /// @param width 宽
  /// @param height 高
  /// @param value 灰度值
  /// @param alpha 不透明度 0 ~ 255
  /// @return 字节
  public static byte[] grayWithAlpha(int width, int height, int value, int alpha) {
    ComponentColorModel model =
        new ComponentColorModel(
            ColorSpace.getInstance(ColorSpace.CS_GRAY),
            true,
            false,
            Transparency.TRANSLUCENT,
            DataBuffer.TYPE_BYTE);
    WritableRaster raster = model.createCompatibleWritableRaster(width, height);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        raster.setSample(x, y, 0, value);
        raster.setSample(x, y, 1, alpha);
      }
    }
    return encode(new BufferedImage(model, raster, false, null), "png");
  }

  /// 竖带图编码成有损 WebP。
  ///
  /// @param width 宽
  /// @param height 高
  /// @return 字节
  public static byte[] webp(int width, int height) {
    ImageWriter writer = null;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
      writer = new WebPImageWriterSpi().createWriterInstance();
      writer.setOutput(stream);
      writer.write(new IIOImage(stripes(width, height, BufferedImage.TYPE_INT_RGB), null, null));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      if (writer != null) {
        writer.dispose();
      }
    }
    return out.toByteArray();
  }

  /// 在 JPEG 的 SOI 之后插一个含 GPS IFD 的 `APP1 Exif` 段。
  ///
  /// @param jpeg 原 JPEG
  /// @return 带 EXIF GPS 的 JPEG
  public static byte[] withExifGps(byte[] jpeg) {
    ByteBuffer tiff = ByteBuffer.allocate(44).order(ByteOrder.BIG_ENDIAN);
    tiff.put("MM".getBytes(StandardCharsets.US_ASCII)).putShort((short) 42).putInt(8);
    // IFD0：一条 GPSInfo 指针（tag 0x8825, LONG, 1, 指向偏移 26）
    tiff.putShort((short) 1).putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(26);
    tiff.putInt(0);
    // GPS IFD：一条 GPSLatitudeRef（tag 0x0001, ASCII, 2, "N\0"）
    tiff.putShort((short) 1).putShort((short) 0x0001).putShort((short) 2).putInt(2);
    tiff.put((byte) 'N').put((byte) 0).putShort((short) 0);
    tiff.putInt(0);
    byte[] exif = concat("Exif\0\0".getBytes(StandardCharsets.US_ASCII), tiff.array());
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(jpeg, 0, 2);
    out.write(0xFF);
    out.write(0xE1);
    out.writeBytes(ByteBuffer.allocate(2).putShort((short) (exif.length + 2)).array());
    out.writeBytes(exif);
    out.write(jpeg, 2, jpeg.length - 2);
    return out.toByteArray();
  }

  /// 在 PNG 的 `IHDR` 之后插 `iCCP`（sRGB 配置文件）与 `eXIf` 块。
  ///
  /// @param png 原 PNG
  /// @return 带元数据的 PNG
  public static byte[] withPngMetadata(byte[] png) {
    int afterIhdr = 8 + 4 + 4 + 13 + 4;
    byte[] icc = ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();
    byte[] iccp = concat("sRGB\0\0".getBytes(StandardCharsets.US_ASCII), deflate(icc));
    byte[] exif =
        concat(
            "MM".getBytes(StandardCharsets.US_ASCII),
            new byte[] {0, 42, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0});
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(png, 0, afterIhdr);
    out.writeBytes(pngChunk("iCCP", iccp));
    out.writeBytes(pngChunk("eXIf", exif));
    out.write(png, afterIhdr, png.length - afterIhdr);
    return out.toByteArray();
  }

  /// 只有文件头声明了尺寸的 PNG：`IDAT` 是一小段压缩的零字节，解码会失败，但读头能拿到尺寸。
  ///
  /// @param width 声明的宽
  /// @param height 声明的高
  /// @return 字节
  public static byte[] pngHeaderOnly(int width, int height) {
    ByteBuffer ihdr = ByteBuffer.allocate(13);
    ihdr.putInt(width).putInt(height).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0);
    ihdr.put((byte) 0);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
    out.writeBytes(pngChunk("IHDR", ihdr.array()));
    out.writeBytes(pngChunk("IDAT", deflate(new byte[4096])));
    out.writeBytes(pngChunk("IEND", new byte[0]));
    return out.toByteArray();
  }

  /// 列出 WebP 文件里的块类型（跳过 `RIFF` 头）。
  ///
  /// @param webp WebP 字节
  /// @return 块类型，按出现顺序
  public static List<String> riffChunks(byte[] webp) {
    List<String> types = new ArrayList<>();
    ByteBuffer buffer = ByteBuffer.wrap(webp).order(ByteOrder.LITTLE_ENDIAN);
    int offset = 12;
    while (offset + 8 <= webp.length) {
      types.add(new String(webp, offset, 4, StandardCharsets.US_ASCII));
      int length = buffer.getInt(offset + 4);
      offset += 8 + length + (length & 1);
    }
    return types;
  }

  /// 用 TwelveMonkeys（纯 Java）解码 WebP。
  ///
  /// @param webp WebP 字节
  /// @return 图片
  public static BufferedImage decodeWebp(byte[] webp) {
    try (MemoryCacheImageInputStream in =
        new MemoryCacheImageInputStream(new ByteArrayInputStream(webp))) {
      ImageReader reader = new WebPImageReaderSpi().createReaderInstance();
      try {
        reader.setInput(in);
        return reader.read(0);
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /// 三条竖带：左 1/4 红、中间绿、右 1/4 蓝。
  ///
  /// @param width 宽
  /// @param height 高
  /// @param type 图片类型
  /// @return 图片
  private static BufferedImage stripes(int width, int height, int type) {
    BufferedImage image = new BufferedImage(width, height, type);
    Graphics2D g = image.createGraphics();
    g.setColor(Color.GREEN);
    g.fillRect(0, 0, width, height);
    g.setColor(Color.RED);
    g.fillRect(0, 0, width / 4, height);
    g.setColor(Color.BLUE);
    g.fillRect(width - width / 4, 0, width / 4, height);
    g.dispose();
    return image;
  }

  /// 用 JDK 自带的写出器编码。
  ///
  /// @param image 图片
  /// @param format `jpg` 或 `png`
  /// @return 字节
  private static byte[] encode(BufferedImage image, String format) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      if (!ImageIO.write(image, format, out)) {
        throw new IllegalStateException("没有 " + format + " 写出器");
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  /// 一个 PNG 块：长度 + 类型 + 数据 + CRC32（覆盖类型与数据）。
  ///
  /// @param type 类型
  /// @param data 数据
  /// @return 块
  private static byte[] pngChunk(String type, byte[] data) {
    byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
    CRC32 crc = new CRC32();
    crc.update(typeBytes);
    crc.update(data);
    ByteBuffer chunk = ByteBuffer.allocate(12 + data.length);
    chunk.putInt(data.length).put(typeBytes).put(data).putInt((int) crc.getValue());
    return chunk.array();
  }

  /// zlib 压缩。
  ///
  /// @param data 原始字节
  /// @return 压缩后的字节
  private static byte[] deflate(byte[] data) {
    Deflater deflater = new Deflater();
    deflater.setInput(data);
    deflater.finish();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    while (!deflater.finished()) {
      out.write(buffer, 0, deflater.deflate(buffer));
    }
    deflater.end();
    return out.toByteArray();
  }

  /// 拼两段字节。
  ///
  /// @param a 前段
  /// @param b 后段
  /// @return 拼接结果
  private static byte[] concat(byte[] a, byte[] b) {
    byte[] result = new byte[a.length + b.length];
    System.arraycopy(a, 0, result, 0, a.length);
    System.arraycopy(b, 0, result, a.length, b.length);
    return result;
  }
}
