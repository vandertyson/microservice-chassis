package com.viettel.vocs.microchassis.util;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * @author tiennn18
 */
public class LogDistinctCollection<T> implements Iterable<T> {
	protected LinkedHashSet<T> set;
	protected LinkedList<T> list;

	public LogDistinctCollection() {
		set = new LinkedHashSet<>();
		list = new LinkedList<>();
	}

	public void add(T item) {
		if (set.add(item)) {
			list.add(item);
		}
	}


	public int size() {
		return set.size();
	}
	public T get(int idx){
		return list.get(idx);
	}
	public List<T> getList() { // to get ordered list, without editable internal list
		return new LinkedList<>(list);
	}
	public int getOrder(T item){
		return list.indexOf(item);
	}
	@Override
	public Iterator<T> iterator() {
		return list.iterator();
	}
}
