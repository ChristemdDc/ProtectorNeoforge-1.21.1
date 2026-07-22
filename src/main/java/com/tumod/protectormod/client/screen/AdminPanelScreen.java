package com.tumod.protectormod.client.screen;

import com.tumod.protectormod.client.AdminPanelClientState;
import com.tumod.protectormod.network.ModNetworking;
import com.tumod.protectormod.network.ModPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Panel de administración (solo admin, se abre con {@code /protector admin panel}). Dos pestañas:
 * <ul>
 *   <li><b>Protecciones</b>: todas las protecciones del servidor (todas las dimensiones), con TP y
 *       eliminar.</li>
 *   <li><b>Clanes</b>: todos los clanes, con expulsar miembros (funciona con el líder offline),
 *       disolver y editar límites.</li>
 * </ul>
 *
 * <p>No pide datos en {@code init()}: el snapshot lo empuja el servidor (al ejecutar el comando y tras
 * cada acción) vía {@link ModPayloads.AdminPanelDataPayload} → {@link AdminPanelClientState}. Es una
 * {@code Screen} cruda, como {@link AllianceScreen}.
 */
public class AdminPanelScreen extends Screen {

    private enum Tab { PROTECTIONS, CLANS }

    private record Label(int x, int y, String text) {}

    private static final int PANEL_W = 380;
    private static final int PANEL_H = 244;
    private static final int PROT_ROW_H = 18;
    private static final int CLAN_ROW_H = 14;
    private static final int MEMBER_ROW_H = 16;

    private static final BlockPos NO_POS = BlockPos.ZERO;
    private static final UUID NIL = new UUID(0L, 0L);

    private Tab tab = Tab.PROTECTIONS;
    private int protScroll = 0;
    private int clanScroll = 0;
    private int memberScroll = 0;
    private UUID selectedClan = null;

    private final List<Label> labels = new ArrayList<>();

    public AdminPanelScreen() {
        super(Component.literal("Panel de Administración"));
    }

    private int panelX() { return this.width / 2 - PANEL_W / 2; }
    private int panelY() { return this.height / 2 - PANEL_H / 2; }
    private int contentTop() { return panelY() + 46; }
    private int contentBottom() { return panelY() + PANEL_H - 14; }

    private ModPayloads.AdminPanelDataPayload data() { return AdminPanelClientState.get(); }

    private List<ModPayloads.AdminProtEntry> protections() {
        ModPayloads.AdminPanelDataPayload d = data();
        if (d == null) return List.of();
        List<ModPayloads.AdminProtEntry> list = new ArrayList<>(d.protections());
        list.sort(Comparator.comparing(ModPayloads.AdminProtEntry::dimension)
                .thenComparing(p -> p.clanName().isEmpty() ? "~" : p.clanName())
                .thenComparing(ModPayloads.AdminProtEntry::ownerName));
        return list;
    }

    private List<ModPayloads.AdminClanEntry> clans() {
        ModPayloads.AdminPanelDataPayload d = data();
        if (d == null) return List.of();
        List<ModPayloads.AdminClanEntry> list = new ArrayList<>(d.clans());
        list.sort(Comparator.comparing(c -> c.name().toLowerCase()));
        return list;
    }

    private ModPayloads.AdminClanEntry selectedClanEntry() {
        if (selectedClan == null) return null;
        for (ModPayloads.AdminClanEntry c : clans()) if (c.clanId().equals(selectedClan)) return c;
        return null;
    }

