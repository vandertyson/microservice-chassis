///*
// * Copyright 2014 The Netty Project
// *
// * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
// * "License"); you may not use this file except in compliance with the License. You may obtain a
// * copy of the License at:
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software distributed under the License
// * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
// * or implied. See the License for the specific language governing permissions and limitations under
// * the License.
// */
//package com.viettel.vocs.microchassis.http.codec.io;
//
//import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
//import io.netty.channel.ChannelHandlerContext;
//import io.netty.handler.codec.http.HttpObjectAggregator;
//import io.netty.handler.codec.http.HttpServerCodec;
//import io.netty.handler.ssl.ApplicationProtocolNames;
//import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
//
///**
// * Negotiates with the browser if HTTP2 or HTTP is going to be used. Once
// * decided, the Netty pipeline is setup with the correct handlers for the
// * selected protocol.
// * H1N2SReqHandler stand for Http1 Negotiation 2 Server Request handler
// */
//
//public class AlpnSIHandler extends ApplicationProtocolNegotiationHandler {
//    private final HttpServerOptions options;
//    private final H1SIHandler h1IHandler;
//    private final H2SIHandler h2OHandler;
//    public AlpnSIHandler(HttpServerOptions options,
//                         H1SIHandler h1IHandler,
//                         H2SIHandler h2OHandler) {
//        super(ApplicationProtocolNames.HTTP_1_1);
//        this.h2OHandler = h2OHandler;
//        this.options = options;
//        this.h1IHandler = h1IHandler;
//    }
//
//    @Override
//    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
//
//    }
//}
