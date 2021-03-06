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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import wybs.lang.Attribute;
import wybs.lang.SyntacticHeap;
import wybs.lang.SyntacticItem;

public abstract class AbstractSyntacticHeap implements SyntacticHeap {
	/**
	 * The list of syntactic items contained in this heap.
	 */
	protected final ArrayList<SyntacticItem> syntacticItems = new ArrayList<>();

	/**
	 * The root item for this heap.
	 */
	protected int root;

	public AbstractSyntacticHeap() {

	}

	public AbstractSyntacticHeap(SyntacticHeap heap) {
		// Copy over the root
		this.root = heap.getRootItem().getIndex();
		// Now, clone items from heap in here
		Allocator allocator = new Allocator(this);
		//
		for (int i = 0; i != heap.size(); ++i) {
			SyntacticItem oitem = heap.getSyntacticItem(i);
			SyntacticItem item = clone(oitem, allocator.map);
			allocator.allocate(item);
		}
	}

	@Override
	public int size() {
		return syntacticItems.size();
	}

	@Override
	public SyntacticItem getRootItem() {
		return getSyntacticItem(root);
	}

	@Override
	public void setRootItem(SyntacticItem item) {
		this.root = allocate(item).getIndex();
	}

	@Override
	public SyntacticItem getSyntacticItem(int index) {
		return syntacticItems.get(index);
	}

	@Override
	public int getIndexOf(SyntacticItem item) {
		for (int i = 0; i != syntacticItems.size(); ++i) {
			if (syntacticItems.get(i) == item) {
				return i;
			}
		}
		throw new IllegalArgumentException("invalid syntactic item");
	}

	public <T extends SyntacticItem> List<T> getSyntacticItems(Class<T> kind) {
		ArrayList<T> matches = new ArrayList<>();
		for (int i = 0; i != syntacticItems.size(); ++i) {
			SyntacticItem item = syntacticItems.get(i);
			if (kind.isInstance(item)) {
				matches.add((T) item);
			}
		}
		return matches;
	}

	/**
	 * Get first parent of a syntactic item matching the given kind. If no item
	 * was found, then null is returned.
	 *
	 * @param child
	 * @param kind
	 * @return
	 */
	@Override
	public <T extends SyntacticItem> T getParent(SyntacticItem child, Class<T> kind) {
		// FIXME: this could be optimised a *lot*
		for (int i = 0; i != syntacticItems.size(); ++i) {
			SyntacticItem parent = syntacticItems.get(i);
			if(kind.isInstance(parent)) {
				for(int j=0;j!=parent.size();++j) {
					if(parent.get(j) == child) {
						return (T) parent;
					}
				}
			}
		}
		// no match
		return null;
	}


	/**
	 * Get first ancestor of a syntactic item matching the given kind. If no item
	 * was found, then null is returned.
	 *
	 * @param child
	 * @param kind
	 * @return
	 */
	@Override
	public <T extends SyntacticItem> T getAncestor(SyntacticItem child, Class<T> kind) {
		// FIXME: this could be optimised a *lot*
		if (kind.isInstance(child)) {
			return (T) child;
		} else {
			for (int i = 0; i != syntacticItems.size(); ++i) {
				SyntacticItem parent = syntacticItems.get(i);
				for (int j = 0; j != parent.size(); ++j) {
					// Don't follow cross-references
					if (parent.get(j) == child && !(parent instanceof AbstractCompilationUnit.Ref)) {
						// FIXME: this is not specifically efficient. It would
						// be helpful if SyntacticItems actually contained
						// references to their parent items.
						T tmp = getAncestor(parent, kind);
						if (tmp != null) {
							return tmp;
						}
					}
				}
			}
			// no match
			return null;
		}
	}

	@Override
	public <T extends SyntacticItem> T allocate(T item) {
		return (T) new Allocator(this).allocate(item);
	}

