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
package wyfs.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import wyfs.lang.Content;
import wyfs.lang.Content.Registry;
import wyfs.lang.Path;
import wyfs.lang.Path.ID;
import wyfs.lang.Path.RelativeRoot;
import wyfs.lang.Path.Root;
import wyfs.util.DirectoryRoot.Folder;

/**
 * <p>
 * Provides an implementation of <code>Path.Root</code> which stores files in
 * memory, rather than on disk or within e.g. a Jar file. Initially, a virtual
 * root is completely empty since there is nothing backing it. Virtual roots are
 * used to hold temporary files that are generated during compilation and which
 * one does not want stored on e.g. the file system.
 * </p>
 *
 * <p>
 * As an example, intermediate Wyil files are often stored in virtual root. The
 * build task will typically use a virtual root as the default (meaning wyil
 * files are not written to disk during compilation), but the user can then
 * override this in order to examine them (e.g. for debugging).
 * </p>
 *
 * @author David J. Pearce
 *
 */
public class VirtualRoot extends AbstractRoot<VirtualRoot.Folder> {

	/**
	 * Construct a virtual root out of nothing.
	 *
	 * @param contentTypes
	 *            --- registry of known content types and their "suffixes"
	 * @throws IOException
	 */
	public VirtualRoot(Content.Registry contentTypes) {
		super(contentTypes);
	}

	@Override
	protected Folder root() {
		return new Folder(Trie.ROOT);
	}


	@Override
	public RelativeRoot createRelativeRoot(ID id) throws IOException {
		return new Relative(contentTypes);
	}

	public final class Relative extends VirtualRoot implements Path.RelativeRoot {

		public Relative(Registry contentTypes) {
			super(contentTypes);
		}

		@Override
		public Root getParent() {
			return VirtualRoot.this;
		}

	}

	/**
	 * An entry is a file on the file system which represents a Whiley module. The
	 * file may be encoded in a range of different formats. For example, it may be a
	 * source file and/or a binary wyil file.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static final class Entry<T> extends AbstractEntry<T> implements Path.Entry<T> {

		/**
		 * The following is use to determine the appropriate "suffix" of an
		 * entry.
		 */
		private final Content.Registry contentTypes;

		/**
		 * The raw data representing the contents of this file. Initially, this
		 * is empty as one would expect.
		 */
		private byte[] data;

		/**
		 * Number of bytes in data actually used.
		 */
		private int length;

		/**
		 * The last modified date. This is a time stamp used to determine when
		 * the file was last modified in order to calculate which dependents
		 * need recompilation.
		 */
		private long lastModified;

		public Entry(Path.ID id, Content.Registry contentTypes) {
			super(id);
			this.data = new byte[0];
			this.contentTypes = contentTypes;
		}

		@Override
		public String location() {
			return "~:" + id.toString();
		}

		@Override
		public String suffix() {
			return contentTypes.suffix(contentType);
		}

		@Override
		public long lastModified() {
			return lastModified;
		}

		@Override
		public InputStream inputStream() {
			return new ByteArrayInputStream(data,0,length);
		}

		@Override
		public OutputStream outputStream() {
			lastModified = System.currentTimeMillis();
			data = new byte[0];
			length = 0;
			// create an output stream which will automatically resize the given
			// array.
			return new OutputStream() {
				@Override
				public void write(byte[] bytes) {
					data = new byte[bytes.length];
					System.arraycopy(bytes, 0, data, 0, bytes.length);
					length = data.length;
				}
				@Override
				public void write(byte[] bytes, int off, int len) {
					data = new byte[len];
					System.arraycopy(bytes, off, data, 0, len);
					length = data.length;
				}
				@Override
				public void write(int b) {
					if (length >= data.length) {
						data = Arrays.copyOf(data, (data.length + 1) * 2);
					}
					data[length++] = (byte) b;
				}
			};
		}

		@Override
		public String toString() {
			return location();
		}
	}

	/**
	 * Represents a directory in the virtual file system.
	 *
	 * @author David J. Pearce
	 *
	 */
	public final class Folder extends AbstractFolder {
		public Folder(Path.ID id) {
			super(id);
		}

		@Override
		protected Path.Item[] contents() throws IOException {
			// Initially, a virtual folder is always empty.
			return new Path.Item[0];
		}

		@Override
		public <T> Path.Entry<T> create(ID nid, Content.Type<T> ct) throws IOException {
			if (nid.size() == 1) {
				// attempting to create an entry in this folder
				Path.Entry<T> e = super.get(nid.subpath(0, 1), ct);
				if (e == null) {
					// Entry doesn't already exist, so create it
					nid = id.append(nid.get(0));
					e = new Entry(nid, contentTypes);
					e.associate(ct, null);
					super.insert(e);
				}
				return e;
			} else {
				// attempting to create entry in subfolder.
				Path.Folder folder = getFolder(nid.get(0));
				if (folder == null) {
					// Folder doesn't already exist, so create it.
					folder = new Folder(id.append(nid.get(0)));
					super.insert(folder);
				}
				return folder.create(nid.subpath(1, nid.size()), ct);
			}
		}

		@Override
		public String toString() {
			return "~:" + id;
		}
	}
}
