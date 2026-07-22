package com.tumod.protectormod.block;

import com.cretania.animaslime.engine.block.AnimaSlimeTallColumn;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * Protector Mecánico: una protección EXTRA, aparte del {@link ProtectionCoreBlock} clásico. Hereda
 * TODA su funcionalidad (colocación con límites de clan y solape, integración con ships, mejoras por
 * nivel, GUI, permisos B/I/C, flags, red, aplicación de reglas) porque:
 *
 * <ul>
 *   <li>Extiende {@link ProtectionCoreBlock}: reutiliza {@code setPlacedBy}, {@code onRemove},
 *       {@code useWithoutItem}, drops, etc. sin duplicar nada.</li>
 *   <li>Su {@code BlockEntity} es un {@link ProtectionCoreBlockEntity} normal (con un
 *       {@code BlockEntityType} propio), así que {@code ModEvents.findCoreAt}, el índice de
 *       {@code ProtectionDataManager} y el escaneo de chunks —que operan por
 *       {@code instanceof ProtectionCoreBlockEntity}— lo reconocen igual que al núcleo clásico.</li>
 * </ul>
 *
 * <p><b>Lo ÚNICO que cambia respecto al núcleo clásico es el render:</b> en vez de los modelos JSON por
 * nivel, la geometría la pinta el motor de AnimaSlime (ver {@code ProtectorAnimaSlimeModels}). El
 * bloque ya es de 2 celdas de alto ({@code DoubleBlockHalf}), que encaja con el {@code height=2} del
 * modelo animado.
 */
public class MechanicalProtectorBlock extends ProtectionCoreBlock implements AnimaSlimeTallColumn {

    public MechanicalProtectorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // La mitad de ABAJO ancla el modelo animado de 2 celdas; la de ARRIBA es invisible para no
        // dibujar una copia por celda. La colisión sigue siendo Shapes.block() (heredado): columna
        // sólida 1x2x1.
        return state.getValue(HALF) == DoubleBlockHalf.LOWER
                ? RenderShape.ENTITYBLOCK_ANIMATED
                : RenderShape.INVISIBLE;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // Solo la celda de abajo tiene BlockEntity (igual que el núcleo clásico), pero con NUESTRO
        // BlockEntityType, para poder enlazarle SOLO a él el render de AnimaSlime sin afectar al núcleo
        // clásico. Sigue siendo un ProtectionCoreBlockEntity → toda la lógica de protección aplica.
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) return null;
        return new ProtectionCoreBlockEntity(ModBlockEntities.MECHANICAL_PROTECTOR_BE.get(), pos, state);
    }

    // ── AnimaSlimeTallColumn: contorno de selección unificado para la columna de 2 celdas ──
    @Override
    public int columnHeight(BlockState state) {
        return 2;
    }

    @Override
    public int columnSegment(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? 0 : 1;
    }
}