	public void print(PrintWriter out) {
		String lenStr = Integer.toString(syntacticItems.size());
		for (int i = 0; i != syntacticItems.size(); ++i) {
			SyntacticItem item = syntacticItems.get(i);
			out.print("// ");
			// Right align the string to ensure that all bytecodes are
			// displayed on the same column. This just helps reading them.
			String iStr = Integer.toString(i);
			for (int j = iStr.length(); j < lenStr.length(); ++j) {
				out.print(" ");
			}
			out.print("#" + i + " " + item);
			//
			List<Attribute> attributes = item.attributes();
			if (attributes.size() > 0) {
				out.print(" [");
				for (int j = 0; j != attributes.size(); ++j) {
					if (j != 0) {
						out.print(", ");
					}
					out.print(attributes.get(j));
				}
				out.print("]");
			}

			out.println();
		}
		out.flush();
	}

	/**
	 * <p>
	 * Recursively copy this syntactic item. Observe the resulting cloned
	 * syntactic item is *not* allocated to any heap, and this must be done
	 * separately. All children are recursively cloned as well.
	 * </p>
	 * <p>
	 * This method preserves the underlying aliasing structure of the object
	 * being cloned. However, aliasing information is not preserved across calls
	 * to this method. Furthermore, it is not currently capable of handling
	 * cyclic structures.
	 * </p>
	 *
	 * @return
	 */
	public static <T extends SyntacticItem> T clone(T item) {
		return clone(item, new IdentityHashMap<>());
	}

	// ========================================================================
	// HELPERS
	// ========================================================================

	/**
	 * Recursively copy this syntactic item. Observe the resulting cloned
	 * syntactic item is *not* allocated to any heap, and this must be done
	 * separately. All children are recursively copied as well.
	 *
	 * @param mapping
	 *            A mapping from the original syntactic items to the cloned
	 *            syntactic items. This is necessary to preserve the aliasing
	 *            structure in the resulting cloned item.
	 * @return
	 */
	private static <T extends SyntacticItem> T clone(T item, Map<SyntacticItem, SyntacticItem> mapping) {
		SyntacticItem clonedItem = mapping.get(item);
		if (clonedItem == null) {
			// Item not previously cloned. Therefore, first create new item
			SyntacticItem[] operands = new SyntacticItem[item.size()];
			for (int i = 0; i != operands.length; ++i) {
				SyntacticItem operand = item.get(i);
				if (operand != null) {
					operands[i] = clone(operand, mapping);
				}
			}
			// Now, create new item and store that for later.
			clonedItem = item.clone(operands);
			mapping.put(item, clonedItem);
		}
		return (T) clonedItem;
	}

	public static <T extends SyntacticItem> T cloneOnly(T item, Map<SyntacticItem, SyntacticItem> mapping, Class<?> clazz) {
		SyntacticItem clonedItem = mapping.get(item);
		if (clonedItem == null) {
			// Item not previously cloned. Therefore, first create new item
			SyntacticItem[] operands = item.getAll();
			SyntacticItem[] nOperands = operands;
			if (operands != null) {
				for (int i = 0; i != operands.length; ++i) {
					SyntacticItem operand = operands[i];
					if (operand != null) {
						SyntacticItem nOperand = cloneOnly(operand, mapping,clazz);
						if(nOperand != operand && operands == nOperands) {
							nOperands = Arrays.copyOf(operands, operands.length);
						}
						nOperands[i] = nOperand;
					}
				}
			}
			// Now, create new item and store that for later.
			if(nOperands != operands || clazz.isInstance(item)) {
				clonedItem = item.clone(nOperands);
				mapping.put(item, clonedItem);
			} else {
				clonedItem = item;
			}
		}
		return (T) clonedItem;
	}

	/**
	 * <p>
	 * Create a new syntactic item by replacing all occurrences of one item
	 * (<code>from</code>) with another (<code>to</code>). In the case that
	 * there is no change to the item (or any of its children) then the original
	 * item is returned untouched. Specifically, the exact same reference will
	 * be returned.
	 * </p>
	 * <p>
	 * Any new items created during this process are allocated into the heap of
	 * the item they are replacing. This is necessary since otherwise a mix of
	 * allocated and unallocated items would be returned, making it difficult to
	 * then allocate them all.
	 * </p>
	 * <p>
	 * This method preserves the underlying aliasing structure of the object
	 * being cloned. However, aliasing information is not preserved across calls
	 * to this method. Furthermore, it is not currently capable of handling
	 * cyclic structures.
	 * </p>
	 *
	 * @param item
	 *            The syntactic item we are currently substituting into
	 * @param from
	 *            The syntactic item we are looking to replace
	 * @param to
	 *            The syntactic item that will replace all occurrences of from
	 * @return
	 */
	public static SyntacticItem substitute(SyntacticItem item, SyntacticItem from, SyntacticItem to) {
		SyntacticItem nItem = substitute(item, from, to, new IdentityHashMap<>());
		if(nItem != item) {
			item.getHeap().allocate(nItem);
		}
		return nItem;
	}

