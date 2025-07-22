//package com.viettel.vocs.microchassis.depricated;
//
//import com.viettel.vocs.microchassis.base.ChassisConst;
//import io.netty.handler.codec.http.HttpVersion;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//
//import java.util.Arrays;
//import java.util.HashSet;
//import java.util.Map;
//import java.util.Set;
//import java.util.function.Consumer;
//import java.util.stream.Collectors;
//
///**
// * @author tiennn18
// */
//public interface HttpMultiSupportOptions {
//	Logger logger = LogManager.getLogger(HttpMultiSupportOptions.class);
//
//	default boolean isSupport(HttpVersion... versions) {
//		return Arrays.stream(versions).anyMatch(version -> getProtoConfigs().containsKey(version.text()));
//	}
//
//	default HttpMultiSupportOptions disable(HttpVersion... versions) {
//		Arrays.stream(versions).forEach(version -> getProtoConfigs().remove(version.text()));
//		return this;
//	}
//
//	default Set<String> getSupportedProtocolNames() {
//		return getProtoConfigs().keySet().stream().map(k -> new HttpVersion(k, true).protocolName()).collect(Collectors.toSet());
//	}
//
//	default Set<String> getSupportedNames() {
//		return new HashSet<>(getProtoConfigs().keySet());
//	}
//
//	default boolean anySupport(HttpVersion... versions) {
//		return Arrays.stream(versions).anyMatch(version -> getProtoConfigs().containsKey(version.text()));
//	}
//
//	default HttpMultiSupportOptions setSupport(HttpVersion version, HttpVersionConfigures httpVersionConfigures) {
//		if (httpVersionConfigures != null // enable
//			&& httpVersionConfigures.getVersion().equals(version) // valid config
//		) {
////			storedProperties = httpVersionConfigures.getStoredProperties(); // bidirect update
//			getProtoConfigs().put(version.text(), httpVersionConfigures);
//		} else { //set null or invalid ~ remove
//			getProtoConfigs().remove(version.text());
//		}
//		return this;
//	}
//
//	default <S extends HttpVersionConfigures> S getSupport(HttpVersion version) {
//		return getSupport(version.text());
//	}
//
//	default <S extends HttpVersionConfigures> S getSupport(String version) {
//		try {
//			return (S) getProtoConfigs().get(version);
//		} catch (ClassCastException e) {
//			return null;
//		}
//	}
//
//	default <S extends HttpVersionConfigures> S onSupport(HttpVersion version, Consumer<S> applier) {
//		S config = getSupport(version);
//		if (config != null) {
//			applier.accept(config);
//			return config;
//		} else return null;
//	}
//
//	default HttpMultiSupportOptions reload(HttpVersion... versions) { // reload changes from altered storedProperties
//		for (HttpVersion version : versions) {
//			if (version.equals(ChassisConst.SupportVersion.HTTP_2_0)) {
//				return setSupport(version,
////                  storedProperties == null ?
//					new HttpVersionConfigures()
////                  : new Http2VersionConfigures(storedProperties)
//				);
//			} else if (version.equals(ChassisConst.SupportVersion.HTTP_1_1)) {
//				return setSupport(version,
////                  storedProperties == null ?
//					new HttpVersionConfigures()
////                  : new Http1VersionConfigures(storedProperties)
//				);
////			} else if (version.equals(SupportVersion.HTTP_1_0)) {
////				return setSupport(version,
//////                  storedProperties == null ?
////					new Http1_0VersionConfigures()
//////                  : new Http1VersionConfigures(storedProperties)
////				);
//			}
//		}
//		return this;
//	}
//
//	default HttpMultiSupportOptions enable(HttpVersion... versions) { // trigger, not rescan from storedProperties
//		for (HttpVersion version : versions) {
//			if (version.equals(ChassisConst.SupportVersion.HTTP_2_0)) {
//				getProtoConfigs().putIfAbsent(version.text(), //storedProperties == null?
//					new HttpVersionConfigures()
//					//: new Http2VersionConfigures(storedProperties)
//				);
//			} else if (version.equals(ChassisConst.SupportVersion.HTTP_1_1)) {
//				getProtoConfigs().putIfAbsent(version.text(), //storedProperties == null?
//					new HttpVersionConfigures()
//					//: new Http1VersionConfigures(storedProperties)
//				);
////			} else if (version.equals(SupportVersion.HTTP_1_0)) {
////				getProtoConfigs().putIfAbsent(version.text(), //storedProperties == null?
////					new Http1_0VersionConfigures()
////					//: new Http1VersionConfigures(storedProperties)
////				);
//			}
//		}
//		return this;
//	}
//
//	Map<String, HttpVersionConfigures> getProtoConfigs();
//
//	Set<HttpVersionConfigures> getSupportConfigs();
//}
