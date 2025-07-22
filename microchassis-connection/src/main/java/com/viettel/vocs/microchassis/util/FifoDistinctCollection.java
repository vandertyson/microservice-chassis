package com.viettel.vocs.microchassis.util;

/**
 * @author tiennn18
 */

public class FifoDistinctCollection<T> extends LogDistinctCollection<T>{
	public FifoDistinctCollection(){
		super();
	}
	public void remove(T item) {
		if (set.remove(item)) {
			list.remove(item);
		}
	}

	public void clear() {
		set.clear();
		list.clear();
	}
}
