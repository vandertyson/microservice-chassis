/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.depricated;

import com.viettel.vocs.common.config.loader.ConfigLoader;
import io.netty.handler.codec.http.HttpVersion;

/**
 * @author vttek
 */
public class HttpVersionConfigures extends ConfigLoader<HttpVersionConfigures> {

	@Override
	public boolean diff(HttpVersionConfigures obj) {
//		if (super.diff(obj)) return true;
//		if (!(obj instanceof HttpVersionConfigures)) return true;
//		HttpVersionConfigures o = (HttpVersionConfigures) obj;
		return true;
//			;
	}

	//    @Override
//    public Properties getStoredProperties() {
//        return storedProperties;
//    }
	public HttpVersionConfigures() {
		super();
	}

	public HttpVersion getVersion() {
		return null;
	}
}
