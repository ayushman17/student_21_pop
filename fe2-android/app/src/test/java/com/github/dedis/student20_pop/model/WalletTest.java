package com.github.dedis.student20_pop.model;

import androidx.core.util.Pair;

import net.i2p.crypto.eddsa.Utils;
import org.junit.Test;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.StringJoiner;

import javax.crypto.ShortBufferException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class WalletTest {

  @Test
  public void importSeedAndExportSeedAreCoherent()
      throws NoSuchAlgorithmException, InvalidKeyException, ShortBufferException {

    String Lao_ID = "1234123412341234";
    String Roll_Call_ID = "1234123412341234";

    Wallet hdw1 = new Wallet();
    String[] exp_str = hdw1.exportSeed();
    StringJoiner joiner = new StringJoiner(" ");
    for(String i: exp_str) joiner.add(i);

    Pair<byte[], byte[]> res1 =  hdw1.findKeyPair(Lao_ID,Roll_Call_ID);

    Wallet hdw2 = new Wallet();
    hdw2.importSeed(joiner.toString(), new HashMap<>());
    Pair<byte[], byte[]> res2 =  hdw2.findKeyPair(Lao_ID,Roll_Call_ID);

    assertArrayEquals(res1.first, res2.first);
    assertArrayEquals(res1.second, res2.second);
  }

  @Test
  public void crossValidationWithFe1Web()
      throws NoSuchAlgorithmException, InvalidKeyException, ShortBufferException {
    String Lao_ID = "T8grJq7LR9KGjE7741gXMqPny8xsLvsyBiwIFwoF7rg=";
    String Roll_Call_ID = "T8grJq7LR9KGjE7741gXMqPny8xsLvsyBiwIFwoF7rg=";

    Wallet hdw = new Wallet();
    hdw.importSeed("garbage effort river orphan negative kind outside quit hat camera approve first", new HashMap<>());
    Pair<byte[], byte[]> res =  hdw.findKeyPair(Lao_ID,Roll_Call_ID);
    assertEquals(Utils.bytesToHex(res.first), "9e8ca414e088b2276d140bb69302269ccede242197e1f1751c45ec40b01678a0");
    assertEquals(Utils.bytesToHex(res.second), "7147759d146897111bcf74f60a1948b1d3a22c9199a6b88c236eb7326adc2efc");

  }
}