    @Override
    protected void init() {
        super.init();
        this.labels.clear();
        int px = panelX(), py = panelY();

        // Pestañas.
        this.addRenderableWidget(Button.builder(Component.literal("Protecciones (" + protections().size() + ")"),
                        b -> { this.tab = Tab.PROTECTIONS; rebuildWidgets(); })
                .bounds(px + 10, py + 24, 130, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("Clanes (" + clans().size() + ")"),
                        b -> { this.tab = Tab.CLANS; rebuildWidgets(); })
                .bounds(px + 146, py + 24, 110, 16).build());

        // Refrescar + cerrar.
        this.addRenderableWidget(Button.builder(Component.literal("⟳"),
                        b -> ModNetworking.sendAdminAction("refresh", NO_POS, "", NIL, NIL, 0))
                .bounds(px + PANEL_W - 60, py + 24, 20, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("✕"),
                        b -> this.onClose())
                .bounds(px + PANEL_W - 34, py + 24, 20, 16).build());

        if (tab == Tab.PROTECTIONS) buildProtectionsTab();
        else buildClansTab();
    }

    // ─────────────────────────── Pestaña Protecciones ───────────────────────────

    private void buildProtectionsTab() {
        int px = panelX();
        List<ModPayloads.AdminProtEntry> list = protections();
        int rows = Math.max(1, (contentBottom() - contentTop()) / PROT_ROW_H);
        protScroll = clamp(protScroll, 0, Math.max(0, list.size() - rows));

        if (list.isEmpty()) {
            labels.add(new Label(px + 16, contentTop() + 6, "§8No hay protecciones colocadas."));
            return;
        }
        int end = Math.min(protScroll + rows, list.size());
        for (int i = protScroll; i < end; i++) {
            ModPayloads.AdminProtEntry p = list.get(i);
            int rowY = contentTop() + (i - protScroll) * PROT_ROW_H;
            String type = p.isAdmin() ? "§dADMIN" : "§bL" + p.level();
            String clan = p.clanName().isEmpty() ? "§8-" : "§a" + trim(p.clanName(), 12);
            String txt = "§7" + shortDim(p.dimension()) + " §f" + p.pos().getX() + "," + p.pos().getY() + "," + p.pos().getZ()
                    + "  §f" + trim(p.ownerName(), 12) + "  " + clan + "  " + type + " §7r" + p.radius();
            labels.add(new Label(px + 14, rowY + 5, txt));

            this.addRenderableWidget(Button.builder(Component.literal("TP"),
                            b -> { ModNetworking.sendAdminAction("teleport", p.pos(), p.dimension(), NIL, NIL, 0); this.onClose(); })
                    .bounds(px + PANEL_W - 96, rowY + 1, 40, 15).build());
            this.addRenderableWidget(Button.builder(Component.literal("§cEliminar"),
                            b -> confirm("Eliminar protección",
                                    "¿Eliminar la protección en " + p.pos().getX() + "," + p.pos().getY() + "," + p.pos().getZ()
                                            + " (" + shortDim(p.dimension()) + ")?",
                                    () -> ModNetworking.sendAdminAction("delete_protection", p.pos(), p.dimension(), NIL, NIL, 0)))
                    .bounds(px + PANEL_W - 52, rowY + 1, 42, 15).build());
        }
    }

    // ─────────────────────────── Pestaña Clanes ───────────────────────────

    private void buildClansTab() {
        int px = panelX();
        List<ModPayloads.AdminClanEntry> list = clans();
        // La lista de clanes (izquierda) se dibuja/clica en render/mouseClicked. Aquí solo el detalle.
        if (selectedClan != null && selectedClanEntry() == null) selectedClan = null; // el clan seleccionado ya no existe
        ModPayloads.AdminClanEntry sel = selectedClanEntry();
        if (sel == null) {
            labels.add(new Label(px + 168, contentTop() + 6, "§7Selecciona un clan de la lista."));
            return;
        }
        buildClanDetail(sel);
    }

