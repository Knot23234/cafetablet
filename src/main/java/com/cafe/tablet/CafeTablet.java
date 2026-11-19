package com.cafe.tablet;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.*;

import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

@Mod("cafetablet")
public class CafeTablet {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "cafetablet");
    public static final RegistryObject<Item> TABLET = ITEMS.register("tablet", () -> new TabletItem(new Item.Properties().stacksTo(1)));

    public CafeTablet() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) e.accept(TABLET);
        });
        Data.load();
    }

    /** Sağ tık -> tablet ekranı */
    public static class TabletItem extends Item {
        public TabletItem(Properties p) { super(p); }
        @Override
        public InteractionResultHolder<ItemStack> use(Level lvl, Player pl, net.minecraft.world.InteractionHand h) {
            if (lvl.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Minecraft.getInstance().setScreen(new TabletScreen()));
            return InteractionResultHolder.sidedSuccess(pl.getItemInHand(h), lvl.isClientSide);
        }
    }

    public static class Order { String customer; List<String> lines = new ArrayList<>(); String time; }

    /** Veri + sipariş üretici (ekran kapalıyken de çalışır) */
    @Mod.EventBusSubscriber(modid = "cafetablet", value = Dist.CLIENT)
    public static class Data {
        static List<String> customers = new ArrayList<>(List.of("Süleyman Abi", "Ayşe Hanım"));
        static List<String> foods = new ArrayList<>(List.of("Dürüm", "Karpuzlu İçecek", "Ayran"));
        static List<Order> pending = new ArrayList<>(), history = new ArrayList<>();
        static int min = 10, max = 20, ticks = 200;
        static final Random R = new Random();
        static final Gson G = new GsonBuilder().setPrettyPrinting().create();
        static Path file() { return FMLPaths.CONFIGDIR.get().resolve("cafetablet.json"); }

        static void load() {
            try {
                if (!Files.exists(file())) return;
                JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
                customers = G.fromJson(o.get("customers"), new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());
                foods = G.fromJson(o.get("foods"), new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());
                history = G.fromJson(o.get("history"), new com.google.gson.reflect.TypeToken<List<Order>>(){}.getType());
                min = o.get("min").getAsInt(); max = o.get("max").getAsInt();
            } catch (Exception ignored) {}
        }
        static void save() {
            try {
                JsonObject o = new JsonObject();
                o.add("customers", G.toJsonTree(customers)); o.add("foods", G.toJsonTree(foods));
                o.add("history", G.toJsonTree(history)); o.addProperty("min", min); o.addProperty("max", max);
                Files.writeString(file(), G.toJson(o));
            } catch (Exception ignored) {}
        }
        static void generate() {
            if (customers.isEmpty() || foods.isEmpty()) return;
            Order o = new Order();
            o.customer = customers.get(R.nextInt(customers.size()));
            int n = 1 + R.nextInt(Math.min(2, foods.size()));
            List<String> pool = new ArrayList<>(foods); Collections.shuffle(pool, R);
            for (int i = 0; i < n; i++) o.lines.add((1 + R.nextInt(20)) + "x " + pool.get(i));
            pending.add(o);
        }
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END || Minecraft.getInstance().player == null) return;
            if (--ticks <= 0) { generate(); ticks = (min + R.nextInt(Math.max(1, max - min + 1))) * 20; }
        }
    }

    /** Tarayıcı tarzı arayüz: üst menü, sol liste, orta kart, sağ akış */
    public static class TabletScreen extends Screen {
        static final int W = 400, H = 220;
        int tab = 0, sel = 0, L, T;
        EditBox cBox, fBox, minBox, maxBox;
        public TabletScreen() { super(Component.literal("Kafe Tablet")); }
        @Override protected void init() { build(); }

        void build() {
            clearWidgets();
            L = (width - W) / 2; T = (height - H) / 2;
            sel = Math.min(sel, Math.max(0, Data.pending.size() - 1));
            String[] names = {"Siparişler", "Geçmiş", "Ayarlar"};
            for (int i = 0; i < 3; i++) { final int t = i;
                addRenderableWidget(Button.builder(Component.literal(names[i]), b -> { tab = t; build(); }).bounds(L + 70 + i * 66, T + 4, 62, 16).build()); }
            if (tab == 0 && !Data.pending.isEmpty())
                addRenderableWidget(Button.builder(Component.literal("\u2714 Teslim edildi"), b -> {
                    Order o = Data.pending.remove(sel);
                    o.time = new SimpleDateFormat("HH:mm:ss").format(new Date());
                    Data.history.add(o); Data.save(); build();
                }).bounds(L + 120, T + H - 30, 160, 20).build());
            if (tab == 2) {
                int x = L + 120;
                cBox = new EditBox(font, x, T + 52, 110, 18, Component.literal("Kişi"));
                fBox = new EditBox(font, x, T + 92, 110, 18, Component.literal("Yemek"));
                minBox = new EditBox(font, x, T + 132, 40, 18, Component.literal("min")); minBox.setValue("" + Data.min);
                maxBox = new EditBox(font, x + 46, T + 132, 40, 18, Component.literal("max")); maxBox.setValue("" + Data.max);
                addRenderableWidget(cBox); addRenderableWidget(fBox); addRenderableWidget(minBox); addRenderableWidget(maxBox);
                addRenderableWidget(Button.builder(Component.literal("+ Kişi"), b -> add(Data.customers, cBox)).bounds(x + 114, T + 51, 52, 20).build());
                addRenderableWidget(Button.builder(Component.literal("- Kişi"), b -> rm(Data.customers)).bounds(x + 168, T + 51, 52, 20).build());
                addRenderableWidget(Button.builder(Component.literal("+ Yemek"), b -> add(Data.foods, fBox)).bounds(x + 114, T + 91, 52, 20).build());
                addRenderableWidget(Button.builder(Component.literal("- Yemek"), b -> rm(Data.foods)).bounds(x + 168, T + 91, 52, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Kaydet"), b -> {
                    try { int a = Math.max(1, Integer.parseInt(minBox.getValue())), c = Math.max(a, Integer.parseInt(maxBox.getValue()));
                        Data.min = a; Data.max = c; Data.save(); } catch (NumberFormatException ignored) {}
                }).bounds(x + 92, T + 131, 60, 20).build());
            }
        }
        void add(List<String> l, EditBox b) { String s = b.getValue().trim(); if (!s.isEmpty()) { l.add(s); b.setValue(""); Data.save(); } }
        void rm(List<String> l) { if (!l.isEmpty()) { l.remove(l.size() - 1); Data.save(); } }

        void avatar(GuiGraphics g, String n, int x, int y) {
            g.fill(x, y, x + 18, y + 18, 0xFF000000 | ((n.hashCode() * 0x9E3779B1) & 0x7F7F7F) | 0x303030);
            g.drawString(font, n.substring(0, 1), x + 6, y + 5, 0xFFFFFFFF);
        }
        String cut(String s, int w) { return font.plainSubstrByWidth(s, w); }

        @Override public boolean mouseClicked(double mx, double my, int btn) {
            if (tab == 0 && mx >= L && mx < L + 110)
                for (int i = 0; i < Math.min(8, Data.pending.size()); i++)
                    if (my >= T + 38 + i * 22 && my < T + 58 + i * 22) { sel = i; build(); return true; }
            return super.mouseClicked(mx, my, btn);
        }

        @Override public void render(GuiGraphics g, int mx, int my, float pt) {
            renderBackground(g);
            g.fill(L, T, L + W, T + H, 0xFFFFFFFF);                  // gövde
            g.fill(L, T, L + W, T + 24, 0xFFFFFFFF);                 // üst çubuk
            g.fill(L, T + 24, L + W, T + 25, 0xFFE3E3E8);
            g.drawString(font, "\u2615 Kafe", L + 8, T + 8, 0xFF9147FF);
            g.fill(L, T + 25, L + 110, T + H, 0xFFF7F7F8);           // sol panel
            g.fill(L + W - 120, T + 25, L + W, T + H, 0xFFF7F7F8);   // sağ panel
            int ink = 0xFF0E0E10, mu = 0xFF6B6B76;
            g.drawString(font, tab == 2 ? "KİŞİLER" : "BEKLEYENLER", L + 6, T + 29, mu);
            if (tab == 2) {
                for (int i = 0; i < Math.min(8, Data.customers.size()); i++) g.drawString(font, cut(Data.customers.get(i), 98), L + 6, T + 42 + i * 12, ink);
            } else {
                for (int i = 0; i < Math.min(8, Data.pending.size()); i++) {
                    Order o = Data.pending.get(i); int y = T + 38 + i * 22;
                    if (i == sel) g.fill(L + 2, y - 1, L + 108, y + 20, 0xFFE3E3E8);
                    avatar(g, o.customer, L + 4, y);
                    g.drawString(font, cut(o.customer, 80), L + 26, y, ink);
                    g.drawString(font, cut(o.lines.get(0), 80), L + 26, y + 10, mu);
                    g.fill(L + 102, y + 2, L + 106, y + 6, 0xFFE91916);   // canlı nokta
                }
            }
            g.drawString(font, "SON TESLİMAT", L + W - 114, T + 29, mu);
            for (int i = 0; i < Math.min(8, Data.history.size()); i++) {
                Order o = Data.history.get(Data.history.size() - 1 - i); int y = T + 40 + i * 21;
                g.drawString(font, cut(o.customer, 108), L + W - 114, y, 0xFF9147FF);
                g.drawString(font, cut(String.join("+", o.lines), 108), L + W - 114, y + 9, ink);
            }
            int cx = L + 118;
            if (tab == 0) {
                if (Data.pending.isEmpty()) g.drawString(font, "Yeni sipariş bekleniyor...", cx, T + 60, mu);
                else {
                    Order o = Data.pending.get(sel);
                    g.fill(cx, T + 34, cx + 164, T + H - 36, 0xFFFFFFFF);
                    g.renderOutline(cx, T + 34, 164, H - 70, 0xFFE3E3E8);
                    avatar(g, o.customer, cx + 8, T + 42);
                    g.drawString(font, o.customer, cx + 32, T + 47, ink);
                    for (int i = 0; i < o.lines.size(); i++) g.drawString(font, "\u25B6 " + o.lines.get(i), cx + 10, T + 74 + i * 14, 0xFF9147FF);
                }
            } else if (tab == 1) {
                g.drawString(font, "TESLİM EDİLENLER", cx, T + 29, mu);
                for (int i = 0; i < Math.min(8, Data.history.size()); i++) {
                    Order o = Data.history.get(Data.history.size() - 1 - i);
                    g.drawString(font, "\u2714 " + cut(o.customer, 80) + " " + o.time, cx, T + 42 + i * 20, 0xFF00A86B);
                    g.drawString(font, cut(String.join(" + ", o.lines), 160), cx, T + 51 + i * 20, ink);
                }
            } else {
                g.drawString(font, "Yeni kişi", cx, T + 40, mu);
                g.drawString(font, "Yeni yemek", cx, T + 80, mu);
                g.drawString(font, "Süre: min / max (sn)", cx, T + 120, mu);
            }
            super.render(g, mx, my, pt);
        }
        @Override public boolean isPauseScreen() { return false; }
    }
}
