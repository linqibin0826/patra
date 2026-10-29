package dev.patra.identity.infra.account.avatar;

import com.luciad.imageio.webp.WebPImageWriterSpi;
import dev.patra.identity.domain.account.exception.InvalidAvatarException;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarRejection;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarRenditions;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarSize;
import dev.patra.identity.domain.account.policy.AvatarPolicy;
import dev.patra.identity.domain.account.port.avatar.AvatarImagePort;
import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;

/// 头像出图：魔数识别格式 → 读头校验尺寸与像素 → 居中取方并按区域加下采样解码（内存封顶）→ 转 sRGB →
/// 逐次减半再双三次缩到 512，128 从 512 出 → WebP 有损编码。
///
/// 解码只用纯 Java（TwelveMonkeys 的 JPEG / WebP、JDK 的 PNG），不可信输入不进原生代码；编码用 libwebp 的
/// JNI 包装，只吃自己缩放后的像素。读取器从 ImageIO 注册表里按类名前缀挑（TwelveMonkeys 的 JPEG 读取器要经
/// 注册才拿得到 JDK 的委托）：注册表里两个 WebP 读取器并存，原生的那个不能用来解码上传；Boot 胖 jar 里插件在
/// 嵌套 jar 中，构造时 `scanForPlugins()` 一次（同 catalog 的 `ImageIoDimensionAdapter`）。写出器直接从 SPI 实例化。
///
/// 编码器只拿到像素，输出里不会有 EXIF / XMP / ICC；非 sRGB 的图先转成 sRGB，所以丢掉 ICC 不丢颜色。
@Component
public class AvatarImageAdapter implements AvatarImagePort {

  /// 下采样后解码出来的方图至少保留这么大，再逐次减半缩到 512。
  private static final int DECODE_TARGET = 1024;

  /// 最多同时出几张图：WebP 与渐进式 JPEG 解码时整张图进内存，并发上传不能把堆占满。
  private static final int MAX_CONCURRENT_RENDERS = 2;

  /// 等出图名额最多等多久，等不到回 503。
  private static final Duration RENDER_WAIT = Duration.ofSeconds(10);

  private final Semaphore renderSlots;
  private final Duration renderWait;

  /// 默认名额与等待时间；扫一次 ImageIO 插件（Boot 胖 jar 里 SPI 在嵌套 jar 中，默认注册表看不到）。
  public AvatarImageAdapter() {
    this(new Semaphore(MAX_CONCURRENT_RENDERS), RENDER_WAIT);
  }

  /// 指定名额与等待时间（测试用）。
  ///
  /// @param renderSlots 出图名额
  /// @param renderWait 等名额的上限
  AvatarImageAdapter(Semaphore renderSlots, Duration renderWait) {
    this.renderSlots = renderSlots;
    this.renderWait = renderWait;
    ImageIO.scanForPlugins();
  }

  /// 出图。
  ///
  /// @param upload 原图字节
  /// @return 两档 WebP
  /// @throws InvalidAvatarException 类型不对、太小、像素过多或读不出
  /// @throws TemporarilyUnavailableException 出图名额等不到
  @Override
  public AvatarRenditions render(byte[] upload) {
    AvatarFormat format = AvatarFormatSniffer.sniff(upload);
    acquireSlot();
    try {
      BufferedImage square = decodeSquare(upload, format);
      BufferedImage large = scale(square, AvatarSize.LARGE.getPixels());
      BufferedImage small = scale(large, AvatarSize.SMALL.getPixels());
      return AvatarRenditions.of(encode(large), encode(small));
    } finally {
      renderSlots.release();
    }
  }

