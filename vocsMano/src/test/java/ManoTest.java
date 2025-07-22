import com.viettel.vocs.mano.service.ManoIntegration;
import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.os.TimeUtils;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileNotFoundException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ManoTest {

	protected static final Logger logger = LogManager.getLogger(ManoTest.class);

	@BeforeAll
	public void setUpClass(){
		LogUtils.setupLog4j2();
	}

	@Test
	void formatMetric() {
		String toBeformat = "INFORMATION_ALL{podType='abc', name='hax cbc'} 100.5 16858899589";
		Triple<String, Double, Long> ret = ManoIntegration.formatMetricLine(toBeformat);
		assertEquals("INFORMATION_ALL{podType='abc', name='hax cbc'}", ret.getLeft());
		assertEquals(100.5, ret.getMiddle());
		assertEquals(16858899589L, ret.getRight());
	}

	@Test
	void formatMetric2() {
		String toBeformat = "INFORMATION_ALL 100.5 16858899589";
		Triple<String, Double, Long> ret = ManoIntegration.formatMetricLine(toBeformat);
		assertEquals("INFORMATION_ALL", ret.getLeft());
		assertEquals(100.5, ret.getMiddle());
		assertEquals(16858899589L, ret.getRight());
	}

	@Test
	void formatMetric3() {
		String toBeformat = "INFORMATION_ALL 100.5 ";
		Triple<String, Double, Long> ret = ManoIntegration.formatMetricLine(toBeformat);
		assertEquals("INFORMATION_ALL", ret.getLeft());
		assertEquals(100.5, ret.getMiddle());
	}

	@Test
	void testPairNull() {
		Pair<String, Integer> x = Pair.of("x", null);
		Integer b = x.getValue();
		assertEquals(null, b);
	}

	@Test
	void testConcurrentDeque() {
		ConcurrentLinkedDeque<String> cld = new ConcurrentLinkedDeque();
		cld.offer("1");
		cld.offer("2");
		cld.offer("3");
		cld.addFirst("0");
		String remove = cld.remove();
		System.out.println("top value " + remove);
		String second = cld.remove();
		System.out.println("next value " + second);
		assertEquals("0", remove);
		assertEquals("1", second);
		assertEquals(2, cld.size());
	}

	@Test
	void testCompletable() {
		CompletableFuture<Boolean> comp = new CompletableFuture<>();
		Executors.newSingleThreadExecutor().execute(() -> {
			comp.complete(TimeUtils.waitSafeMili(500));
		});
		try {
			comp.get(1, TimeUnit.SECONDS);
		} catch (Exception ex) {
			System.out.println("Timeout");
		}
		System.out.println("Done " + comp.isDone());
	}

	@Test
	void testDES() throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
		String SECRET_KEY = "ocs@mano";
		String authen = "admin:admin";

		SecretKeySpec skeySpec = new SecretKeySpec(SECRET_KEY.getBytes(), "DES");
		Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5PADDING");
		cipher.init(Cipher.ENCRYPT_MODE, skeySpec);

		//encode
		byte[] byteEncrypted = cipher.doFinal(authen.getBytes());
		String encrypted = Base64.getEncoder().encodeToString(byteEncrypted);
		System.out.println("Base64 Encrypted authen " + encrypted);

		//decode
		cipher.init(Cipher.DECRYPT_MODE, skeySpec);
		byte[] decode = Base64.getDecoder().decode(encrypted);
		byte[] byteDecrypted = cipher.doFinal(decode);
		String decrypted = new String(byteDecrypted);
		System.out.println("Decoded authen " + decrypted);
		assertEquals(authen, decrypted);
	}

	@Test
	void testQSD() {
		String path = "/abc/xyz?a=1&b=2,7&c&d";
		QueryStringDecoder qsd = new QueryStringDecoder(path);
		System.out.println(qsd.path());
		System.out.println(qsd.rawPath());
		System.out.println(qsd.parameters());
	}
}
