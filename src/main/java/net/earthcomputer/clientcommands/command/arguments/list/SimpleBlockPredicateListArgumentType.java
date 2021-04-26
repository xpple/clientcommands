package net.earthcomputer.clientcommands.command.arguments.list;

import static net.earthcomputer.clientcommands.command.arguments.SimpleBlockPredicateArgumentType.blockPredicate;

import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.block.Block;

public class SimpleBlockPredicateListArgumentType extends ListArgumentType<Predicate<Block>> {

    private SimpleBlockPredicateListArgumentType(int min, int max) {
        super(blockPredicate(), min, max);
    }

    public static SimpleBlockPredicateListArgumentType blockPredicateList() {
        return blockPredicateList(1);
    }

    public static SimpleBlockPredicateListArgumentType blockPredicateList(int min) {
        return blockPredicateList(min, Integer.MAX_VALUE);
    }

    public static SimpleBlockPredicateListArgumentType blockPredicateList(int min, int max) {
        return new SimpleBlockPredicateListArgumentType(min, max);
    }

    @SuppressWarnings("unchecked")
    public static List<Predicate<Block>> getBlockPredicateList(final CommandContext<?> context, final String name) {
        return (List<Predicate<Block>>) context.getArgument(name, List.class);
    }
}
