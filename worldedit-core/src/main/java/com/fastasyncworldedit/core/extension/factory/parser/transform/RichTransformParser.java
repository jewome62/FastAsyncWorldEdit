package com.fastasyncworldedit.core.extension.factory.parser.transform;

import com.fastasyncworldedit.core.command.SuggestInputParseException;
import com.fastasyncworldedit.core.configuration.Caption;
import com.fastasyncworldedit.core.extension.factory.parser.FaweParser;
import com.fastasyncworldedit.core.extent.ResettableExtent;
import com.fastasyncworldedit.core.extent.transform.MultiTransform;
import com.fastasyncworldedit.core.extent.transform.RandomTransform;
import com.fastasyncworldedit.core.math.random.TrueRandom;
import com.fastasyncworldedit.core.util.StringMan;
import com.sk89q.minecraft.util.commands.CommandLocals;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.NoMatchException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.internal.expression.Expression;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Attempts to parse transforms given rich inputs, allowing for &amp; and ,. Also allows for nested transforms
 */
public class RichTransformParser extends FaweParser<ResettableExtent> {

    /**
     * New instance
     *
     * @param worldEdit {@link WorldEdit} instance.
     */
    public RichTransformParser(WorldEdit worldEdit) {
        super(worldEdit);
    }

    @Override
    public ResettableExtent parseFromInput(String input, ParserContext context) throws InputParseException {
        if (input.isEmpty()) {
            return null;
        }

        List<Double> unionChances = new ArrayList<>();
        List<Double> intersectionChances = new ArrayList<>();

        List<ResettableExtent> intersection = new ArrayList<>();
        List<ResettableExtent> union = new ArrayList<>();
        final CommandLocals locals = new CommandLocals();
        Actor actor = context != null ? context.getActor() : null;
        if (actor != null) {
            locals.put(Actor.class, actor);
        }
        try {
            List<Map.Entry<ParseEntry, List<String>>> parsed = parse(input);
            for (Map.Entry<ParseEntry, List<String>> entry : parsed) {
                ParseEntry pe = entry.getKey();
                String command = pe.getInput();
                ResettableExtent transform;
                double chance = 1;
                if (command.isEmpty()) {
                    transform = parseFromInput(StringMan.join(entry.getValue(), ','), context);
                } else if (!worldEdit.getTransformFactory().containsAlias(command)) {
                    // Legacy syntax
                    int percentIndex = command.indexOf('%');
                    if (percentIndex != -1) {  // Legacy percent pattern
                        chance = Expression.compile(command.substring(0, percentIndex)).evaluate();
                        command = command.substring(percentIndex + 1);
                        if (!entry.getValue().isEmpty()) {
                            if (!command.isEmpty()) {
                                command += " ";
                            }
                            command += StringMan.join(entry.getValue(), " ");
                        }
                        transform = parseFromInput(command, context);
                    } else {
                        throw new NoMatchException(Caption.of("fawe.error.parse.unknown-transform", pe.getFull(),
                                TextComponent
                                        .of("https://github.com/IntellectualSites/FastAsyncWorldEdit-Documentation/wiki/Transforms"
                                        )
                                        .clickEvent(ClickEvent.openUrl(
                                                "https://github.com/IntellectualSites/FastAsyncWorldEdit-Documentation/wiki/Transforms"
                                        ))
                        ));
                    }
                } else {
                    try {
                        transform = worldEdit.getTransformFactory().parseWithoutRich(pe.getFull(), context);
                    } catch (SuggestInputParseException rethrow) {
                        throw rethrow;
                    } catch (Throwable e) {
                        throw new NoMatchException(Caption.of("fawe.error.parse.unknown-transform", pe.getFull(),
                                TextComponent
                                        .of("https://github.com/IntellectualSites/FastAsyncWorldEdit-Documentation/wiki/Transforms"
                                        )
                                        .clickEvent(ClickEvent.openUrl(
                                                "https://github.com/IntellectualSites/FastAsyncWorldEdit-Documentation/wiki/Transforms"
                                        ))
                        ));
                    }
                }
                if (pe.isAnd()) { // &
                    intersectionChances.add(chance);
                    intersection.add(transform);
                } else {
                    if (!intersection.isEmpty()) {
                        if (intersection.size() == 1) {
                            throw new InputParseException(Caption.of("fawe.error.parse.invalid-dangling-character", "&"));
                        }
                        MultiTransform multi = new MultiTransform();
                        double total = 0;
                        for (int i = 0; i < intersection.size(); i++) {
                            Double value = intersectionChances.get(i);
                            total += value;
                            multi.add(intersection.get(i), value);
                        }
                        union.add(multi);
                        unionChances.add(total);
                        intersection.clear();
                        intersectionChances.clear();
                    }
                    unionChances.add(chance);
                    union.add(transform);
                }
            }
        } catch (Throwable e) {
            throw new InputParseException(TextComponent.of(e.getMessage()), e);
        }
        if (!intersection.isEmpty()) {
            if (intersection.size() == 1) {
                throw new InputParseException(Caption.of("fawe.error.parse.invalid-dangling-character", "&"));
            }
            MultiTransform multi = new MultiTransform();
            double total = 0;
            for (int i = 0; i < intersection.size(); i++) {
                Double value = intersectionChances.get(i);
                total += value;
                multi.add(intersection.get(i), value);
            }
            union.add(multi);
            unionChances.add(total);
            intersection.clear();
            intersectionChances.clear();
        }
        if (union.isEmpty()) {
            throw new NoMatchException(Caption.of("fawe.error.parse.unknown-transform", input,
                    TextComponent.of("https://github.com/IntellectualSites/FastAsyncWorldEdit-Documentation/wiki/Transforms"
                    ).clickEvent(ClickEvent.openUrl(
                            "https://github.com/IntellectualSites/FastAsyncWorldEdit-Documentation/wiki/Transforms"
                    ))
            ));
        } else if (union.size() == 1) {
            return union.get(0);
        } else {
            RandomTransform random = new RandomTransform(new TrueRandom());
            for (int i = 0; i < union.size(); i++) {
                random.add(union.get(i), unionChances.get(i));
            }
            return random;
        }
    }

    @Override
    public List<String> getMatchedAliases() {
        return Collections.emptyList();
    }

}