    private void buildClanDetail(ModPayloads.AdminClanEntry c) {
        int rx = panelX() + 164;
        int y = contentTop();
        labels.add(new Label(rx, y, "§6§l" + trim(c.name(), 18)));
        y += 12;
        labels.add(new Label(rx, y, "§7Líder: §f" + trim(c.leaderName(), 16)));
        y += 14;

        // Protecciones X/Y con steppers.
        labels.add(new Label(rx, y + 3, "§7Protec: §f" + c.protectionsUsed() + "/" + c.maxProtections()));
        this.addRenderableWidget(stepper(rx + 96, y, "-", c.clanId(), "set_max_protections", c.maxProtections() - 1));
        this.addRenderableWidget(stepper(rx + 112, y, "+", c.clanId(), "set_max_protections", c.maxProtections() + 1));
        y += 18;

        // Miembros máx con steppers.
        labels.add(new Label(rx, y + 3, "§7Miemb: §f" + c.members().size() + "/" + c.maxMembers()));
        this.addRenderableWidget(stepper(rx + 96, y, "-", c.clanId(), "set_max_members", c.maxMembers() - 1));
        this.addRenderableWidget(stepper(rx + 112, y, "+", c.clanId(), "set_max_members", c.maxMembers() + 1));
        y += 18;

        labels.add(new Label(rx, y + 3, "§7Aliados: §f" + c.alliesCount()));
        this.addRenderableWidget(Button.builder(Component.literal("§cDisolver clan"),
                        b -> confirm("Disolver clan", "¿Disolver el clan '" + c.name() + "' por completo?",
                                () -> ModNetworking.sendAdminAction("disband", NO_POS, "", c.clanId(), NIL, 0)))
                .bounds(rx + 70, y, 130, 16).build());
        y += 22;

        // Lista de miembros con Expulsar (scroll propio).
        labels.add(new Label(rx, y, "§e§lMiembros"));
        y += 12;
        List<ModPayloads.MemberRef> members = c.members();
        int memberTop = y;
        int memberRows = Math.max(1, (contentBottom() - memberTop) / MEMBER_ROW_H);
        memberScroll = clamp(memberScroll, 0, Math.max(0, members.size() - memberRows));
        int end = Math.min(memberScroll + memberRows, members.size());
        for (int i = memberScroll; i < end; i++) {
            ModPayloads.MemberRef m = members.get(i);
            int rowY = memberTop + (i - memberScroll) * MEMBER_ROW_H;
            labels.add(new Label(rx + 2, rowY + 4, (m.isLeader() ? "§6★ " : "§f") + trim(m.name(), 16)));
            if (!m.isLeader()) {
                this.addRenderableWidget(Button.builder(Component.literal("§cExpulsar"),
                                b -> ModNetworking.sendAdminAction("kick", NO_POS, "", c.clanId(), m.id(), 0))
                        .bounds(rx + 138, rowY + 1, 60, 14).build());
            }
        }
    }

    private Button stepper(int x, int y, String sign, UUID clanId, String action, int newValue) {
        return Button.builder(Component.literal(sign), b -> ModNetworking.sendAdminAction(action, NO_POS, "", clanId, NIL, newValue))
                .bounds(x, y, 14, 16).build();
    }

    // ─────────────────────────── Render ───────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(g, mouseX, mouseY, partialTicks);
        int px = panelX(), py = panelY();

