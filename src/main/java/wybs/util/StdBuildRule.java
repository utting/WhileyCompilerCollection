// Copyright 2011 The Whiley Project Developers
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package wybs.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import wybs.lang.Build;
import wycc.util.Pair;
import wyfs.lang.Content;
import wyfs.lang.Path;

/**
 * <p>
 * Provides a straightforward, yet flexible build rule implementation. This
 * build rule supports both include and exclude filters. It is expected that
 * this rule is sufficiently flexible for the majority of situations
 * encountered.
 * </p>
 * <p>
 * <b>NOTE</b>: instances of this class are immutable, although objects they
 * reference may not be (e.g. builders).
 * </p>
 *
 * @author David J. Pearce
 *
 */
public class StdBuildRule implements Build.Rule {
	/**
	 * The builder used to build files using this rule.
	 */
	final Build.Task builder;

	/**
	 * The source root containing all files which might be built using this
	 * rule. However, whether or not files contained in this root will actually
	 * be built depends on the includes and excludes filters.
	 */
	final Path.Root source;

	/**
	 * The destination root into which all files built using this rule are
	 * placed.
	 */
	final Path.Root target;

	/**
	 * A content filter used to determine which files contained in the source
	 * root should be built by this rule.  Maybe null.
	 */
	final Content.Filter<?> includes;

	/**
	 * A content filter used to determine which files contained in the source
	 * root should be not built by this rule.  Maybe null.
	 */
	final Content.Filter<?> excludes;

	/**
	 * Construct a standard build rule.
	 *
	 * @param builder
	 *            The build task used to build files using this rule.
	 * @param srcRoot
	 *            The source root containing all files which might be built
	 *            using this rule. However, whether or not files contained in
	 *            this root will actually be built depends on the includes and
	 *            excludes filters.
	 * @param includes
	 *            A content filter used to determine which files contained in
	 *            the source root should be built by this rule. Maybe null.
	 * @param excludes
	 *            A content filter used to determine which files contained in
	 *            the source root should be not built by this rule. Maybe null.
	 * @param targetRoot
	 *            The destination root into which all files built using this
	 *            rule are placed.
	 */
	public StdBuildRule(Build.Task builder, Path.Root srcRoot, Content.Filter<?> includes, Content.Filter<?> excludes,
			Path.Root targetRoot) {
		this.builder = builder;
		this.source = srcRoot;
		this.target = targetRoot;
		this.includes = includes;
		this.excludes = excludes;
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Set<Path.Entry<?>> apply(Collection<? extends Path.Entry<?>> group, Build.Graph graph) throws IOException {
		ArrayList<Pair<Path.Entry<?>, Path.Root>> matches = new ArrayList<>();

		// First, determine the set of matching files
		for (Path.Entry e : group) {
			if (includes == null || !includes.matches(e.id(), e.contentType())) {
				continue;
			}
			if (excludes != null && excludes.matches(e.id(), e.contentType())) {
				continue;
			}
			matches.add(new Pair<Path.Entry<?>, Path.Root>(e, target));
		}

		// Second, build all matching files
		if (matches.size() > 0) {
			return builder.build(matches, graph);
		} else {
			return Collections.EMPTY_SET;
		}
	}
}
