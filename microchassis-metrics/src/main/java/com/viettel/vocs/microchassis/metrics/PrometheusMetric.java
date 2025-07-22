/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.metrics;

import com.viettel.vocs.common.datatype.BooleanToggle;
import lombok.NonNull;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

import static com.viettel.vocs.microchassis.base.ChassisConfig.MetricConfig.SERIES_TYPE_KEY;

/**
 * @author vttek
 */
public abstract class PrometheusMetric {
	private static final Logger logger = LogManager.getLogger(PrometheusMetric.class);
	public static BooleanToggle isSkip = new BooleanToggle(false);

	public void collect(double value) {
		if (isSkip.get()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Skip collect metric. metric={}, value={}", this.name, value);
			}
			return;
		}
		doCollect(value);
	}

	public abstract void doCollect(double value);

	public abstract double lastValue();

	public abstract void reset();

	protected final String name;
	protected final Map<String, String> mapLabels;
	protected int hashCode = 0;

	public Map<String, String> getMapLabels() {
		return mapLabels;
	}

	protected PrometheusMetric(@NonNull String name, Pair<String, String>... labels) {
		this.name = name;
		mapLabels = new HashMap<>();
		for (Pair<String, String> label : labels) {
			if (label.getLeft() == null || label.getRight() == null) {
				logger.error("[Label is invalid. Ignore in constructor]{key={}, value={}}",
					label.getLeft(), label.getRight());
				continue;
			}
			mapLabels.put(label.getKey(), label.getValue());
		}
	}

	protected PrometheusMetric(@NonNull String name, Map<String, String> mapLabels) {
		this.name = name;
		this.mapLabels = mapLabels;
	}

	public String getName() {
		return name;
	}

	static int computeHash(String name, String seriesType, Pair<String, String>... labels) {
		return computeHash(name, seriesType, Arrays.stream(labels).collect(Collectors.toList()));
	}
	public static int computeHash(String name, String seriesType, List<Pair<String, String>> labels) {
		int hash = 7;
		hash = 97 * hash + Objects.hashCode(name);
		int mapHash = 0;
		if (seriesType != null && !seriesType.isEmpty()) {
			mapHash = SERIES_TYPE_KEY.get().hashCode() * 31 + seriesType.hashCode() * 37;
		}
		for (Pair<String, String> label : labels) {
			if (label.getLeft() == null || label.getRight() == null) {
				logger.error("[Label is invalid. Ignore when compute hash]{key={}, value={}}",
					label.getLeft(), label.getRight());
				continue;
			}
			if (SERIES_TYPE_KEY.get().equals(label.getLeft())) {
				continue;
			}
			mapHash += label.getLeft().hashCode() * 31 + label.getValue().hashCode() * 37;
		}
		hash = 97 * hash + mapHash;
		return hash;
	}

	@Override
	public int hashCode() {
		if (hashCode == 0) {
			int hash = 7;
			hash = 97 * hash + Objects.hashCode(name);
			int mapHash = 0;
			mapHash = mapLabels.entrySet().stream()
				.filter(f -> f.getKey() != null && f.getValue() != null)
				.map(entry -> entry.getKey().hashCode() * 31 + entry.getValue().hashCode() * 37)
				.reduce(mapHash, Integer::sum);
			hash = 97 * hash + mapHash;
			hashCode = hash;
		}
		return hashCode;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final PrometheusMetric other = (PrometheusMetric) obj;
		if (!Objects.equals(name, other.name)) {
			return false;
		}
		if (!Objects.equals(mapLabels, other.mapLabels)) {
			return false;
		}
		return true;
	}

	public String getLabelPrometheusString(List<Pair<String, String>> additionalLabels) {
		Map<String, String> finalLabels = new HashMap<>(mapLabels);
		if (additionalLabels != null && !additionalLabels.isEmpty()) {
			for (Pair<String, String> other : additionalLabels) {
				finalLabels.put(other.getKey(), other.getValue());
			}
		}
		if (finalLabels.isEmpty()) {
			return null;
		}
		return finalLabels.entrySet().stream().map(x -> x.getKey() + "=\"" + x.getValue() + "\"").collect(Collectors.joining(", "));
	}

	public void addLabels(List<Pair<String, String>> labels) {
		for (Pair<String, String> label : labels) {
			if (label.getLeft() == null || label.getRight() == null) {
				logger.error("[Label is invalid. Not add]{name={}, key={}, value={}}",
					this.name, label.getLeft(), label.getRight());
				continue;
			}
			mapLabels.put(label.getLeft(), label.getRight());
		}
	}

	public void addLabels(Pair<String, String>... labels) {
		addLabels(Arrays.stream(labels).collect(Collectors.toList()));
	}

	@Override
	public String toString() {
		return name + "{" + getLabelPrometheusString(null) + "}";
	}
}
