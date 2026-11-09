import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/** 一次性工具：logo 裁掉 8% 边缘（去水印痕迹）并缩放到 128px，保留透明通道 */
public class LogoCrop {
    public static void main(String[] args) throws Exception {
        File src = new File(args[0]);
        File dst = new File(args[1]);
        BufferedImage in = ImageIO.read(src);
        int crop = Math.round(in.getWidth() * 0.08f);
        int w = in.getWidth() - 2 * crop;
        BufferedImage out = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(in, 0, 0, 128, 128, crop, crop, crop + w, crop + w, null);
        g.dispose();
        ImageIO.write(out, "png", dst);
        System.out.println("logo.png written: " + dst.getAbsolutePath());
    }
}