  /// 等一个出图名额。
  ///
  /// @throws TemporarilyUnavailableException 超时或被中断
  private void acquireSlot() {
    try {
      if (!renderSlots.tryAcquire(renderWait.toMillis(), TimeUnit.MILLISECONDS)) {
        throw new TemporarilyUnavailableException();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TemporarilyUnavailableException(e);
    }
  }

  /// 读头校验后，按居中的正方形区域解码，边长大于 2048 时整数倍下采样。解码有任何告警都算读不出。
  ///
  /// @param upload 原图字节
  /// @param format 格式
  /// @return sRGB 的方图，带不带 alpha 随源图
  private static BufferedImage decodeSquare(byte[] upload, AvatarFormat format) {
    ImageReader reader = readerFor(format);
    try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(upload))) {
      reader.setInput(in, true, true);
      int width;
      int height;
      try {
        width = reader.getWidth(0);
        height = reader.getHeight(0);
      } catch (IOException | RuntimeException e) {
        throw new InvalidAvatarException(AvatarRejection.UNREADABLE);
      }
      int side = Math.min(width, height);
      if (side < AvatarPolicy.MIN_SHORT_SIDE) {
        throw new InvalidAvatarException(AvatarRejection.TOO_SMALL);
      }
      if ((long) width * height > AvatarPolicy.MAX_PIXELS) {
        throw new InvalidAvatarException(AvatarRejection.TOO_MANY_PIXELS);
      }
      ImageReadParam param = reader.getDefaultReadParam();
      param.setSourceRegion(new Rectangle((width - side) / 2, (height - side) / 2, side, side));
      int step = Math.max(1, side / DECODE_TARGET);
      param.setSourceSubsampling(step, step, 0, 0);
      List<String> warnings = new ArrayList<>();
      reader.addIIOReadWarningListener((source, warning) -> warnings.add(warning));
      BufferedImage decoded;
      try {
        decoded = reader.read(0, param);
      } catch (IOException | RuntimeException e) {
        throw new InvalidAvatarException(AvatarRejection.UNREADABLE);
      }
      if (!warnings.isEmpty()) {
        // 截断、数据段损坏时解码器只告警并返回半张图，按读不出处理
        throw new InvalidAvatarException(AvatarRejection.UNREADABLE);
      }
      return toSrgb(decoded);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      reader.dispose();
    }
  }

  /// 按格式取纯 Java 的读取器。
  ///
  /// @param format 格式
  /// @return 读取器
  private static ImageReader readerFor(AvatarFormat format) {
    return switch (format) {
      case JPEG -> registeredReader("jpeg", "com.twelvemonkeys.");
      case WEBP -> registeredReader("webp", "com.twelvemonkeys.");
      case PNG -> registeredReader("png", "com.sun.imageio.");
    };
  }

  /// 从注册表里按格式名与类名前缀挑一个读取器。
  ///
  /// @param formatName 格式名
  /// @param classPrefix 读取器类名前缀
  /// @return 读取器
  /// @throws IllegalStateException 类路径上没有这个读取器（依赖缺失）
  private static ImageReader registeredReader(String formatName, String classPrefix) {
    Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(formatName);
    while (readers.hasNext()) {
      ImageReader reader = readers.next();
      if (reader.getClass().getName().startsWith(classPrefix)) {
        return reader;
      }
      reader.dispose();
    }
    throw new IllegalStateException("找不到 " + classPrefix + " 的 " + formatName + " 读取器");
  }

  /// 转成 sRGB 的 `TYPE_INT_ARGB_PRE`（有 alpha）或 `TYPE_INT_RGB`，后面缩放都在这两种类型上做。
  ///
  /// @param source 解码结果
  /// @return sRGB 图
  private static BufferedImage toSrgb(BufferedImage source) {
    boolean alpha = source.getColorModel().hasAlpha();
    BufferedImage target =
        new BufferedImage(
            source.getWidth(),
            source.getHeight(),
            alpha ? BufferedImage.TYPE_INT_ARGB_PRE : BufferedImage.TYPE_INT_RGB);
    ColorSpace colorSpace = source.getColorModel().getColorSpace();
    if (colorSpace.getType() == ColorSpace.TYPE_GRAY) {
      copyGray(source, target);
    } else if (colorSpace.isCS_sRGB()) {
      draw(source, target, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    } else {
      new ColorConvertOp(null).filter(source, target);
    }
    return target;
  }

  /// 灰度图逐像素复制成 r = g = b。解码器给灰度图的色彩空间是线性的 `CS_GRAY`，但文件里的灰度值实际按
  /// sRGB 编码；交给 `ColorConvertOp` 或 `drawImage` 会当线性值做一次 gamma 提升，中灰 128 变成 189。
  ///
  /// @param source 灰度图（可带 alpha，可 16 位）
  /// @param target 同尺寸的 `TYPE_INT_RGB` 或 `TYPE_INT_ARGB_PRE`
  private static void copyGray(BufferedImage source, BufferedImage target) {
    Raster raster = source.getRaster();
    ColorModel model = source.getColorModel();
    int grayShift = Math.max(0, model.getComponentSize(0) - 8);
    boolean alpha = model.hasAlpha();
    int alphaShift = alpha ? Math.max(0, model.getComponentSize(1) - 8) : 0;
    for (int y = 0; y < source.getHeight(); y++) {
      for (int x = 0; x < source.getWidth(); x++) {
        int gray = raster.getSample(x, y, 0) >> grayShift;
        int a = alpha ? raster.getSample(x, y, 1) >> alphaShift : 0xFF;
        // setRGB 收的是非预乘值，写进 TYPE_INT_ARGB_PRE 时它自己会预乘，这里不能再乘一次
        target.setRGB(x, y, a << 24 | gray << 16 | gray << 8 | gray);
      }
    }
  }

  /// 缩放到 `size × size`：比目标大一倍以上时先逐次双线性减半，最后一步双三次。
  ///
  /// @param source 方图
  /// @param size 目标边长
  /// @return 缩放后的图
  private static BufferedImage scale(BufferedImage source, int size) {
    BufferedImage current = source;
    while (current.getWidth() > 2 * size) {
      current =
          resize(current, current.getWidth() / 2, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }
    return resize(current, size, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
  }

  /// 按给定插值缩放成方图，类型不变。
  ///
  /// @param source 方图
  /// @param size 边长
  /// @param interpolation 插值方式
  /// @return 缩放后的图
  private static BufferedImage resize(BufferedImage source, int size, Object interpolation) {
    BufferedImage target = new BufferedImage(size, size, source.getType());
    draw(source, target, interpolation);
    return target;
  }

  /// 把源图铺满目标图（覆盖而不是叠加，保留透明）。
  ///
  /// @param source 源图
  /// @param target 目标图
  /// @param interpolation 插值方式
  private static void draw(BufferedImage source, BufferedImage target, Object interpolation) {
    Graphics2D g = target.createGraphics();
    try {
      g.setComposite(AlphaComposite.Src);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.drawImage(source, 0, 0, target.getWidth(), target.getHeight(), null);
    } finally {
      g.dispose();
    }
  }

  /// WebP 有损编码；有 alpha 时先转成非预乘的 `TYPE_INT_ARGB`。
  ///
  /// @param image 方图
  /// @return WebP 字节
  private static byte[] encode(BufferedImage image) {
    BufferedImage pixels = image;
    if (image.getType() == BufferedImage.TYPE_INT_ARGB_PRE) {
      pixels = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
      draw(image, pixels, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageWriter writer = null;
    try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
      writer = new WebPImageWriterSpi().createWriterInstance();
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionType("Lossy");
      param.setCompressionQuality(AvatarPolicy.WEBP_QUALITY);
      writer.setOutput(stream);
      writer.write(null, new IIOImage(pixels, null, null), param);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      if (writer != null) {
        writer.dispose();
      }
    }
    return out.toByteArray();
  }
}
