package com.tumod.protectormod.client.screen;

import com.tumod.protectormod.client.AllianceClientState;
import com.tumod.protectormod.network.ModNetworking;
import com.tumod.protectormod.network.ModPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Menú de gestión de alianzas de clan. Se abre desde el botón "Aliados" de la
 * {@link ProtectionCoreScreen}. Panel de dos columnas:
 * <ul>
 *   <li><b>Izquierda</b>: estado — aliados actuales (con permisos B/I/C editables por el líder)
 *       y solicitudes recibidas/enviadas.</li>
 *   <li><b>Derecha</b>: buscar y aliarse — buscador con autocompletado (TAB) y un listado de
 *       clanes disponibles siempre visible (con scroll), coincidencia exacta primero.</li>
 * </ul>
 *
 * <p>Los datos vienen del servidor vía {@link ModPayloads.AllianceDataPayload}
 * ({@link AllianceClientState}); {@link #onDataUpdated()} reconstruye la UI al llegar un update.
 */
public class AllianceScreen extends Screen {

    private record Label(int x, int y, String text) {}

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 250;
    private static final int MAX_REQ_VISIBLE = 2;
    private static final int LIST_ROW_H = 14;

    private final Screen lastScreen;
    private final BlockPos corePos;
    private final List<Label> labels = new ArrayList<>();

    private EditBox searchInput;
    private String searchText = "";
    private int candidateScroll = 0;
    private boolean requested = false; // evita re-pedir datos en cada rebuildWidgets (bucle de foco)

    public AllianceScreen(Screen lastScreen, BlockPos corePos) {
        super(Component.literal("Alianzas"));
        this.lastScreen = lastScreen;
        this.corePos = corePos;
    }

    private int panelX() { return this.width / 2 - PANEL_W / 2; }
    private int panelY() { return this.height / 2 - PANEL_H / 2; }
    private int leftX()  { return panelX() + 14; }
    private int rightX() { return panelX() + 182; }
    private int listTop() { return panelY() + 76; }
    private int listBottom() { return panelY() + PANEL_H - 30; }
    private int visibleRows() { return Math.max(1, (listBottom() - listTop()) / LIST_ROW_H); }

    @Override
    protected void init() {
        super.init();
        // Pedir datos UNA sola vez al abrir. Si se pide en cada init(), la respuesta del servidor
        // dispara rebuildWidgets() → init() → nueva petición… bucle que recrea el EditBox y le
        // roba el foco (por eso no se podía escribir). Las actualizaciones posteriores llegan
        // solas tras cada acción (los handlers del servidor reenvían el snapshot).
        if (!this.requested) {
            ModNetworking.sendOpenAlliance(this.corePos);
            this.requested = true;
        }

        this.labels.clear();
        this.searchInput = null; // se recrea solo si el líder aún puede aliarse (evita lista fantasma)
        int px = panelX(), py = panelY();
        int lx = leftX(), rx = rightX();

        this.addRenderableWidget(Button.builder(Component.literal("« Volver"), b -> this.minecraft.setScreen(lastScreen))
                .bounds(px + PANEL_W / 2 - 45, py + PANEL_H - 22, 90, 18).build());

        ModPayloads.AllianceDataPayload data = AllianceClientState.get();
        if (data == null || !data.hasClan()) {
            labels.add(new Label(px + 20, py + 60, "§7Necesitas pertenecer a un clan para gestionar alianzas."));
            return;
        }

        boolean leader = data.isLeader();
        boolean atMax = data.allies().size() >= data.maxAlliances();

        // ── Columna izquierda: estado ──
        int y = py + 46;
        labels.add(new Label(lx, y, "§6§lAliados §7(" + data.allies().size() + "/" + data.maxAlliances() + ")"));
        y += 14;
        if (data.allies().isEmpty()) {
            labels.add(new Label(lx + 6, y, "§8sin aliados"));
            y += 16;
        } else {
            for (ModPayloads.AllyEntry ally : data.allies()) {
                labels.add(new Label(lx + 4, y, "§b" + ally.clanName()));
                if (leader) {
                    UUID id = ally.clanId();
                    this.addRenderableWidget(permButton(lx + 4, y + 10, "B", ally.build(), id, "build"));
                    this.addRenderableWidget(permButton(lx + 26, y + 10, "I", ally.interact(), id, "interact"));
                    this.addRenderableWidget(permButton(lx + 48, y + 10, "C", ally.chests(), id, "chests"));
                    this.addRenderableWidget(Button.builder(Component.literal("§cRomper"),
                                    b -> ModNetworking.sendAllianceAction(corePos, "break", id))
                            .bounds(lx + 72, y + 10, 50, 14).build());
                    y += 30;
                } else {
                    String p = (ally.build() ? "B" : "") + (ally.interact() ? "I" : "") + (ally.chests() ? "C" : "");
                    labels.add(new Label(lx + 90, y, "§7[" + (p.isEmpty() ? "-" : p) + "]"));
                    y += 14;
                }
            }
        }

        y += 8;
        labels.add(new Label(lx, y, "§a§lRecibidas"));
        y += 14;
        y = requestSection(data.incoming(), y, lx, leader, true);

        y += 6;
        labels.add(new Label(lx, y, "§e§lEnviadas"));
        y += 14;
        requestSection(data.outgoing(), y, lx, leader, false);

        // ── Columna derecha: buscar / aliarse ──
        if (leader && !atMax) {
            labels.add(new Label(rx, py + 46, "§6§lAliarse con un clan"));
            this.searchInput = new EditBox(this.font, rx, py + 58, 144, 15, Component.literal("Buscar clan"));
            this.searchInput.setMaxLength(16);
            this.searchInput.setHint(Component.literal("Buscar… (TAB autocompleta)").withStyle(ChatFormatting.DARK_GRAY));
            this.searchInput.setValue(searchText);
            this.searchInput.setResponder(s -> { this.searchText = s; this.candidateScroll = 0; });
            this.addRenderableWidget(this.searchInput);
        } else if (leader) {
            // Al máximo de alianzas: se oculta toda la sección de aliarse (sin lista).
            labels.add(new Label(rx, py + 46, "§6§lAliarse con un clan"));
            labels.add(new Label(rx, py + 64, "§7Ya tienes el máximo de"));
            labels.add(new Label(rx, py + 76, "§7aliados §f(" + data.allies().size() + "/" + data.maxAlliances() + ")§7."));
            labels.add(new Label(rx, py + 94, "§7Rompe una alianza para"));
            labels.add(new Label(rx, py + 106, "§7aliarte con otro clan."));
        } else {
            labels.add(new Label(rx, py + 46, "§6§lAliarse con un clan"));
            labels.add(new Label(rx, py + 64, "§8Solo el líder del clan"));
            labels.add(new Label(rx, py + 76, "§8puede gestionar alianzas."));
        }
    }

    private int requestSection(List<ModPayloads.ClanRef> reqs, int y, int lx, boolean leader, boolean incoming) {
        if (reqs.isEmpty()) {
            labels.add(new Label(lx + 6, y, "§8ninguna"));
            return y + 15;
        }
        int shown = Math.min(reqs.size(), MAX_REQ_VISIBLE);
        for (int i = 0; i < shown; i++) {
            ModPayloads.ClanRef req = reqs.get(i);
            UUID id = req.clanId();
            labels.add(new Label(lx + 4, y + 3, "§f" + trim(req.clanName(), 10)));
            if (leader) {
                if (incoming) {
                    this.addRenderableWidget(Button.builder(Component.literal("§a✔"),
                                    b -> ModNetworking.sendAllianceAction(corePos, "accept", id))
                            .bounds(lx + 96, y, 20, 15).build());
                    this.addRenderableWidget(Button.builder(Component.literal("§c✘"),
                                    b -> ModNetworking.sendAllianceAction(corePos, "reject", id))
                            .bounds(lx + 118, y, 20, 15).build());
                } else {
                    this.addRenderableWidget(Button.builder(Component.literal("§7✘ cancelar"),
                                    b -> ModNetworking.sendAllianceAction(corePos, "cancel", id))
                            .bounds(lx + 78, y, 60, 15).build());
                }
            }
            y += 17;
        }
        if (reqs.size() > shown) {
            labels.add(new Label(lx + 6, y, "§8+" + (reqs.size() - shown) + " más"));
            y += 13;
        }
        return y;
    }

    private Button permButton(int x, int y, String letter, boolean on, UUID allyId, String type) {
        Component label = Component.literal(letter + (on ? "§a✔" : "§c✘"));
        return Button.builder(label, b -> ModNetworking.sendAllyPerm(corePos, allyId, type, !on))
                .bounds(x, y, 20, 14).build();
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /** Candidatos filtrados y ordenados: exacto → empieza-por → contiene → alfabético. */
    private List<ModPayloads.ClanRef> getFilteredCandidates() {
        ModPayloads.AllianceDataPayload data = AllianceClientState.get();
        if (data == null) return List.of();
        String q = (searchInput != null ? searchInput.getValue() : searchText).trim().toLowerCase();
        List<ModPayloads.ClanRef> all = new ArrayList<>(data.candidates());
        if (q.isEmpty()) {
            all.sort(Comparator.comparing(c -> c.clanName().toLowerCase()));
            return all;
        }
        return all.stream()
                .filter(c -> c.clanName().toLowerCase().contains(q))
                .sorted(Comparator.comparingInt((ModPayloads.ClanRef c) -> rank(c.clanName().toLowerCase(), q))
                        .thenComparing(c -> c.clanName().toLowerCase()))
                .toList();
    }

    private static int rank(String name, String q) {
        if (name.equals(q)) return 0;
        if (name.startsWith(q)) return 1;
        return 2;
    }

    private boolean listActive() {
        return this.searchInput != null; // solo líder && !atMax crea el buscador
    }

    public void onDataUpdated() { this.rebuildWidgets(); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);

        int px = panelX(), py = panelY();
        int cx = this.width / 2;

        // Panel de fondo + borde + separador de columnas.
        graphics.fill(px, py, px + PANEL_W, py + PANEL_H, 0xF01B1B1F);
        drawBorder(graphics, px, py, PANEL_W, PANEL_H, 0xFF4A4A55);
        graphics.fill(px + 8, py + 40, px + PANEL_W - 8, py + 41, 0xFF4A4A55); // línea bajo el título
        graphics.fill(px + 168, py + 44, px + 169, py + PANEL_H - 26, 0xFF3A3A42); // separador columnas

        super.render(graphics, mouseX, mouseY, partialTicks); // botones + EditBox

        ModPayloads.AllianceDataPayload data = AllianceClientState.get();
        String title = (data != null && data.hasClan())
                ? "ALIANZAS  ·  " + data.ownClanName().toUpperCase()
                : "ALIANZAS";
        graphics.drawCenteredString(this.font,
                Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), cx, py + 14, 0xFFFFFF);

        for (Label l : labels) {
            graphics.drawString(this.font, l.text(), l.x(), l.y(), 0xFFFFFF, false);
        }

        // Lista de clanes disponibles (siempre visible en la columna derecha).
        if (listActive()) {
            renderCandidateList(graphics, mouseX, mouseY);
        }
    }

    private void renderCandidateList(GuiGraphics graphics, int mouseX, int mouseY) {
        int rx = rightX();
        int top = listTop();
        int bottom = listBottom();
        int w = 144;

        graphics.fill(rx, top - 2, rx + w, bottom, 0xFF121216); // fondo de la lista

        List<ModPayloads.ClanRef> filtered = getFilteredCandidates();
        int rows = visibleRows();
        int maxScroll = Math.max(0, filtered.size() - rows);
        if (candidateScroll > maxScroll) candidateScroll = maxScroll;
        if (candidateScroll < 0) candidateScroll = 0;

        if (filtered.isEmpty()) {
            boolean hasQuery = searchInput != null && !searchInput.getValue().trim().isEmpty();
            graphics.drawString(this.font, hasQuery ? "§8sin coincidencias" : "§8no hay clanes",
                    rx + 5, top + 4, 0xFFFFFF, false);
            return;
        }

        int end = Math.min(candidateScroll + rows, filtered.size());
        for (int i = candidateScroll; i < end; i++) {
            ModPayloads.ClanRef c = filtered.get(i);
            int rowY = top + (i - candidateScroll) * LIST_ROW_H;
            boolean hover = mouseX >= rx && mouseX <= rx + w && mouseY >= rowY && mouseY <= rowY + LIST_ROW_H;
            if (hover) graphics.fill(rx + 1, rowY, rx + w - 1, rowY + LIST_ROW_H, 0xFF2E4A2E);
            graphics.drawString(this.font, (hover ? "§a> §f" : "§7- §f") + trim(c.clanName(), 15),
                    rx + 5, rowY + 3, 0xFFFFFF, false);
        }

        // Barra de scroll.
        if (maxScroll > 0) {
            int trackX = rx + w - 3;
            int trackH = bottom - top;
            graphics.fill(trackX, top, trackX + 2, bottom, 0xFF2A2A32);
            int thumbH = Math.max(12, trackH * rows / filtered.size());
            int thumbY = top + (trackH - thumbH) * candidateScroll / maxScroll;
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFF7A7A8A);
        }
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && listActive()) {
            int rx = rightX();
            int top = listTop();
            int w = 144;
            List<ModPayloads.ClanRef> filtered = getFilteredCandidates();
            int rows = visibleRows();
            int end = Math.min(candidateScroll + rows, filtered.size());
            for (int i = candidateScroll; i < end; i++) {
                int rowY = top + (i - candidateScroll) * LIST_ROW_H;
                if (mouseX >= rx && mouseX <= rx + w && mouseY >= rowY && mouseY <= rowY + LIST_ROW_H) {
                    ModNetworking.sendAllianceAction(corePos, "propose", filtered.get(i).clanId());
                    if (this.searchInput != null) this.searchInput.setValue("");
                    this.searchText = "";
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, 1.2F);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (listActive() && mouseX >= rightX() && mouseX <= rightX() + 144
                && mouseY >= listTop() - 2 && mouseY <= listBottom()) {
            candidateScroll -= (int) Math.signum(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 258 && this.searchInput != null && this.searchInput.isFocused()) { // TAB
            List<ModPayloads.ClanRef> filtered = getFilteredCandidates();
            if (!filtered.isEmpty()) this.searchInput.setValue(filtered.get(0).clanName());
            return true;
        }
        if (keyCode == 256) { // ESC → volver
            this.minecraft.setScreen(lastScreen);
            return true;
        }
        if (this.searchInput != null && this.searchInput.isFocused()) {
            return this.searchInput.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
