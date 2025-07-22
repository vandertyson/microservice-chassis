package com.viettel.vocs.mano.service;

import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import com.viettel.vocs.microchassis.topology.model.business.node.BusinessNode;
import com.viettel.vocs.microchassis.topology.model.business.node.GrandBusiness;
import com.viettel.vocs.microchassis.topology.model.business.node.SubBusiness;

/**
 * @author tiennn18
 */
public abstract class Mano1Handler extends HttpServerHandler implements ManoHandler {

	protected Mano1Handler(HttpServerOptions config) {
		super(config);
//		config.setAutoDecompose(false);
		registerManoAPIs(this);
		registerMtopoAPIs(this);
	}
	public Runnable dispatchCallback(HttpServerContext serverCtx){
		return ()-> super.dispatch(serverCtx != null ? (HttpServerContext) serverCtx : null);
	}

	@Override
	public void dispatch(HttpServerContext serverCtx) { // called when API not found, call by method handle overrided
		manoFinalDispatch(serverCtx);
	}

	@Override public BusinessNode getBusiness() {
		return new SubBusiness("chassis", new GrandBusiness(GrandBusiness.PredefinedGrandBusiness.OCS));
	}
}

