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
package wybs.lang;

public interface SyntacticItem extends SyntacticElement, Comparable<SyntacticItem> {

	/**
	 * Get the enclosing compilation unit in which this syntactic item is
	 * contained. This maybe null if the item is not yet allocate to a heap.
	 *
	 * @return
	 */
	public SyntacticHeap getHeap();

	/**
	 * Allocated the given item to a syntactic heap. Note that an item can only
	 * be allocated to one heap at a time. Therefore, an exception will be
	 * raised if this item is already allocated to another heap.
	 *
	 * @param heap
	 *            The heap into which this item is being allocated
	 * @param index
	 *            The index at which this item is being allocated
	 */
	public void allocate(SyntacticHeap heap, int index);

	/**
	 * The opcode which defines what this bytecode does. Observe that certain
	 * bytecodes must correspond with specific subclasses. For example,
	 * <code>opcode == LOAD</code> always indicates that <code>this</code> is an
	 * instanceof <code>Load</code>.
	 */
	public int getOpcode();

	/**
	 * Mutate the opcode of this item
	 *
	 * @param opcode
	 */
	public void setOpcode(int opcode);

	/**
	 * Get the number of operands in this bytecode
	 *
	 * @return
	 */
	public int size();

	/**
	 * Return the ith top-level operand in this bytecode.
	 *
	 * @param i
	 * @return
	 */
	public SyntacticItem get(int i);

	/**
	 * Return the top-level operands in this bytecode.
	 *
	 * @return
	 */
	public SyntacticItem[] getAll();

	/**
	 * Mutate the ith child of this item
	 *
	 * @param ith
	 * @param child
	 */
	public void setOperand(int ith, SyntacticItem child);

	/**
	 * Get the index of this item in the parent's items table.
	 *
	 * @return
	 */
	public int getIndex();

	/**
	 * Get any data associated with this item. This will be null if no data is
	 * associated.
	 *
	 * @return
	 */
	public byte[] getData();


	/**
	 * Get the first syntactic item of a given kind which refers to this item.
	 *
	 * @param kind
	 * @return
	 */
	public <T extends SyntacticItem> T getParent(Class<T> kind);

	/**
	 * Get the first syntactic item of a given kind which refers directly or
	 * indirectly to this item.
	 *
	 * @param kind
	 * @return
	 */
	public <T extends SyntacticItem> T getAncestor(Class<T> kind);

	/**
	 * Create a new copy of the given syntactic item with the given operands.
	 * The number of operands must match <code>size()</code> for this item, and
	 * be of appropriate type.
	 *
	 * @param operands
	 * @return
	 */
	public SyntacticItem clone(SyntacticItem[] operands);

	// ============================================================
	// Schema
	// ============================================================

	public enum Operands {
		ZERO, ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, MANY
	}

	public enum Data {
		ZERO,
		ONE,
		TWO,
		MANY
	}

	public static abstract class Schema {
		private final Operands operands;
		private final Data data;
		private final String mnemonic;

		public Schema(Operands operands, Data data) {
			this(operands,data,"unknown");
		}

		public Schema(Operands operands, Data data, String mnemonic) {
			this.operands = operands;
			this.data = data;
			this.mnemonic = mnemonic;
		}

		public Operands getOperandLayout() {
			return operands;
		}

		public Data getDataLayout() {
			return data;
		}

		public String getMnemonic() {
			return mnemonic;
		}

		public abstract SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data);

		@Override
		public String toString() {
			return "<" + operands + " operands, " + data + ">";
		}
	}
}
