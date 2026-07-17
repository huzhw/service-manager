package com.servicemanager.ui;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用图标生成工具
 * <p>
 * 纯 Java2D 绘制，不依赖外部图片资源。输出多尺寸图标供窗口任务栏和系统托盘使用。
 */
public class AppIcon {

    /** 品牌色 */
    private static final Color BRAND_BLUE = new Color(0x21, 0x96, 0xF3);
    private static final Color BRAND_DARK = new Color(0x15, 0x65, 0xC0);
    private static final Color BRAND_LIGHT = new Color(0x64, 0xB5, 0xF6);

    /**
     * 生成 16×16 托盘图标
     */
    public static Image createTrayIcon() {
        return createIcon(16);
    }

    /**
     * 生成窗口图标列表（含多种尺寸，Windows 任务栏自动选择合适大小）
     */
    public static List<Image> createWindowIcons() {
        List<Image> list = new ArrayList<>();
        list.add(createIcon(16));
        list.add(createIcon(32));
        list.add(createIcon(64));
        list.add(createIcon(128));
        return list;
    }

    /**
     * 绘制指定尺寸的图标
     * <p>
     * 设计：圆角方形底板 + 渐变蓝色 + 中心白色图形（小尺寸为简化圆点，大尺寸为齿轮状四叶片）
     */
    private static Image createIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int pad = Math.max(1, size / 16);       // 外边距
        int arc = Math.max(2, size / 4);         // 圆角半径

        // 底板：圆角方形 + 从上到下渐变
        GradientPaint bgGradient = new GradientPaint(
                0, 0, BRAND_LIGHT,
                0, size, BRAND_DARK);
        g.setPaint(bgGradient);
        g.fill(new RoundRectangle2D.Float(pad, pad, size - pad * 2, size - pad * 2, arc, arc));

        // 中心图形
        g.setColor(Color.WHITE);
        int cx = size / 2;
        int cy = size / 2;

        if (size <= 16) {
            // 小尺寸：白色圆点 + 环
            int r = size / 4;
            g.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        } else if (size <= 32) {
            // 中尺寸：十字四叶片（简化的齿轮/服务器）
            int bladeW = size / 5;
            int bladeH = size / 4;
            int gap = size / 10;
            // 水平叶片
            g.fillRoundRect(cx - bladeH, cy - bladeW / 2, bladeH * 2, bladeW, arc / 2, arc / 2);
            // 垂直叶片
            g.fillRoundRect(cx - bladeW / 2, cy - bladeH, bladeW, bladeH * 2, arc / 2, arc / 2);
            // 中心圆
            int dotR = Math.max(2, size / 12);
            g.fill(new Ellipse2D.Float(cx - dotR, cy - dotR, dotR * 2, dotR * 2));
        } else {
            // 大尺寸：齿轮状四叶片 + 中心圆 + 外环
            int bladeW = size / 6;
            int bladeH = size / 3;
            int gap = size / 8;
            // 水平叶片
            g.fillRoundRect(cx - bladeH, cy - bladeW / 2, bladeH * 2, bladeW, size / 8, size / 8);
            // 垂直叶片
            g.fillRoundRect(cx - bladeW / 2, cy - bladeH, bladeW, bladeH * 2, size / 8, size / 8);

            // 中心实心圆
            int dotR = size / 10;
            g.fill(new Ellipse2D.Float(cx - dotR, cy - dotR, dotR * 2, dotR * 2));

            // 外层细环
            g.setStroke(new BasicStroke(Math.max(1f, size / 32f)));
            int ringR = size / 2 - pad * 2;
            g.draw(new Ellipse2D.Float(cx - ringR, cy - ringR, ringR * 2, ringR * 2));
        }

        g.dispose();
        return img;
    }
}
