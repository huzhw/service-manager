package com.servicemanager.ui;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用图标 — 优先加载 tools/app-icon.png（新设计：深色底+渐变齿轮+运行灯），
 * 文件缺失时回退内置 Java2D 绘制（齿轮+盾牌风格）
 */
public class AppIcon {

    /** 外部图标候选路径（相对工作目录，start.bat 已 cd 到项目根） */
    private static final String[] PNG_CANDIDATES = {
            "tools/app-icon.png",
            "app-icon.png",
    };

    private static Image externalCache;

    /**
     * 加载外部图标文件；未命中返回 null
     */
    private static synchronized Image loadExternal() {
        if (externalCache != null) {
            return externalCache;
        }
        for (String path : PNG_CANDIDATES) {
            File f = new File(path);
            if (f.isFile()) {
                try {
                    BufferedImage img = ImageIO.read(f);
                    if (img != null) {
                        externalCache = img;
                        break;
                    }
                } catch (IOException ignored) {
                    // 换下一个候选路径 / 回退内置绘制
                }
            }
        }
        return externalCache;
    }

    private static final Color BG_START  = new Color(0x42, 0xA5, 0xF5);  // Material Blue 400
    private static final Color BG_END    = new Color(0x1E, 0x88, 0xE5);  // Material Blue 600
    private static final Color ACCENT    = new Color(0x15, 0x65, 0xC0);  // Material Blue 800
    private static final Color WHITE     = new Color(0xFF, 0xFF, 0xFF);
    private static final Color SHADOW    = new Color(0, 0, 0, 30);

    public static Image createTrayIcon() {
        Image ext = loadExternal();
        if (ext instanceof BufferedImage) {
            return scaled((BufferedImage) ext, 16);
        }
        return createIcon(16);
    }

    public static List<Image> createWindowIcons() {
        List<Image> list = new ArrayList<>();
        Image ext = loadExternal();
        if (ext instanceof BufferedImage) {
            for (int size : new int[]{16, 32, 48, 64, 256}) {
                // 必须返回 BufferedImage：App.start 会对每项强转 (BufferedImage)
                list.add(scaled((BufferedImage) ext, size));
            }
            return list;
        }
        list.add(createIcon(16));
        list.add(createIcon(32));
        list.add(createIcon(64));
        list.add(createIcon(128));
        return list;
    }

    /**
     * 等比缩放到指定尺寸并返回 BufferedImage（不能返回 ToolkitImage，
     * 否则调用方强转 BufferedImage 会 ClassCastException 崩机）
     */
    private static BufferedImage scaled(BufferedImage src, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return out;
    }

    private static Image createIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int pad = Math.max(1, size / 12);
        int r = size / 2 - pad;

        // 圆角方形背景 + 渐变 + 阴影
        Shape bg = new RoundRectangle2D.Float(pad, pad, size - pad * 2, size - pad * 2, size / 4, size / 4);
        GradientPaint bgPaint = new GradientPaint(0, 0, BG_START, size, size, BG_END);
        g.setPaint(bgPaint);
        g.fill(bg);

        // 齿轮
        g.setColor(WHITE);
        int cx = size / 2, cy = size / 2;

        if (size <= 16) {
            // 16px: 简化齿轮 — 圆+十字
            int dotR = size / 6;
            g.fillOval(cx - dotR, cy - dotR, dotR * 2, dotR * 2);
            int armW = Math.max(1, size / 12);
            int armL = Math.max(1, size / 3);
            g.fillRect(cx - armW / 2, cy - armL, armW, armL * 2);
            g.fillRect(cx - armL, cy - armW / 2, armL * 2, armW);
        } else {
            drawGear(g, cx, cy, r, size);
        }

        g.dispose();
        return img;
    }

    /** 绘制齿轮：中心圆 + N 个齿 */
    private static void drawGear(Graphics2D g, int cx, int cy, int outerR, int size) {
        int teeth = size <= 32 ? 6 : 8;
        int innerR = (int) (outerR * 0.55);    // 内圈半径
        int toothW = (int) (outerR * 0.22);    // 齿宽
        int toothH = (int) (outerR * 0.35);    // 齿高（伸出长度）

        // 构建齿轮路径
        GeneralPath gear = new GeneralPath();
        for (int i = 0; i < teeth; i++) {
            double angle = Math.PI * 2 * i / teeth - Math.PI / 2;
            double halfAngle = Math.PI / teeth;

            // 齿的两侧角度
            double a1 = angle - halfAngle * 0.3;
            double a2 = angle + halfAngle * 0.3;

            // 齿的四个点：外顶点 → 右内 → 右外 → 内
            double tx1 = cx + (outerR - toothH + toothH) * Math.cos(a1);  // 齿左外
            double ty1 = cy + (outerR - toothH + toothH) * Math.sin(a1);
            double tx2 = cx + (outerR - toothH + toothH) * Math.cos(a2);  // 齿右外
            double ty2 = cy + (outerR - toothH + toothH) * Math.sin(a2);

            // 内圈对应角度
            double ia1 = angle - halfAngle * 0.8;
            double ia2 = angle + halfAngle * 0.8;
            double ix1 = cx + innerR * Math.cos(ia1);
            double iy1 = cy + innerR * Math.sin(ia1);
            double ix2 = cx + innerR * Math.cos(ia2);
            double iy2 = cy + innerR * Math.sin(ia2);

            if (i == 0) {
                gear.moveTo(tx1, ty1);
            }
            gear.lineTo(tx2, ty2);
            gear.lineTo(ix2, iy2);
            gear.lineTo(ix1, iy1);
            gear.closePath();
        }

        // 外圈
        Area outer = new Area(new Ellipse2D.Float(cx - outerR, cy - outerR, outerR * 2, outerR * 2));
        // 内圈（挖空）
        Area inner = new Area(new Ellipse2D.Float(cx - innerR, cy - innerR, innerR * 2, innerR * 2));
        outer.subtract(inner);

        // 齿区只保留在圆环上的部分
        Area teethArea = new Area(gear);
        Area ring = new Area(new Ellipse2D.Float(cx - outerR, cy - outerR, outerR * 2, outerR * 2));
        Area innerHole = new Area(new Ellipse2D.Float(
                cx - innerR + toothH / 2, cy - innerR + toothH / 2,
                (innerR - toothH / 2) * 2, (innerR - toothH / 2) * 2));
        ring.subtract(innerHole);
        teethArea.intersect(ring);

        // 绘制齿轮外环 + 齿
        g.fill(outer);
        g.fill(teethArea);

        // 中心圆（小圆点）
        int dotR = Math.max(2, outerR / 5);
        g.setColor(new Color(0x21, 0x96, 0xF3));  // 用品牌蓝色
        g.fillOval(cx - dotR, cy - dotR, dotR * 2, dotR * 2);
    }
}
