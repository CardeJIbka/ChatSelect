package com.cardejibka.client;

import com.cardejibka.client.mixin.ChatComponentAccessor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public final class ChatSelection {

    private static final int PADDING = 4;
    private static final long MULTI_CLICK_MS = 500;
    private static final int HIGHLIGHT = 0x883399FF;

    private static long ibeamCursor;

    public record Hit(GuiMessage.Line line, int ch, int under) {}

    public record Press(double x, double y, int button) {}

    private GuiMessage.Line anchorLine;
    private GuiMessage.Line headLine;
    private int anchorCh;
    private int headCh;

    private boolean dragging;
    private boolean moved;
    private Press press;
    private int scrollTimer;
    private boolean ibeamActive;

    private long lastClickTime;
    private double lastClickX;
    private double lastClickY;
    private int clickCount;

    public void start(Hit hit, double x, double y, int button) {
        anchorLine = headLine = hit.line();
        anchorCh = headCh = hit.ch();
        dragging = true;
        moved = false;
        press = new Press(x, y, button);
    }

    public void clear() {
        anchorLine = headLine = null;
        anchorCh = headCh = 0;
        dragging = false;
        moved = false;
        press = null;
    }

    public boolean hasSelection() {
        return anchorLine != null && headLine != null
                && (anchorLine != headLine || anchorCh != headCh);
    }

    public int registerClick(double x, double y) {
        long now = System.currentTimeMillis();
        boolean sameSpot = Math.abs(x - lastClickX) <= 4 && Math.abs(y - lastClickY) <= 4;

        if (clickCount > 0 && sameSpot && now - lastClickTime <= MULTI_CLICK_MS) {
            clickCount = Math.min(clickCount + 1, 3);
        } else {
            clickCount = 1;
        }

        lastClickTime = now;
        lastClickX = x;
        lastClickY = y;
        return clickCount;
    }

    public void selectAll(Minecraft mc) {
        List<GuiMessage.Line> lines = lines(mc);
        if (lines.isEmpty()) {
            return;
        }
        anchorLine = lines.get(lines.size() - 1);
        anchorCh = 0;
        headLine = lines.get(0);
        headCh = Glyphs.of(headLine.content()).size();
        dragging = false;
    }

    public void selectWord(Hit hit) {
        Glyphs glyphs = Glyphs.of(hit.line().content());
        if (glyphs.size() == 0) {
            return;
        }

        int i = Mth.clamp(hit.under(), 0, glyphs.size() - 1);
        int kind = glyphs.kind(i);
        int start = i;
        int end = i + 1;
        while (start > 0 && glyphs.kind(start - 1) == kind) {
            start--;
        }
        while (end < glyphs.size() && glyphs.kind(end) == kind) {
            end++;
        }
        if (kind != 0) {
            while (end < glyphs.size() && glyphs.kind(end) == 0) {
                end++;
            }
        }

        anchorLine = headLine = hit.line();
        anchorCh = start;
        headCh = end;
        dragging = false;
    }

    public void selectMessage(Minecraft mc, Hit hit) {
        List<GuiMessage.Line> lines = lines(mc);
        int index = indexOf(lines, hit.line());
        if (index < 0) {
            return;
        }

        int newest = index;
        while (newest > 0 && !lines.get(newest).endOfEntry()) {
            newest--;
        }
        int oldest = index;
        while (oldest + 1 < lines.size() && !lines.get(oldest + 1).endOfEntry()) {
            oldest++;
        }

        anchorLine = lines.get(oldest);
        anchorCh = 0;
        headLine = lines.get(newest);
        headCh = Glyphs.of(headLine.content()).size();
        dragging = false;
    }

    public Press update(Minecraft mc, int mouseX, int mouseY) {
        if (!dragging) {
            return null;
        }

        if (isLeftDown()) {
            Hit hit = hit(mc, mouseX, mouseY, true);
            if (hit != null) {
                headLine = hit.line();
                headCh = hit.ch();
                if (headLine != anchorLine || headCh != anchorCh) {
                    moved = true;
                }
            }
            scrollWhileDragging(mc, mouseY);
            return null;
        }

        dragging = false;
        if (!moved) {
            Press click = press;
            clear();
            return click;
        }
        press = null;
        return null;
    }

    public void updateCursor(Minecraft mc, int mouseX, int mouseY, boolean overInput) {
        boolean wanted = dragging || overInput || isOverText(mc, mouseX, mouseY);
        if (wanted == ibeamActive) {
            return;
        }
        ibeamActive = wanted;

        long window = mc.getWindow().getWindow();
        if (wanted) {
            if (ibeamCursor == 0L) {
                ibeamCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR);
            }
            GLFW.glfwSetCursor(window, ibeamCursor);
        } else {
            GLFW.glfwSetCursor(window, 0L);
        }
    }

    public void resetCursor(Minecraft mc) {
        if (ibeamActive) {
            ibeamActive = false;
            GLFW.glfwSetCursor(mc.getWindow().getWindow(), 0L);
        }
    }

    public void render(GuiGraphics graphics, Minecraft mc) {
        Range range = range(mc);
        if (range == null) {
            return;
        }
        Layout layout = layout(mc);

        for (int i = range.from; i >= range.to; i--) {
            int row = i - layout.scroll;
            if (row < 0 || row >= layout.rows) {
                continue;
            }

            Glyphs glyphs = Glyphs.of(range.lines.get(i).content());
            int start = i == range.from ? range.fromCh : 0;
            int end = i == range.to ? range.toCh : glyphs.size();
            if (start >= end) {
                continue;
            }

            float scale = (float) layout.scale;
            int x1 = Math.round((PADDING + mc.font.width(glyphs.prefix(start))) * scale);
            int x2 = Math.round((PADDING + mc.font.width(glyphs.prefix(end))) * scale);
            int y1 = Math.round((layout.bottom - (row + 1) * layout.lineHeight) * scale);
            int y2 = Math.round((layout.bottom - row * layout.lineHeight) * scale);
            graphics.fill(RenderPipelines.GUI_TEXT_HIGHLIGHT, x1, y1, x2, y2, 0xFF0000FF);
        }
    }

    public String getText(Minecraft mc) {
        Range range = range(mc);
        if (range == null) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        for (int i = range.from; i >= range.to; i--) {
            GuiMessage.Line line = range.lines.get(i);
            Glyphs glyphs = Glyphs.of(line.content());
            int start = i == range.from ? range.fromCh : 0;
            int end = i == range.to ? range.toCh : glyphs.size();
            text.append(glyphs.substring(start, end));

            if (i != range.to) {
                text.append(line.endOfEntry() ? '\n' : ' ');
            }
        }
        return text.toString();
    }

    public Hit hit(Minecraft mc, double mouseX, double mouseY, boolean clamp) {
        Layout layout = layout(mc);
        if (layout.rows <= 0) {
            return null;
        }

        double x = mouseX / layout.scale - PADDING;
        double y = mouseY / layout.scale;
        int row = (int) Math.floor((layout.bottom - y) / layout.lineHeight);
        boolean tooHigh = row >= layout.rows;
        boolean tooLow = row < 0;

        if (!clamp) {
            double maxX = ChatComponent.getWidth(mc.options.chatWidth().get()) / layout.scale;
            if (tooHigh || tooLow || x < -PADDING || x > maxX) {
                return null;
            }
        }

        row = Mth.clamp(row, 0, layout.rows - 1);
        GuiMessage.Line line = layout.lines.get(row + layout.scroll);
        Glyphs glyphs = Glyphs.of(line.content());

        int ch;
        int under;
        if (clamp && tooHigh) {
            ch = 0;
            under = 0;
        } else if (clamp && tooLow) {
            ch = glyphs.size();
            under = Math.max(0, glyphs.size() - 1);
        } else {
            ch = charAt(mc.font, glyphs, x);
            under = charUnder(mc.font, glyphs, x);
        }
        return new Hit(line, ch, under);
    }

    private boolean isOverText(Minecraft mc, int mouseX, int mouseY) {
        Hit hit = hit(mc, mouseX, mouseY, false);
        if (hit == null) {
            return false;
        }
        double x = mouseX / mc.options.chatScale().get() - PADDING;
        return x <= mc.font.width(hit.line().content());
    }

    public static boolean isLeftDown() {
        long window = GLFW.glfwGetCurrentContext();
        return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    public static int[] wordRange(String text, int index) {
        if (text.isEmpty()) {
            return new int[]{0, 0};
        }

        int i = Mth.clamp(index, 0, text.length() - 1);
        int kind = kindOf(text.charAt(i));
        int start = i;
        int end = i + 1;
        while (start > 0 && kindOf(text.charAt(start - 1)) == kind) {
            start--;
        }
        while (end < text.length() && kindOf(text.charAt(end)) == kind) {
            end++;
        }
        if (kind != 0) {
            while (end < text.length() && kindOf(text.charAt(end)) == 0) {
                end++;
            }
        }
        return new int[]{start, end};
    }

    private static int kindOf(int cp) {
        if (Character.isWhitespace(cp)) {
            return 0;
        }
        return Character.isLetterOrDigit(cp) || cp == '_' ? 1 : 2;
    }

    private static int charAt(Font font, Glyphs glyphs, double x) {
        if (x <= 0) {
            return 0;
        }
        int previous = 0;
        for (int i = 1; i <= glyphs.size(); i++) {
            int width = font.width(glyphs.prefix(i));
            if (x < width) {
                return x - previous < width - x ? i - 1 : i;
            }
            previous = width;
        }
        return glyphs.size();
    }

    private static int charUnder(Font font, Glyphs glyphs, double x) {
        if (x <= 0) {
            return 0;
        }
        for (int i = 0; i < glyphs.size(); i++) {
            if (x < font.width(glyphs.prefix(i + 1))) {
                return i;
            }
        }
        return Math.max(0, glyphs.size() - 1);
    }

    private void scrollWhileDragging(Minecraft mc, int mouseY) {
        if (++scrollTimer % 3 != 0) {
            return;
        }
        Layout layout = layout(mc);
        double top = (layout.bottom - layout.rows * layout.lineHeight) * layout.scale;
        double bottom = layout.bottom * layout.scale;

        if (mouseY < top) {
            chat(mc).scrollChat(1);
        } else if (mouseY > bottom) {
            chat(mc).scrollChat(-1);
        }
    }

    private record Layout(List<GuiMessage.Line> lines, double scale, int lineHeight,
                          int bottom, int scroll, int rows) {}

    private static ChatComponent chat(Minecraft mc) {
        return mc.gui.getChat();
    }

    private static List<GuiMessage.Line> lines(Minecraft mc) {
        return ((ChatComponentAccessor) chat(mc)).chatselect$lines();
    }

    private static Layout layout(Minecraft mc) {
        ChatComponentAccessor accessor = (ChatComponentAccessor) chat(mc);
        List<GuiMessage.Line> lines = accessor.chatselect$lines();

        double scale = mc.options.chatScale().get();
        int lineHeight = (int) (9.0 * (mc.options.chatLineSpacing().get() + 1.0));
        int bottom = Mth.floor((mc.getWindow().getGuiScaledHeight() - 40) / scale);
        int scroll = accessor.chatselect$scroll();

        int perPage = ChatComponent.getHeight(mc.options.chatHeightFocused().get()) / lineHeight;
        int rows = Math.max(0, Math.min(lines.size() - scroll, perPage));
        return new Layout(lines, scale, lineHeight, bottom, scroll, rows);
    }

    private record Range(List<GuiMessage.Line> lines, int from, int fromCh, int to, int toCh) {}

    private Range range(Minecraft mc) {
        if (!hasSelection()) {
            return null;
        }
        List<GuiMessage.Line> lines = lines(mc);
        int a = indexOf(lines, anchorLine);
        int h = indexOf(lines, headLine);
        if (a < 0 || h < 0) {
            clear();
            return null;
        }

        if (a > h) {
            return new Range(lines, a, anchorCh, h, headCh);
        }
        if (a < h) {
            return new Range(lines, h, headCh, a, anchorCh);
        }
        return new Range(lines, a, Math.min(anchorCh, headCh), a, Math.max(anchorCh, headCh));
    }

    private static int indexOf(List<GuiMessage.Line> lines, GuiMessage.Line line) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i) == line) {
                return i;
            }
        }
        return -1;
    }

    private static final class Glyphs {
        private final IntArrayList codePoints = new IntArrayList();
        private final List<Style> styles = new ArrayList<>();

        static Glyphs of(FormattedCharSequence sequence) {
            Glyphs glyphs = new Glyphs();
            sequence.accept((index, style, codePoint) -> {
                glyphs.codePoints.add(codePoint);
                glyphs.styles.add(style);
                return true;
            });
            return glyphs;
        }

        int size() {
            return codePoints.size();
        }

        int kind(int index) {
            int cp = codePoints.getInt(index);
            if (Character.isWhitespace(cp)) {
                return 0;
            }
            return Character.isLetterOrDigit(cp) || cp == '_' ? 1 : 2;
        }

        FormattedCharSequence prefix(int length) {
            return sink -> {
                for (int i = 0; i < length; i++) {
                    if (!sink.accept(i, styles.get(i), codePoints.getInt(i))) {
                        return false;
                    }
                }
                return true;
            };
        }

        String substring(int start, int end) {
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                sb.appendCodePoint(codePoints.getInt(i));
            }
            return sb.toString();
        }
    }
}