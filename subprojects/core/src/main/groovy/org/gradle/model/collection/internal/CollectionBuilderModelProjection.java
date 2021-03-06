/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.model.collection.internal;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import org.gradle.api.Nullable;
import org.gradle.internal.util.BiFunction;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.gradle.internal.Cast.uncheckedCast;

public abstract class CollectionBuilderModelProjection<I> implements ModelProjection {

    protected final Class<I> baseItemType;
    protected final ModelType<I> baseItemModelType;

    public CollectionBuilderModelProjection(ModelType<I> baseItemModelType) {
        this.baseItemModelType = baseItemModelType;
        this.baseItemType = baseItemModelType.getConcreteClass();
    }

    protected abstract Collection<? extends Class<?>> getCreatableTypes(MutableModelNode node);

    protected abstract BiFunction<? extends ModelCreators.Builder, MutableModelNode, ModelReference<? extends I>> getCreatorFunction();

    private String getBuilderTypeDescriptionForCreatableTypes(Collection<? extends Class<?>> creatableTypes) {
        StringBuilder sb = new StringBuilder(CollectionBuilder.class.getName());
        if (creatableTypes.size() == 1) {
            @SuppressWarnings("ConstantConditions")
            String onlyType = Iterables.getFirst(creatableTypes, null).getName();
            sb.append("<").append(onlyType).append(">");
        } else {
            sb.append("<T>; where T is one of [");
            Joiner.on(", ").appendTo(sb, CollectionUtils.sort(Iterables.transform(creatableTypes, new Function<Class<?>, String>() {
                public String apply(Class<?> input) {
                    return input.getName();
                }
            })));
            sb.append("]");
        }
        return sb.toString();
    }

    public Iterable<String> getReadableTypeDescriptions(MutableModelNode node) {
        return getWritableTypeDescriptions(node);
    }

    protected Class<? extends I> itemType(ModelType<?> targetType) {
        Class<?> targetClass = targetType.getRawClass();
        if (targetClass.equals(CollectionBuilder.class)) {
            Class<?> targetItemClass = targetType.getTypeVariables().get(0).getRawClass();
            if (targetItemClass.isAssignableFrom(baseItemType)) {
                return baseItemType;
            }
            if (baseItemType.isAssignableFrom(targetItemClass)) {
                return targetItemClass.asSubclass(baseItemType);
            }
            return null;
        }
        if (targetClass.isAssignableFrom(CollectionBuilder.class)) {
            return baseItemType;
        }
        return null;
    }

    public <T> boolean canBeViewedAsWritable(ModelType<T> targetType) {
        return itemType(targetType) != null;
    }

    public <T> boolean canBeViewedAsReadOnly(ModelType<T> type) {
        return canBeViewedAsWritable(type);
    }

    public <T> ModelView<? extends T> asReadOnly(ModelType<T> type, MutableModelNode modelNode, @Nullable ModelRuleDescriptor ruleDescriptor) {
        return asWritable(type, modelNode, ruleDescriptor, null);
    }

    public <T> ModelView<? extends T> asWritable(ModelType<T> targetType, MutableModelNode node, ModelRuleDescriptor ruleDescriptor, List<ModelView<?>> inputs) {
        Class<? extends I> itemType = itemType(targetType);
        if (itemType != null) {
            return toView(ruleDescriptor, node, itemType);
        }
        return null;
    }

    protected <T, S extends I> ModelView<? extends T> toView(ModelRuleDescriptor sourceDescriptor, MutableModelNode node, Class<S> itemClass) {
        ModelType<S> itemType = ModelType.of(itemClass);
        CollectionBuilder<I> builder = new DefaultCollectionBuilder<I>(baseItemModelType, sourceDescriptor, node, getCreatorFunction());

        CollectionBuilder<S> subBuilder = builder.withType(itemClass);
        CollectionBuilderModelView<S> view = new CollectionBuilderModelView<S>(node.getPath(), DefaultCollectionBuilder.typeOf(itemType), subBuilder, sourceDescriptor);
        return uncheckedCast(view);
    }

    @Override
    public Iterable<String> getWritableTypeDescriptions(MutableModelNode node) {
        return Collections.singleton(getBuilderTypeDescriptionForCreatableTypes(getCreatableTypes(node)));
    }
}
