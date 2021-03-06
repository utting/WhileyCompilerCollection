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
package wybs.io;

import java.io.*;
import java.math.BigInteger;
import java.util.Arrays;

import wybs.lang.SyntacticHeap;
import wybs.lang.SyntacticItem;
import wycc.util.Pair;
import wyfs.io.BinaryInputStream;

/**
 * <p>
 * Responsible for writing a WyilFile to an output stream in binary form. The
 * binary format is structured to given maximum flexibility and to avoid
 * built-in limitations in terms of e.g. maximum sizes, etc.
 * </p>
 *
 * @author David J. Pearce
 *
 */
public abstract class SyntacticHeapReader {
	protected final BinaryInputStream in;
	protected final SyntacticItem.Schema[] schema;

	public SyntacticHeapReader(InputStream output, SyntacticItem.Schema[] schema) {
		this.in = new BinaryInputStream(output);
		this.schema = schema;
	}

	public void close() throws IOException {
		in.close();
	}

	public abstract SyntacticHeap read() throws IOException;

	/**
	 * Read all the items in this heap, returning the identified root item and an
	 * array of all items contained therein.
	 *
	 * @return
	 * @throws IOException
	 */
	protected Pair<Integer, SyntacticItem[]> readItems() throws IOException {
		// first, write magic number
		checkHeader();
		// second, determine number of items
		int size = in.read_uv();
		// third, determine the root item
		int root = in.read_uv();
		//
		Bytecode[] items = new Bytecode[size];
		// third, read abstract syntactic items
		for (int i = 0; i != items.length; ++i) {
			items[i] = readItem();
		}
		//
		return new Pair<>(root, constructItems(items));
	}

	protected abstract void checkHeader() throws IOException;

	protected Bytecode readItem() throws IOException {
		// read opcode
		int opcode = in.read_u8();
		// Write operands
		int[] operands = readOperands(opcode);
		// Write data (if any)
		byte[] data = readData(opcode);
		// Pad to next byte boundary
		in.pad_u8();
		//
		return new Bytecode(opcode,operands,data);
	}

	protected int[] readOperands(int opcode) throws IOException {
		// Determine operand layout
		SyntacticItem.Operands layout = schema[opcode].getOperandLayout();
		int[] operands;
		int size;
		// Determine number of operands according to layout
		switch(layout) {
		case MANY:
			size = in.read_uv();
			break;
		default:
			size = layout.ordinal();
		}
		//
		operands = new int[size];
		// Read operands
		for (int i = 0; i != operands.length; ++i) {
			operands[i] = in.read_uv();
		}
		//
		return operands;
	}

	protected byte[] readData(int opcode) throws IOException {
		// Determine operand layout
		SyntacticItem.Data layout = schema[opcode].getDataLayout();
		byte[] bytes;
		int size;
		// Determine number of bytes according to layout
		switch(layout) {
		case MANY:
			size = in.read_uv();
			break;
		default:
			size = layout.ordinal();
		}
		//
		bytes = new byte[size];
		// Read operands
		for (int i = 0; i != bytes.length; ++i) {
			bytes[i] = (byte) in.read_u8();
		}
		//
		return bytes;
	}

	protected SyntacticItem[] constructItems(Bytecode[] bytecodes) {
		SyntacticItem[] items = new SyntacticItem[bytecodes.length];
		//
		for(int i=0;i!=items.length;++i) {
			constructItem(i,bytecodes,items);
		}
		//
		return items;
	}

	protected void constructItem(int index, Bytecode[] bytecodes, SyntacticItem[] items) {
		// FIXME: this fails in the presence of truly recursive items.
		if (items[index] == null) {
			// This item not yet constructed, therefore construct it!
			Bytecode bytecode = bytecodes[index];
			// Destructure bytecode
			int opcode = bytecode.opcode;
			int[] operands = bytecode.operands;
			byte[] data = bytecode.data;
			// Construct empty item
			SyntacticItem item = schema[bytecode.opcode].construct(opcode, new SyntacticItem[operands.length], data);
			// Store item so can be accessed recursively
			items[index] = item;
			// Recursively construct operands
			for (int i = 0; i != operands.length; ++i) {
				constructItem(operands[i], bytecodes, items);
				item.setOperand(i, items[operands[i]]);
			}
		}
	}

	private static class Bytecode {
		public final int opcode;
		public final int[] operands;
		public final byte[] data;

		public Bytecode(int opcode, int[] operands, byte[] data) {
			this.opcode = opcode;
			this.operands = operands;
			this.data = data;
		}
	}
}
