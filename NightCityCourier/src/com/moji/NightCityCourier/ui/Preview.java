package com.moji.NightCityCourier.ui;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 界面预览生成器。
 *
 * <p>因为界面全部是自绘的，不需要显示设备也能把「真实布局」渲染成 PNG：
 * 布局参数、字体、颜色与运行时完全一致，只把数据换成一份演示快照。
 *
 * <p>用法：{@code java -Djava.awt.headless=true -cp out com.moji.NightCityCourier.ui.Preview [输出目录]}
 */
public final class Preview {

    private Preview() {
    }

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "preview");
        Files.createDirectories(out);

        Screens.Model model = new Screens.Model();
        int w = 1280;
        int h = 800;

        write(out, "01-dashboard.png", Screens.render(w, h, g -> Screens.dashboard(g, w, h, model)));
        write(out, "02-growth.png", Screens.render(w, h, g -> Screens.growth(g, w, h)));
        write(out, "03-achievements.png", Screens.render(w, h, g -> Screens.achievements(g, w, h)));

        System.out.println("预览已生成到 " + out.toAbsolutePath());
        try (var s = Files.list(out)) {
            s.sorted().forEach(p -> System.out.println("  " + p.getFileName()));
        }
    }

    private static void write(Path dir, String name, java.awt.image.BufferedImage img) throws IOException {
        ImageIO.write(img, "png", dir.resolve(name).toFile());
    }
}
