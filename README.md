# ProtectorMod — NeoForge 1.21.1

Mod de **protección de áreas** con núcleos, clanes y alianzas, integrado a fondo con **Create Aeronautics** para que las naves aparcadas dentro de una protección no puedan ser robadas.

Reescritura para **NeoForge 1.21.1 / Java 21** del mod original de Fabric 1.20.1.

- **Minecraft:** 1.21.1
- **Loader:** NeoForge 21.1.220+
- **Versión:** 2.1.0
- **Licencia:** MIT

---

## ✨ Características

### Protecciones
- **Núcleos de protección** con 5 niveles (radio hasta 128 bloques), mejorables con materiales.
- **Núcleos de administración** con radio configurable y prioridad sobre el resto.
- Detección de zona **independiente de la carga de chunks** (registro persistente + índice espacial por chunk → búsqueda O(1), pensada para servidores grandes).
- Regla **"el más restrictivo manda"** cuando varias protecciones solapan su área.

### Clanes y alianzas
- **Clanes**: líder, miembros, invitaciones por chat, límite de miembros.
- **Alianzas entre clanes** (máx. 2 por clan) con buscador de clanes y permisos configurables por alianza.
- **Pool de protecciones por clan**: el clan comparte un límite de protecciones colocables.
- **Permisos B/I/C (Build / Interact / Chests) por-núcleo** para cada miembro (independientes en cada protección).

### Sistema de flags (20)
Por-zona, editables por el dueño/admin:

`pvp` · `build` · `chests` · `interact` · `villager-trade` · `fire-damage` · `hunger` · `explosions` · `mob-spawn` · `entry` · `fall-damage` · `fire-spread` · `lighter` · `item-pickup` · `mob-grief` · `use-buckets` · `item-drop` · `crop-trample` · `enderpearl`

### Integración con Create Aeronautics
- Un **clan de ProtectorMod se reconoce como "party" válida** en las protecciones de sublevel de [Aeronautics-Claims](https://github.com/) (AeroClaims).
- ProtectorMod **cede dentro de los ships** (AeroClaims manda ahí); los núcleos de administración mantienen prioridad (usar sí, modificar no).
- **Anti-robo de naves**: bloquea (des)ensamblar un Physics Assembler si la nave está dentro de una protección y el jugador no tiene permiso — incluso resolviendo la posición real de la nave en el mundo cuando está ensamblada.

### Rendimiento y robustez
- Índice espacial por chunk para las búsquedas de protección.
- Persistencia por **`SavedData`/NBT** dentro del guardado del mundo (sin dependencias nativas).
- Compatibilidad soft: si Create Aeronautics no está presente, esas integraciones simplemente no se activan.

---

## 🔧 Compilar

Requiere **JDK 21**.

```bash
./gradlew build
```

El jar queda en `build/libs/protectormod-2.0.0.jar`.

---

## 📦 Dependencias

**Requerida:**
- NeoForge 21.1.220+ (Minecraft 1.21.1)

**Opcionales (soft-dependencies)** — para las integraciones de naves:
- [Create Aeronautics](https://www.curseforge.com/) (aporta `sable`, `simulated`, `veil`)
- Aeronautics-Claims (AeroClaims)

Sin estos mods, ProtectorMod funciona igual como mod de protección de áreas; solo se desactivan las funciones específicas de ships.

---

## 🎮 Comandos

- `/clan …` — gestión de clanes (info, protecciones, etc.).
- `/protector accept` / `/protector deny` — aceptar o rechazar una invitación.

---

## 🗂️ Estructura

```
src/main/java/com/tumod/protectormod/
├── block/            Bloques (núcleo, núcleo admin)
├── blockentity/      BlockEntities y permisos por-núcleo
├── client/           GUIs y render (pantallas de clan, alianzas, flags)
├── command/          Comandos de clan
├── event/            Eventos de juego (aplicación de flags/permisos)
├── integration/      Create Aeronautics: ShipGuard, AssemblyGuard, resolver de party
├── mixin/            Mixins (fuego, cubos, ensamblado, etc.)
├── network/          Payloads y handlers de red
└── util/             Persistencia (SavedData), datos de clanes, índice de protecciones
```

---

## 📄 Licencia

MIT. Autor original: **Shadow_K3**.
