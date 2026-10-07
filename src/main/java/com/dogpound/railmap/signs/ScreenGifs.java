package com.dogpound.railmap.signs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Animated GIFs for the train screens. Drop any .gif into config/irextras/screens/ and every Pride Rail screen
 * plays it (each train picks one, they loop forever, real frame timing). Built-in: Pride + trans flag waves.
 */
public final class ScreenGifs {
    private static final class Gif {
        final List<ResourceLocation> frames = new ArrayList<>();
        final List<Integer> delays = new ArrayList<>();
        int total;
        float aspect = 2f;
    }

    private static List<Gif> gifs;

    private ScreenGifs() {}

    static boolean any() {
        load();
        return !gifs.isEmpty();
    }

    private static void load() {
        if (gifs != null) return;
        gifs = new ArrayList<>();
        for (String n : new String[]{"pride_wave", "trans_wave"}) {
            try (InputStream in = ScreenGifs.class.getResourceAsStream("/assets/irextras/screens/" + n + ".gif")) {
                if (in != null) add(ImageIO.createImageInputStream(in), n);
            } catch (Exception ignored) {
            }
        }
        File dir = new File(Minecraft.getMinecraft().gameDir, "config/irextras/screens");
        dir.mkdirs();
        File[] files = dir.listFiles((d, f) -> f.toLowerCase(java.util.Locale.ROOT).endsWith(".gif"));
        if (files != null) for (File f : files) {
            try (ImageInputStream in = ImageIO.createImageInputStream(f)) {
                add(in, "user_" + f.getName().replaceAll("[^a-z0-9_]", "_"));
            } catch (Exception ex) {
                com.dogpound.railmap.RailMap.LOG.warn("[IR Extras] screen gif {}: {}", f.getName(), ex.toString());
            }
        }
    }

    /** decode every frame (composited onto the previous one, as GIFs are drawn) into its own texture */
    private static void add(ImageInputStream in, String name) throws Exception {
        Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("gif");
        if (!it.hasNext()) return;
        ImageReader r = it.next();
        r.setInput(in);
        int n = Math.min(r.getNumImages(true), 240);
        Gif g = new Gif();
        BufferedImage canvas = null;
        for (int i = 0; i < n; i++) {
            BufferedImage fr = r.read(i);
            IIOMetadata md = r.getImageMetadata(i);
            int delay = 100, left = 0, top = 0;
            Node root = md.getAsTree("javax_imageio_gif_image_1.0");
            for (Node c = root.getFirstChild(); c != null; c = c.getNextSibling()) {
                NamedNodeMap a = c.getAttributes();
                if (c.getNodeName().equals("GraphicControlExtension")) delay = Math.max(20, Integer.parseInt(a.getNamedItem("delayTime").getNodeValue()) * 10);
                if (c.getNodeName().equals("ImageDescriptor")) {
                    left = Integer.parseInt(a.getNamedItem("imageLeftPosition").getNodeValue());
                    top = Integer.parseInt(a.getNamedItem("imageTopPosition").getNodeValue());
                }
            }
            if (canvas == null) canvas = new BufferedImage(Math.max(fr.getWidth(), r.getWidth(0)), Math.max(fr.getHeight(), r.getHeight(0)), BufferedImage.TYPE_INT_ARGB);
            Graphics2D gr = canvas.createGraphics();
            gr.drawImage(fr, left, top, null);
            gr.dispose();
            BufferedImage copy = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
            copy.getGraphics().drawImage(canvas, 0, 0, null);
            g.frames.add(Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("irextras_gif_" + name + "_" + i, new DynamicTexture(copy)));
            g.delays.add(delay);
            g.total += delay;
            g.aspect = canvas.getWidth() / (float) canvas.getHeight();
        }
        if (!g.frames.isEmpty()) gifs.add(g);
    }

    static void draw(float w, float h, long now, int seed) {
        load();
        if (gifs.isEmpty()) return;
        Gif g = gifs.get(Math.floorMod(seed, gifs.size()));
        long t = now % Math.max(1, g.total);
        int i = 0;
        for (int acc = 0; i < g.delays.size(); i++) {
            acc += g.delays.get(i);
            if (t < acc) break;
        }
        i = Math.min(i, g.frames.size() - 1);
        Minecraft.getMinecraft().getTextureManager().bindTexture(g.frames.get(i));
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
        // letterbox the gif into the screen
        float gw = w, gh = w / g.aspect;
        if (gh > h) { gh = h; gw = h * g.aspect; }
        float x0 = (w - gw) / 2, y0 = (h - gh) / 2;
        SignRender.quad(x0, y0, x0 + gw, y0 + gh);
    }
}
