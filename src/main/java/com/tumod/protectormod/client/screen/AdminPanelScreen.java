package com.tumod.protectormod.client.screen;

import com.tumod.protectormod.client.AdminPanelClientState;
import com.tumod.protectormod.network.ModNetworking;
import com.tumod.protectormod.network.ModPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Panel de administración (solo admin, {@code /protector admin panel}). Dos pestañas: protecciones y
 * clanes, con TP / eliminar / expulsar / disolver / editar límites.
 *
 * <p>Dibujo 100% inmediato (custom): en cada {@link #render} se pinta todo y se registran las regiones
 * clicables ({@link Hot}); {@link #mouseClicked} las recorre. Así la lista con scroll no crea/destruye
 * widgets y el estilo es totalmente propio. Los datos los empuja el servidor
 * ({@link ModPayloads.AdminPanelDataPayload} → {@link AdminPanelClientState}); no se pide nada en init.
 */
public class AdminPanelScreen extends Screen {

    private enum Tab { PROTECTIONS, CLANS }

    private record Hot(int x1, int y1, int x2, int y2, Runnable action) {}

    // Paleta.
    private static final int C_BG        = 0xF014141A;
    private static final int C_PANEL     = 0xFF1B1B22;
    private static final int C_BORDER    = 0xFF3B3B48;
    private static final int C_HEAD_TOP  = 0xFF2C2C3C;
    private static final int C_HEAD_BOT  = 0xFF1E1E28;
    private static final int C_GOLD      = 0xFFE0B040;
    private static final int C_TXT       = 0xFFE8E8EE;
    private static final int C_SUB       = 0xFF9A9AA6;
    private static final int C_DIM       = 0xFF64646E;
    private static final int C_ROW_ALT   = 0x14FFFFFF;
    private static final int C_HOVER     = 0x28FFFFFF;
    private static final int C_SEL       = 0xFF2E3E5C;
    private static final int C_TAB_ON    = 0xFF39496B;
    private static final int C_TAB_OFF   = 0xFF24242E;
    private static final int C_BTN       = 0xFF33333F;
    private static final int C_BTN_TP    = 0xFF2C5136;
    private static final int C_BTN_DANG  = 0xFF5A2A2E;
    private static final int C_GREEN     = 0xFF63C46A;
    private static final int C_RED       = 0xFFE05656;
    private static final int C_WARN      = 0xFFE0A030;

    private static final BlockPos NO_POS = BlockPos.ZERO;
    private static final UUID NIL = new UUID(0L, 0L);

    private Tab tab = Tab.PROTECTIONS;
    private int protScroll = 0;
    private int clanScroll = 0;
    private int memberScroll = 0;
    private UUID selectedClan = null;

    private final List<Hot> hots = new ArrayList<>();

    public AdminPanelScreen() {
        super(Component.literal("Panel de Administración"));
    }

    // ── Layout ──
    private int panelW() { return Math.min(this.width - 24, 470); }
    private int panelH() { return Math.min(this.height - 24, 286); }
    private int px() { return (this.width - panelW()) / 2; }
    private int py() { return (this.height - panelH()) / 2; }
    private int headerH() { return 26; }
    private int tabY() { return py() + headerH() + 4; }
    private int tabH() { return 18; }
    private int contentTop() { return tabY() + tabH() + 6; }
    private int contentBottom() { return py() + panelH() - 20; }

    private ModPayloads.AdminPanelDataPayload data() { return AdminPanelClientState.get(); }

    private List<ModPayloads.AdminProtEntry> protections() {
        ModPayloads.AdminPanelDataPayload d = data();
        if (d == null) return List.of();
        List<ModPayloads.AdminProtEntry> l = new ArrayList<>(d.protections());
        l.sort(Comparator.comparing(ModPayloads.AdminProtEntry::dimension)
                .thenComparing(p -> p.clanName().isEmpty() ? "~" : p.clanName().toLowerCase())
                .thenComparing(p -> p.ownerName().toLowerCase()));
        return l;
    }

    private List<ModPayloads.AdminClanEntry> clans() {
        ModPayloads.AdminPanelDataPayload d = data();
        if (d == null) return List.of();
        List<ModPayloads.AdminClanEntry> l = new ArrayList<>(d.clans());
        l.sort(Comparator.comparing(c -> c.name().toLowerCase()));
        return l;
    }

    private ModPayloads.AdminClanEntry selectedClanEntry() {
        if (selectedClan == null) return null;
        for (ModPayloads.AdminClanEntry c : clans()) if (c.clanId().equals(selectedClan)) return c;
        return null;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        this.renderBackground(g, mouseX, mouseY, pt);
        hots.clear();

        int px = px(), py = py(), pw = panelW(), ph = panelH();

        // Marco.
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, C_BORDER);
        g.fill(px, py, px + pw, py + ph, C_PANEL);

        // Cabecera con degradado.
        g.fillGradient(px, py, px + pw, py + headerH(), C_HEAD_TOP, C_HEAD_BOT);
        g.fill(px, py + headerH(), px + pw, py + headerH() + 1, C_BORDER);
        g.drawString(this.font, Component.literal("PANEL DE ADMINISTRACIÓN"), px + 10, py + 9, C_GOLD, false);

        // Toolbar (refrescar / cerrar).
        button(g, mouseX, mouseY, px + pw - 116, py + 6, 56, 14, "Actualizar", C_BTN,
                () -> ModNetworking.sendAdminAction("refresh", NO_POS, "", NIL, NIL, 0));
        button(g, mouseX, mouseY, px + pw - 56, py + 6, 46, 14, "Cerrar", C_BTN_DANG, this::onClose);

        // Pestañas.
        int nProt = data() == null ? 0 : data().protections().size();
        int nClan = data() == null ? 0 : data().clans().size();
        tab(g, mouseX, mouseY, px + 8, tabY(), 150, "Protecciones  " + nProt, tab == Tab.PROTECTIONS,
                () -> { tab = Tab.PROTECTIONS; });
        tab(g, mouseX, mouseY, px + 164, tabY(), 120, "Clanes  " + nClan, tab == Tab.CLANS,
                () -> { tab = Tab.CLANS; });

        // Cuerpo.
        g.fill(px + 6, contentTop() - 2, px + pw - 6, contentBottom() + 2, 0x30000000);
        if (data() == null) {
            g.drawString(this.font, "Cargando…", px + 14, contentTop() + 6, C_SUB, false);
        } else if (tab == Tab.PROTECTIONS) {
            renderProtections(g, mouseX, mouseY);
        } else {
            renderClans(g, mouseX, mouseY);
        }

        // Pie.
        g.fill(px, py + ph - 16, px + pw, py + ph - 15, C_BORDER);
        String hint = tab == Tab.PROTECTIONS
                ? "§7Rueda: desplazar  ·  §aTP§7 ir  ·  §cEliminar§7 borrar"
                : "§7Clic: seleccionar clan  ·  §cExpulsar / Disolver§7  ·  §f±§7 límites";
        g.drawString(this.font, hint, px + 10, py + ph - 12, C_DIM, false);
    }

    // ─────────────────────────── Protecciones ───────────────────────────

    private void renderProtections(GuiGraphics g, int mx, int my) {
        int px = px(), pw = panelW();
        int innerX = px + 10, innerR = px + pw - 10;
        int actW = 76;
        // Columnas.
        int cDim = innerX, cCoord = innerX + 34, cOwner = innerX + 146, cClan = innerX + 232;
        int cLvl = innerR - actW - 62, cRad = innerR - actW - 30;
        int top = contentTop();

        // Cabecera de columnas.
        g.drawString(this.font, "DIM", cDim, top, C_DIM, false);
        g.drawString(this.font, "COORDENADAS", cCoord, top, C_DIM, false);
        g.drawString(this.font, "DUEÑO", cOwner, top, C_DIM, false);
        g.drawString(this.font, "CLAN", cClan, top, C_DIM, false);
        g.drawString(this.font, "NIV", cLvl, top, C_DIM, false);
        g.drawString(this.font, "RAD", cRad, top, C_DIM, false);
        int listTop = top + 12;
        int listBot = contentBottom();
        g.fill(innerX - 2, listTop - 1, innerR + 2, listTop, C_BORDER);

        List<ModPayloads.AdminProtEntry> list = protections();
        int rowH = 15;
        int rows = Math.max(1, (listBot - listTop) / rowH);
        protScroll = clamp(protScroll, 0, Math.max(0, list.size() - rows));
        if (list.isEmpty()) {
            g.drawString(this.font, "§8No hay protecciones registradas.", innerX, listTop + 6, C_SUB, false);
            return;
        }
        int end = Math.min(protScroll + rows, list.size());
        for (int i = protScroll; i < end; i++) {
            ModPayloads.AdminProtEntry p = list.get(i);
            int y = listTop + (i - protScroll) * rowH;
            boolean hover = mx >= innerX - 2 && mx <= innerR + 2 && my >= y && my <= y + rowH;
            if ((i & 1) == 0) g.fill(innerX - 2, y, innerR + 2, y + rowH, C_ROW_ALT);
            if (hover) g.fill(innerX - 2, y, innerR + 2, y + rowH, C_HOVER);
            int ty = y + 4;

            g.drawString(this.font, shortDim(p.dimension()), cDim, ty, C_SUB, false);
            g.drawString(this.font, fit(p.pos().getX() + " " + p.pos().getY() + " " + p.pos().getZ(), cOwner - cCoord - 4), cCoord, ty, C_TXT, false);
            g.drawString(this.font, fit(p.ownerName(), cClan - cOwner - 4), cOwner, ty, C_TXT, false);
            g.drawString(this.font, p.clanName().isEmpty() ? "§8-" : fit(p.clanName(), cLvl - cClan - 4), cClan, ty, p.clanName().isEmpty() ? C_DIM : C_GREEN, false);
            // Badge de nivel/tipo.
            String lvlTxt = p.isAdmin() ? "ADM" : "L" + p.level();
            g.drawString(this.font, lvlTxt, cLvl, ty, levelColor(p), false);
            g.drawString(this.font, String.valueOf(p.radius()), cRad, ty, C_SUB, false);

            // Acciones.
            int bx = innerR - actW;
            button(g, mx, my, bx, y + 1, 26, 13, "TP", C_BTN_TP,
                    () -> { ModNetworking.sendAdminAction("teleport", p.pos(), p.dimension(), NIL, NIL, 0); this.onClose(); });
            button(g, mx, my, bx + 30, y + 1, 46, 13, "Eliminar", C_BTN_DANG,
                    () -> confirm("Eliminar protección",
                            "¿Eliminar la protección en " + p.pos().getX() + " " + p.pos().getY() + " " + p.pos().getZ()
                                    + " (" + shortDim(p.dimension()) + ")?",
                            () -> ModNetworking.sendAdminAction("delete_protection", p.pos(), p.dimension(), NIL, NIL, 0)));
        }
        scrollbar(g, innerR + 3, listTop, listBot, list.size(), rows, protScroll);
    }

    // ─────────────────────────── Clanes ───────────────────────────

    private void renderClans(GuiGraphics g, int mx, int my) {
        int px = px(), pw = panelW();
        int lx = px + 10, lw = 152;
        int top = contentTop(), bot = contentBottom();
        int sep = lx + lw + 4;
        g.fill(sep, top - 2, sep + 1, bot, C_BORDER);

        // Lista de clanes (izquierda).
        List<ModPayloads.AdminClanEntry> list = clans();
        int rowH = 24;
        int rows = Math.max(1, (bot - top) / rowH);
        clanScroll = clamp(clanScroll, 0, Math.max(0, list.size() - rows));
        if (list.isEmpty()) {
            g.drawString(this.font, "§8No hay clanes.", lx, top + 6, C_SUB, false);
        }
        int end = Math.min(clanScroll + rows, list.size());
        for (int i = clanScroll; i < end; i++) {
            ModPayloads.AdminClanEntry c = list.get(i);
            int y = top + (i - clanScroll) * rowH;
            boolean sel = c.clanId().equals(selectedClan);
            boolean hover = mx >= lx && mx <= lx + lw && my >= y && my <= y + rowH - 2;
            boolean desync = c.registeredProtections() < c.protectionsUsed();
            g.fill(lx, y, lx + lw, y + rowH - 2, sel ? C_SEL : (hover ? C_HOVER : C_ROW_ALT));
            if (sel) g.fill(lx, y, lx + 2, y + rowH - 2, C_GOLD);
            g.drawString(this.font, fit(c.name(), lw - 16), lx + 6, y + 4, sel ? C_GOLD : C_TXT, false);
            String sub = "M " + c.members().size() + "/" + c.maxMembers()
                    + "   P " + c.registeredProtections() + "/" + c.protectionsUsed();
            g.drawString(this.font, sub, lx + 6, y + 14, C_SUB, false);
            if (desync) g.drawString(this.font, "(!)", lx + lw - 16, y + 4, C_WARN, false);
            hots.add(new Hot(lx, y, lx + lw, y + rowH - 2, () -> { selectedClan = c.clanId(); memberScroll = 0; }));
        }
        scrollbar(g, lx + lw - 1, top, bot, list.size(), rows, clanScroll);

        // Detalle (derecha).
        renderClanDetail(g, mx, my, sep + 8, top, px + pw - 10, bot);
    }

    private void renderClanDetail(GuiGraphics g, int mx, int my, int x, int top, int right, int bot) {
        if (selectedClan != null && selectedClanEntry() == null) selectedClan = null;
        ModPayloads.AdminClanEntry c = selectedClanEntry();
        if (c == null) {
            g.drawString(this.font, "§7Selecciona un clan", x, top + 8, C_SUB, false);
            return;
        }
        int y = top;
        g.drawString(this.font, fit(c.name(), right - x), x, y, C_GOLD, false);
        y += 12;
        g.drawString(this.font, "Líder: §f" + fit(c.leaderName(), right - x - 34), x, y, C_SUB, false);
        y += 16;

        boolean desync = c.registeredProtections() < c.protectionsUsed();
        // Protecciones.
        g.drawString(this.font, "Protecciones", x, y + 3, C_SUB, false);
        String pv = c.registeredProtections() + "/" + c.protectionsUsed() + " · máx " + c.maxProtections();
        g.drawString(this.font, pv, x + 76, y + 3, desync ? C_WARN : C_TXT, false);
        button(g, mx, my, right - 34, y, 15, 14, "-", C_BTN,
                () -> ModNetworking.sendAdminAction("set_max_protections", NO_POS, "", c.clanId(), NIL, c.maxProtections() - 1));
        button(g, mx, my, right - 17, y, 15, 14, "+", C_BTN,
                () -> ModNetworking.sendAdminAction("set_max_protections", NO_POS, "", c.clanId(), NIL, c.maxProtections() + 1));
        y += 18;
        if (desync) {
            g.drawString(this.font, "§e(!) " + (c.protectionsUsed() - c.registeredProtections())
                    + " protección(es) no están en el registro (visita su chunk para recuperarlas).", x, y, C_WARN, false);
            y += 12;
        }
        // Miembros máx.
        g.drawString(this.font, "Miembros", x, y + 3, C_SUB, false);
        g.drawString(this.font, c.members().size() + "/" + c.maxMembers(), x + 76, y + 3, C_TXT, false);
        button(g, mx, my, right - 34, y, 15, 14, "-", C_BTN,
                () -> ModNetworking.sendAdminAction("set_max_members", NO_POS, "", c.clanId(), NIL, c.maxMembers() - 1));
        button(g, mx, my, right - 17, y, 15, 14, "+", C_BTN,
                () -> ModNetworking.sendAdminAction("set_max_members", NO_POS, "", c.clanId(), NIL, c.maxMembers() + 1));
        y += 18;
        g.drawString(this.font, "Aliados: §f" + c.alliesCount(), x, y + 3, C_SUB, false);
        button(g, mx, my, right - 96, y, 96, 14, "Disolver clan", C_BTN_DANG,
                () -> confirm("Disolver clan", "¿Disolver el clan '" + c.name() + "' por completo?",
                        () -> ModNetworking.sendAdminAction("disband", NO_POS, "", c.clanId(), NIL, 0)));
        y += 20;

        // Miembros.
        g.fill(x, y, right, y + 1, C_BORDER);
        y += 3;
        g.drawString(this.font, "MIEMBROS", x, y, C_DIM, false);
        y += 11;
        List<ModPayloads.MemberRef> members = c.members();
        int rowH = 15;
        int rows = Math.max(1, (bot - y) / rowH);
        memberScroll = clamp(memberScroll, 0, Math.max(0, members.size() - rows));
        int end = Math.min(memberScroll + rows, members.size());
        for (int i = memberScroll; i < end; i++) {
            ModPayloads.MemberRef m = members.get(i);
            int ry = y + (i - memberScroll) * rowH;
            boolean hover = mx >= x && mx <= right && my >= ry && my <= ry + rowH;
            if (hover) g.fill(x, ry, right, ry + rowH, C_HOVER);
            String name = (m.isLeader() ? "§6* " : "§f- ") + fit(m.name(), right - x - 70);
            g.drawString(this.font, name, x, ry + 4, m.isLeader() ? C_GOLD : C_TXT, false);
            if (!m.isLeader()) {
                button(g, mx, my, right - 58, ry + 1, 58, 13, "Expulsar", C_BTN_DANG,
                        () -> ModNetworking.sendAdminAction("kick", NO_POS, "", c.clanId(), m.id(), 0));
            }
        }
        scrollbar(g, right + 1, y, bot, members.size(), rows, memberScroll);
    }

    // ─────────────────────────── Widgets custom ───────────────────────────

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String label, int color, Runnable action) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        g.fill(x, y, x + w, y + h, hover ? lighten(color) : color);
        drawBorder(g, x, y, w, h, 0x50FFFFFF);
        int tw = this.font.width(label);
        g.drawString(this.font, label, x + Math.max(1, (w - tw) / 2), y + (h - 8) / 2 + 1, C_TXT, false);
        hots.add(new Hot(x, y, x + w, y + h, action));
    }

    private void tab(GuiGraphics g, int mx, int my, int x, int y, int w, String label, boolean active, Runnable action) {
        int h = tabH();
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        g.fill(x, y, x + w, y + h, active ? C_TAB_ON : (hover ? lighten(C_TAB_OFF) : C_TAB_OFF));
        if (active) g.fill(x, y + h - 2, x + w, y + h, C_GOLD);
        int tw = this.font.width(label);
        g.drawString(this.font, label, x + (w - tw) / 2, y + 5, active ? C_TXT : C_SUB, false);
        hots.add(new Hot(x, y, x + w, y + h, action));
    }

    private void scrollbar(GuiGraphics g, int x, int top, int bot, int total, int rows, int scroll) {
        int maxScroll = Math.max(0, total - rows);
        if (maxScroll <= 0) return;
        int trackH = bot - top;
        g.fill(x, top, x + 3, bot, 0xFF26262E);
        int thumbH = Math.max(12, trackH * rows / total);
        int thumbY = top + (trackH - thumbH) * scroll / maxScroll;
        g.fill(x, thumbY, x + 3, thumbY + thumbH, 0xFF7A7A8A);
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static int lighten(int argb) {
        int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, gg = (argb >> 8) & 0xFF, b = argb & 0xFF;
        r = Math.min(255, r + 28); gg = Math.min(255, gg + 28); b = Math.min(255, b + 28);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    private int levelColor(ModPayloads.AdminProtEntry p) {
        if (p.isAdmin()) return 0xFFC060E0;
        return switch (p.level()) {
            case 1 -> 0xFFC8814F; case 2 -> 0xFFCFCFCF; case 3 -> 0xFFF2CC4B;
            case 4 -> 0xFF4FE0D0; case 5 -> 0xFF9A7FA0; default -> C_SUB;
        };
    }

    // ─────────────────────────── Interacción ───────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = hots.size() - 1; i >= 0; i--) { // último dibujado = encima
                Hot h = hots.get(i);
                if (mouseX >= h.x1() && mouseX <= h.x2() && mouseY >= h.y1() && mouseY <= h.y2()) {
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                    h.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) {
        int dir = (int) Math.signum(sy);
        if (dir == 0) return super.mouseScrolled(mouseX, mouseY, sx, sy);
        if (tab == Tab.PROTECTIONS) {
            protScroll = Math.max(0, protScroll - dir);
        } else {
            int sepX = px() + 10 + 152 + 4;
            if (mouseX < sepX) clanScroll = Math.max(0, clanScroll - dir);
            else memberScroll = Math.max(0, memberScroll - dir);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { this.onClose(); return true; } // ESC
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void onDataUpdated() { /* modo inmediato: el próximo render usa el estado nuevo */ }

    @Override
    public boolean isPauseScreen() { return false; }

    // ─────────────────────────── Helpers ───────────────────────────

    private void confirm(String title, String msg, Runnable onYes) {
        AdminPanelScreen self = this;
        this.minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) onYes.run();
            this.minecraft.setScreen(self);
        }, Component.literal(title), Component.literal(msg)));
    }

    private String fit(String s, int maxPx) {
        if (s == null) s = "";
        if (maxPx < 6) return "";
        if (this.font.width(s) <= maxPx) return s;
        while (s.length() > 1 && this.font.width(s + "…") > maxPx) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private static String shortDim(String id) {
        int i = id.indexOf(':');
        String s = i >= 0 ? id.substring(i + 1) : id;
        return switch (s) {
            case "overworld" -> "OW";
            case "the_nether" -> "Neth";
            case "the_end" -> "End";
            default -> s.length() <= 5 ? s : s.substring(0, 4) + "…";
        };
    }
}
