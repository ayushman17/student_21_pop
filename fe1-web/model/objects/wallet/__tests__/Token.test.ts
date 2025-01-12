import { WalletStore } from 'store';
import * as Seed from '../Seed';
import * as Token from '../Token';
import { Hash } from '../../Hash';
import { Base64UrlData } from '../../Base64Url';
import * as Wallet from '../index';

jest.mock('platform/Storage');
jest.mock('platform/crypto/browser');

const mnemonic: string = 'garbage effort river orphan negative kind outside quit hat camera approve first';

beforeEach(() => {
  WalletStore.clear();
});

test('LAO/RollCall produce known token - test vector 0', async () => {
  const expected = Base64UrlData.fromBuffer(Buffer.from(
    '7147759d146897111bcf74f60a1948b1d3a22c9199a6b88c236eb7326adc2efc', 'hex',
  ));

  await Seed.importMnemonic(mnemonic);

  const laoId: Hash = new Hash('T8grJq7LR9KGjE7741gXMqPny8xsLvsyBiwIFwoF7rg=');
  const rollCallId: Hash = new Hash('T8grJq7LR9KGjE7741gXMqPny8xsLvsyBiwIFwoF7rg=');
  const token = await Token.generateToken(laoId, rollCallId);

  expect(token.publicKey.valueOf()).toEqual(expected.valueOf());
});

test('Path produces known token - test vector 0', async () => {
  const expected = Base64UrlData.fromBuffer(Buffer.from(
    '7147759d146897111bcf74f60a1948b1d3a22c9199a6b88c236eb7326adc2efc', 'hex',
  ));

  await Seed.importMnemonic(mnemonic);

  const path = [
    'm',
    "888'",
    "0'",
    "7920043'/38174203'/71210134'/14078251'/2278823'/50163231'/203204108'/4625150'/6448'/23105'/238184'",
    "7920043'/38174203'/71210134'/14078251'/2278823'/50163231'/203204108'/4625150'/6448'/23105'/238184'",
  ].join('/');

  await Wallet.importMnemonic(mnemonic);

  const token = await Token.generateTokenFromPath(path);
  expect(token.publicKey.valueOf()).toEqual(expected.valueOf());
});