	/**
	 * Helper method for above.
	 *
	 * @param mapping
	 *            A mapping from the original syntactic items to the cloned
	 *            syntactic items. This is necessary to preserve the aliasing
	 *            structure in the resulting cloned item.
	 */
	private static SyntacticItem substitute(SyntacticItem item, SyntacticItem from, SyntacticItem to,
			Map<SyntacticItem, SyntacticItem> mapping) {
		SyntacticItem sItem = mapping.get(item);
		if (sItem != null) {
			// We've previously substituted this item already to produce a
			// potentially updated item. Therefore, simply return that item to
			// ensure the original aliasing structure of the ancestor(s) is
			// properly preserved.
			return sItem;
		} else if (item == from) {
			// We've matched the item being replaced, therefore return the item
			// to which it is being replaced.
			return to;
		} else {
			SyntacticItem nItem = item;
			// We need to recursively descend into children of this item looking
			// for the item to replace. The challenge here is that we need to
			// ensure the original item is preserved as is if there is no
			// change.
			SyntacticItem[] children = item.getAll();
			// Initially, this will alias children. In the event of a child
			// which is actually updated, then this will refer to a new array.
			// That will be the signal that we need to create a new item to
			// return.
			SyntacticItem[] nChildren = children;
			if (children != null) {
				for (int i = 0; i != children.length; ++i) {
					SyntacticItem child = children[i];
					// Check for null, since we don't want to try and substitute
					// into null.
					if (child != null) {
						// Perform the substitution in the given child
						SyntacticItem nChild = substitute(child, from, to, mapping);
						// Check whether anything was actually changed by the
						// substitution.
						if (nChild != child && children == nChildren) {
							// Yes, the child changed and we haven't already
							// cloned the children array. Hence, we'd better
							// clone it now to make sure that the original item
							// is preserved.
							nChildren = Arrays.copyOf(children, children.length);
						}
						nChildren[i] = nChild;
					}
				}
				// Now, clone the original item if necessary. This is only
				// necessary if the children array as been updated in some way.
				if (children != nChildren) {
					// Create the new item which, at this point, will be
					// detached.
					nItem = item.clone(nChildren);
				}
			}
			mapping.put(item, nItem);
			return nItem;
		}
	}

	public static class Allocator implements SyntacticHeap.Allocator<AbstractSyntacticHeap> {
		protected final AbstractSyntacticHeap heap;
		protected final Map<SyntacticItem, SyntacticItem> map;

		public Allocator(AbstractSyntacticHeap heap) {
			this.heap = heap;
			this.map = new IdentityHashMap<>();
		}

		@Override
		public SyntacticItem allocate(SyntacticItem item) {
			SyntacticHeap parent = item.getHeap();
			SyntacticItem allocated = map.get(item);
			if (allocated != null) {
				return allocated;
			} else if (parent == heap) {
				// Item already allocated to this heap, hence nothing to do.
				return item;
			} else {
				// Determine index for allocation
				int index = heap.size();
				// Clone item prior to allocation
				SyntacticItem nItem = item.clone(new SyntacticItem[item.size()]);
				// Allocate item
				heap.syntacticItems.add(nItem);
				// ... and allocate item itself
				nItem.allocate(heap, index);
				map.put(item, nItem);
				// Item not allocated to this heap. Therefore, recursively allocate
				// all children.
				for (int i = 0; i != nItem.size(); ++i) {
					SyntacticItem child = item.get(i);
					if (child != null) {
						child = allocate(child);
					}
					nItem.setOperand(i, child);
				}
				return nItem;
			}
		}
	};
}
