/*
 * Copyright 2021 Budapest University of Technology and Economics
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package hu.bme.mit.theta.core.decl;

import hu.bme.mit.theta.common.Utils;
import hu.bme.mit.theta.core.type.Type;

import hu.bme.mit.theta.common.container.Containers;

import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represents a variable declaration. Variables cannot be directly passed to the SMT solver,
 * they must be replaced with constants for a given index ({@link IndexedConstDecl}).
 * See also {@link hu.bme.mit.theta.core.utils.PathUtils}.
 *
 * @param <DeclType>
 */
public final class VarDecl<DeclType extends Type> extends Decl<DeclType> {
	private static final String DECL_LABEL = "var";

	private final Map<Integer, IndexedConstDecl<DeclType>> indexToConst;
	private final Map<List<Integer>, MultiIndexedConstDecl<DeclType>> indicesToConst;

	VarDecl(final String name, final DeclType type) {
		super(name, type);
		indexToConst = Containers.createMap();
		indicesToConst = Containers.createMap();
	}

	public static <DeclType extends Type> VarDecl<DeclType> copyOf(VarDecl<DeclType> from) {
		return new VarDecl<>(from.getName(), from.getType());
	}

	public IndexedConstDecl<DeclType> getConstDecl(final int index) {
		checkArgument(index >= 0);
		IndexedConstDecl<DeclType> constDecl = indexToConst.get(index);
		if (constDecl == null) {
			constDecl = new IndexedConstDecl<>(this, index);
			indexToConst.put(index, constDecl);
		}
		return constDecl;
	}

	public MultiIndexedConstDecl<DeclType> getConstDecl(final List<Integer> index) {
		checkArgument(index.size() > 1, "Multi-indexed constants must have at least two indices");
		checkArgument(index.stream().allMatch((i) -> i >= 0));
		MultiIndexedConstDecl<DeclType> constDecl = indicesToConst.get(index);
		if (constDecl == null) {
			constDecl = new MultiIndexedConstDecl<>(this, index);
			indicesToConst.put(index, constDecl);
		}
		return constDecl;
	}

	@Override
	public String toString() {
		return Utils.lispStringBuilder(DECL_LABEL).add(getName()).add(getType()).toString();
	}

}
