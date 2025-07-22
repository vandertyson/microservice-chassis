/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.connection.config;

import com.viettel.vocs.common.config.loader.ConfigLoader;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.KeyManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Objects;

/**
 * @author vttek
 */
public class SslConfiguration extends ConfigLoader<SslConfiguration> {
	private static final Logger logger = LogManager.getLogger(SslConfiguration.class);
	private static final SslProvider provider = OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK; // SslProvider.OPENSSL; //

	public boolean requireClientAuth;
	public String passPhrase; // cho private key hoac keystore jks
	public String algorithm;
	// su dung 1 trong 2, jks hoac file(cert, key, trust)
	//// file
	public String certPath; // public cert cua local // allow both PEM and CRT
	public String keyPath; // private key path cua local // allow both PEM and KEY
	public String trustPath; // public cert cua cac remote duoc local trust // allow both PEM and CRT
	//// jks
	public String jksPath;
	//////
	private char[] password;

	@Override
	public boolean diff(SslConfiguration obj) {
		return obj == null
			|| obj.requireClientAuth != requireClientAuth
			|| Arrays.equals(obj.password, password)
			|| !Objects.equals(obj.passPhrase, passPhrase)
			|| !Objects.equals(obj.algorithm, algorithm)
			|| !Objects.equals(obj.certPath, certPath)
			|| !Objects.equals(obj.keyPath, keyPath)
			|| !Objects.equals(obj.trustPath, trustPath)
			|| !Objects.equals(obj.jksPath, jksPath)
			;
	}

	public SslConfiguration() {
	}

//    public SslConfiguration(Properties properties) {
//        if (properties.getProperty("sslConfiguration.certPath") != null) {
//            try {
//                this.certPath = properties.getProperty("sslConfiguration.certPath");
//            } catch (Exception ignored) {
//            }
//        }
//        if (properties.getProperty("sslConfiguration.keyPath") != null) {
//            try {
//                this.keyPath = properties.getProperty("sslConfiguration.keyPath");
//            } catch (Exception ignored) {
//            }
//        }
//        if (properties.getProperty("sslConfiguration.trustPath") != null) {
//            try {
//                this.trustPath = properties.getProperty("sslConfiguration.trustPath");
//            } catch (Exception ignored) {
//            }
//        }
//        if (properties.getProperty("sslConfiguration.passPhrase") != null) {
//            try {
//                this.passPhrase = properties.getProperty("sslConfiguration.passPhrase");
//            } catch (Exception ignored) {
//            }
//        }
//        if (properties.getProperty("sslConfiguration.requireClientAuth") != null) {
//            try {
//                this.requireClientAuth = Boolean.valueOf("sslConfiguration.requireClientAuth");
//            } catch (Exception e) {
//            }
//        }
//    }

	public void fillAll() {
		passPhrase = passPhrase != null && passPhrase.isEmpty() ? null : passPhrase; // do not accept empty, but allow null
		password = passPhrase != null ? passPhrase.toCharArray() : null; // allow null
		algorithm = algorithm != null && !algorithm.isEmpty() ? algorithm : KeyManagerFactory.getDefaultAlgorithm();
		File jksF;
		if (jksPath != null && (!(jksF = new File(jksPath)).exists())) {
			logger.error("JKS path at [{}] not existed, set to null to ignore JKS source", jksF.getAbsolutePath());
			jksPath = null; // reset to null if not invalid // no need to throw IOException
		}
		logger.info("Build SSL by {} config with reqAuth={} algorithm={}, trustPath={} and passphrase={} for jksPath={} or certPath={} keyPath={}",
			provider.toString(), requireClientAuth, algorithm, trustPath, passPhrase, jksPath, certPath, keyPath);
	}

	private KeyManagerFactory keyManagerFactory(char[] password) throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException {
		try (FileInputStream fin = new FileInputStream(jksPath)) {
			KeyStore keyStore = KeyStore.getInstance("JKS");
			keyStore.load(fin, password);
			KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
			keyManagerFactory.init(keyStore, password);
			return keyManagerFactory;
		}
	}

	public SslContext build(boolean isServer) throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException  {
		fillAll();
		SslContextBuilder sslBuilder = (isServer
			? (jksPath != null
			? SslContextBuilder.forServer(keyManagerFactory(password))
			: SslContextBuilder.forServer(new File(certPath), new File(keyPath), passPhrase))
			: (jksPath != null
			? SslContextBuilder.forClient().keyManager(keyManagerFactory(password))
			: (certPath != null && keyPath != null && requireClientAuth
			? SslContextBuilder.forClient().keyManager(new File(certPath), new File(keyPath), passPhrase)
			: SslContextBuilder.forClient())));
		if (trustPath != null) {
			sslBuilder.trustManager(new File(trustPath));
		} else {
			sslBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE); // trust all
		}
		if (isServer && requireClientAuth) sslBuilder.clientAuth(ClientAuth.REQUIRE);
		return sslBuilder.sslProvider(provider)
			/* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
			 * Please refer to the HTTP/2 specification for cipher requirements. */
			.ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
			.applicationProtocolConfig(new ApplicationProtocolConfig(
				ApplicationProtocolConfig.Protocol.ALPN,
				// NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
				ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
				// ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
				ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
//				isHttp2Support ?
					ApplicationProtocolNames.HTTP_2, ApplicationProtocolNames.HTTP_1_1
//					: new String[]{ApplicationProtocolNames.HTTP_1_1}
	)
			).build();
	}

	public SslConfiguration setCertPath(String certPath) {
		this.certPath = certPath;
		return this;
	}

	public SslConfiguration setKeyPath(String keyPath) {
		this.keyPath = keyPath;
		return this;
	}

	public SslConfiguration setTrustPath(String trustPath) {
		this.trustPath = trustPath;
		return this;
	}

	public SslConfiguration setPassPhrase(String passPhrase) {
		this.passPhrase = passPhrase;
		return this;
	}

	public SslConfiguration setRequireClientAuth(boolean requireClientAuth) {
		this.requireClientAuth = requireClientAuth;
		return this;
	}

	public SslConfiguration setJksPath(String jksPath) {
		this.jksPath = jksPath;
		return this;
	}

	public SslConfiguration setAlgorithm(String algorithm) {
		this.algorithm = algorithm;
		return this;
	}
}