        g.fill(px, py, px + PANEL_W, py + PANEL_H, 0xF01B1B1F);
        drawBorder(g, px, py, PANEL_W, PANEL_H, 0xFF4A4A55);
        g.fill(px + 8, py + 20, px + PANEL_W - 8, py + 21, 0xFF4A4A55);
        g.drawCenteredString(this.font, Component.literal("PANEL DE ADMINISTRACIÓN")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), this.width / 2, py + 8, 0xFFFFFF);

        super.render(g, mouseX, mouseY, partialTicks); // pestañas + botones

        if (data() == null) {
            g.drawString(this.font, "§7Cargando…", px + 16, contentTop() + 6, 0xFFFFFF, false);
            return;
        }

        if (tab == Tab.CLANS) renderClanList(g, mouseX, mouseY);

        for (Label l : labels) {
            g.drawString(this.font, l.text(), l.x(), l.y(), 0xFFFFFF, false);
        }

        renderScrollHint(g);
    }

    private void renderClanList(GuiGraphics g, int mouseX, int mouseY) {
        int px = panelX();
        int lx = px + 10, w = 148;
        int top = contentTop(), bottom = contentBottom();
        g.fill(lx, top - 2, lx + w, bottom, 0xFF121216);
        g.fill(px + 160, contentTop() - 2, px + 161, bottom, 0xFF3A3A42); // separador

        List<ModPayloads.AdminClanEntry> list = clans();
        int rows = Math.max(1, (bottom - top) / CLAN_ROW_H);
        clanScroll = clamp(clanScroll, 0, Math.max(0, list.size() - rows));
        if (list.isEmpty()) {
            g.drawString(this.font, "§8No hay clanes creados.", lx + 5, top + 4, 0xFFFFFF, false);
            return;
        }
        int end = Math.min(clanScroll + rows, list.size());
        for (int i = clanScroll; i < end; i++) {
            ModPayloads.AdminClanEntry c = list.get(i);
            int rowY = top + (i - clanScroll) * CLAN_ROW_H;
            boolean hover = mouseX >= lx && mouseX <= lx + w && mouseY >= rowY && mouseY <= rowY + CLAN_ROW_H;
            boolean sel = c.clanId().equals(selectedClan);
            if (sel) g.fill(lx + 1, rowY, lx + w - 1, rowY + CLAN_ROW_H, 0xFF2E3E5A);
            else if (hover) g.fill(lx + 1, rowY, lx + w - 1, rowY + CLAN_ROW_H, 0xFF26262E);
            g.drawString(this.font, (sel ? "§e> §f" : "§7- §f") + trim(c.name(), 14) + " §8(" + c.members().size() + ")",
                    lx + 5, rowY + 3, 0xFFFFFF, false);
        }
    }

    private void renderScrollHint(GuiGraphics g) {
        // (sin barra de scroll dedicada; rueda del ratón desplaza la lista bajo el cursor)
    }

    // ─────────────────────────── Interacción ───────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && tab == Tab.CLANS) {
            int px = panelX();
            int lx = px + 10, w = 148, top = contentTop(), bottom = contentBottom();
            List<ModPayloads.AdminClanEntry> list = clans();
            int rows = Math.max(1, (bottom - top) / CLAN_ROW_H);
            int end = Math.min(clanScroll + rows, list.size());
            for (int i = clanScroll; i < end; i++) {
                int rowY = top + (i - clanScroll) * CLAN_ROW_H;
                if (mouseX >= lx && mouseX <= lx + w && mouseY >= rowY && mouseY <= rowY + CLAN_ROW_H) {
                    selectedClan = list.get(i).clanId();
                    memberScroll = 0;
                    rebuildWidgets();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int dir = (int) Math.signum(scrollY);
        if (dir != 0) {
            int px = panelX();
            if (tab == Tab.PROTECTIONS) {
                protScroll = Math.max(0, protScroll - dir);
                rebuildWidgets();
                return true;
            } else {
                // Izquierda = lista de clanes; derecha = miembros del clan seleccionado.
                if (mouseX < px + 160) clanScroll = Math.max(0, clanScroll - dir);
                else memberScroll = Math.max(0, memberScroll - dir);
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { this.onClose(); return true; } // ESC
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void onDataUpdated() {
        this.rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private void confirm(String title, String msg, Runnable onYes) {
        AdminPanelScreen self = this;
        this.minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) onYes.run();
            this.minecraft.setScreen(self); // vuelve al panel (conserva pestaña/scroll/selección)
        }, Component.literal(title), Component.literal(msg)));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String shortDim(String id) {
        int i = id.indexOf(':');
        String s = i >= 0 ? id.substring(i + 1) : id;
        return switch (s) {
            case "overworld" -> "OW";
            case "the_nether" -> "Nether";
            case "the_end" -> "End";
            default -> trim(s, 8);
        };
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)) + "…";
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
